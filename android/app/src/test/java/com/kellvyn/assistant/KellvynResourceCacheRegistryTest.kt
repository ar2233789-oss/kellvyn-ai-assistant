package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynResourceCacheRegistryTest {
    @Test
    fun managedCacheEvictsLeastRecentlyUsedEntriesWhenByteBudgetIsExceeded() {
        val evicted = mutableListOf<String>()
        val cache = KellvynManagedMemoryCache<String, String>(
            reclaimerId = "image-preview-cache",
            reclaimPriority = 50,
            maxBytes = 30L,
            sizeOf = { _, _ -> 10L },
            onEvicted = { key, _ -> evicted += key }
        )

        cache.put("a", "A")
        cache.put("b", "B")
        cache.put("c", "C")
        assertSame("A", cache.get("a")) // a becomes most recently used
        cache.put("d", "D")

        assertSame("A", cache.get("a"))
        assertNull(cache.get("b"))
        assertSame("C", cache.get("c"))
        assertSame("D", cache.get("d"))
        assertEquals(listOf("b"), evicted)
        assertEquals(30L, cache.sizeBytes())
    }

    @Test
    fun criticalTrimClearsTransientEntriesAndReportsReleasedBytes() {
        val cache = KellvynManagedMemoryCache<String, ByteArray>(
            reclaimerId = "decoded-tiles",
            reclaimPriority = 90,
            maxBytes = 1_000L,
            sizeOf = { _, value -> value.size.toLong() }
        )
        cache.put("tile-1", ByteArray(100))
        cache.put("tile-2", ByteArray(200))

        val freed = cache.reclaim(KellvynCachePressure.CRITICAL, Long.MAX_VALUE)

        assertEquals(300L, freed)
        assertEquals(0L, cache.sizeBytes())
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun registryReclaimsHigherPriorityCachesFirstAndHonorsTargetBytes() {
        val registry = KellvynResourceCacheRegistry()
        val low = KellvynManagedMemoryCache<String, ByteArray>(
            "search-index-cache", 10, 1_000L, { _, v -> v.size.toLong() }
        )
        val high = KellvynManagedMemoryCache<String, ByteArray>(
            "image-tile-cache", 90, 1_000L, { _, v -> v.size.toLong() }
        )
        val lowRegistration = registry.register(low)
        val highRegistration = registry.register(high)
        low.put("low", ByteArray(100))
        high.put("high", ByteArray(100))

        val released = registry.reclaim(KellvynCachePressure.CRITICAL, targetBytes = 100L)

        assertEquals(100L, released)
        assertEquals(0L, high.sizeBytes())
        assertEquals(100L, low.sizeBytes())

        highRegistration.close()
        lowRegistration.close()
        assertEquals(0, registry.activeReclaimerCount())
    }

    @Test
    fun uiHiddenPressureReleasesHalfOfAnEphemeralCache() {
        val cache = KellvynManagedMemoryCache<String, ByteArray>(
            "preview-cache", 50, 1_000L, { _, v -> v.size.toLong() }
        )
        cache.put("one", ByteArray(100))
        cache.put("two", ByteArray(100))

        val released = cache.reclaim(KellvynCachePressure.UI_HIDDEN, Long.MAX_VALUE)

        assertEquals(100L, released)
        assertEquals(100L, cache.sizeBytes())
    }

    @Test
    fun closingRegistrationStopsFutureReclaimCallbacks() {
        val registry = KellvynResourceCacheRegistry()
        val cache = KellvynManagedMemoryCache<String, ByteArray>(
            "vector-preview", 50, 1_000L, { _, v -> v.size.toLong() }
        )
        cache.put("preview", ByteArray(100))
        val registration = registry.register(cache)
        assertTrue(registry.activeReclaimerCount() > 0)

        registration.close()
        val released = registry.reclaim(KellvynCachePressure.CRITICAL)

        assertEquals(0L, released)
        assertEquals(100L, cache.sizeBytes())
        assertFalse(registry.activeReclaimerCount() > 0)
    }
}
