package com.kellvyn.assistant

import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

internal enum class KellvynCachePressure {
    UI_HIDDEN,
    MODERATE,
    HIGH,
    CRITICAL
}

/**
 * Engines implement this contract for transient native/managed buffers, previews, AST caches,
 * decoded tiles and other regenerable data. Reclaim must be non-blocking and must never delete
 * durable project files, model checkpoints or user-authored content.
 */
internal interface KellvynMemoryReclaimer {
    val reclaimerId: String
    val reclaimPriority: Int

    /** Returns an estimated number of bytes released immediately. */
    fun reclaim(level: KellvynCachePressure, targetBytes: Long): Long
}

/** Weak registrations prevent the application-wide registry from keeping an inactive engine alive. */
internal class KellvynResourceCacheRegistry {
    private data class Entry(
        val token: Long,
        val cache: WeakReference<KellvynMemoryReclaimer>
    )

    private val entries = mutableListOf<Entry>()
    private val nextToken = AtomicLong(1L)

    @Synchronized
    fun register(cache: KellvynMemoryReclaimer): AutoCloseable {
        require(cache.reclaimerId.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{1,79}")))
        val token = nextToken.getAndIncrement()
        check(token > 0L) { "O contador de registros de cache esgotou." }
        entries.removeAll { it.cache.get() == null }
        entries += Entry(token, WeakReference(cache))
        return AutoCloseable {
            synchronized(this) {
                entries.removeAll { it.token == token }
            }
        }
    }

    fun reclaim(level: KellvynCachePressure, targetBytes: Long = Long.MAX_VALUE): Long {
        require(targetBytes >= 0L)
        val active = synchronized(this) {
            entries.removeAll { it.cache.get() == null }
            entries.mapNotNull { it.cache.get() }
                .distinctBy { System.identityHashCode(it) }
                .sortedWith(compareByDescending<KellvynMemoryReclaimer> { it.reclaimPriority }
                    .thenBy { it.reclaimerId })
        }
        var released = 0L
        for (cache in active) {
            if (released >= targetBytes) break
            val remaining = if (targetBytes == Long.MAX_VALUE) Long.MAX_VALUE
                else (targetBytes - released).coerceAtLeast(0L)
            val freed = runCatching { cache.reclaim(level, remaining).coerceAtLeast(0L) }
                .getOrDefault(0L)
            released = if (Long.MAX_VALUE - released < freed) Long.MAX_VALUE else released + freed
        }
        return released
    }

    @Synchronized
    fun activeReclaimerCount(): Int {
        entries.removeAll { it.cache.get() == null }
        return entries.count { it.cache.get() != null }
    }
}

/**
 * Byte-bounded LRU for regenerable in-memory objects. It is useful for preview bitmaps, decoded
 * image tiles, parsed syntax trees and render previews. Durable data must never be stored here.
 */
internal class KellvynManagedMemoryCache<K : Any, V : Any>(
    override val reclaimerId: String,
    override val reclaimPriority: Int,
    private val maxBytes: Long,
    private val sizeOf: (K, V) -> Long,
    private val onEvicted: (K, V) -> Unit = { _, _ -> }
) : KellvynMemoryReclaimer {
    private data class Entry<V>(val value: V, val bytes: Long)

    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
    private var currentBytes = 0L

    init {
        require(reclaimerId.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{1,79}")))
        require(reclaimPriority in 0..100)
        require(maxBytes > 0L)
    }

    @Synchronized
    fun get(key: K): V? = entries[key]?.value

    @Synchronized
    fun put(key: K, value: V) {
        val bytes = sizeOf(key, value)
        require(bytes > 0L && bytes <= maxBytes) {
            "O item excede o orçamento do cache gerenciado."
        }
        removeInternal(key)
        entries[key] = Entry(value, bytes)
        currentBytes += bytes
        trimToBytes(maxBytes)
    }

    @Synchronized
    fun remove(key: K): Boolean = removeInternal(key) != null

    @Synchronized
    fun clear() {
        val removed = entries.entries.map { it.key to it.value.value }
        entries.clear()
        currentBytes = 0L
        removed.forEach { (key, value) -> runCatching { onEvicted(key, value) } }
    }

    @Synchronized
    fun sizeBytes(): Long = currentBytes

    @Synchronized
    fun entryCount(): Int = entries.size

    override fun reclaim(level: KellvynCachePressure, targetBytes: Long): Long {
        require(targetBytes >= 0L)
        val before = currentBytes
        val targetRemaining = when (level) {
            KellvynCachePressure.UI_HIDDEN -> before / 2L
            KellvynCachePressure.MODERATE -> before / 2L
            KellvynCachePressure.HIGH -> before / 4L
            KellvynCachePressure.CRITICAL -> 0L
        }
        val desiredRelease = minOf(before - targetRemaining, targetBytes)
        val targetSize = (before - desiredRelease).coerceAtLeast(0L)
        trimToBytes(targetSize)
        return (before - currentBytes).coerceAtLeast(0L)
    }

    private fun trimToBytes(target: Long) {
        val iterator = entries.entries.iterator()
        val evicted = mutableListOf<Pair<K, V>>()
        while (currentBytes > target && iterator.hasNext()) {
            val entry = iterator.next()
            iterator.remove()
            currentBytes = (currentBytes - entry.value.bytes).coerceAtLeast(0L)
            evicted += entry.key to entry.value.value
        }
        evicted.forEach { (key, value) -> runCatching { onEvicted(key, value) } }
    }

    private fun removeInternal(key: K): V? {
        val removed = entries.remove(key) ?: return null
        currentBytes = (currentBytes - removed.bytes).coerceAtLeast(0L)
        runCatching { onEvicted(key, removed.value) }
        return removed.value
    }
}
