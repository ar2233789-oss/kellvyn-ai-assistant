package com.kellvyn.assistant

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal enum class KellvynStudioCapability {
    CODE_NAVIGATION,
    CODE_EDITING,
    ANDROID_BUILD,
    ANDROID_DEVICE_TEST,
    UI_LAYOUT,
    VISUAL_ANALYSIS,
    RASTER_IMAGE_EDIT,
    VECTOR_GRAPHICS,
    SCENE_3D,
    CAD_PARAMETRIC,
    DATA_MODELING,
    NEURAL_TRAINING,
    NEURAL_INFERENCE,
    DEVICE_DIAGNOSTICS,
    WORKFLOW_AUTOMATION
}

internal enum class KellvynStudioPermission {
    PRIVATE_WORKSPACE_READ,
    PRIVATE_WORKSPACE_WRITE,
    NETWORK_ACCESS,
    INSTALL_APK,
    ACCESSIBILITY_CONTROL,
    CAMERA_CAPTURE,
    MICROPHONE_CAPTURE,
    USER_MEDIA_READ
}

internal data class KellvynStudioModuleManifest(
    val moduleId: String,
    val version: Int,
    val capabilities: Set<KellvynStudioCapability>,
    val supportedExtensions: Set<String>,
    val minSdk: Int,
    val requiresNetwork: Boolean,
    val workloadClass: KellvynWorkloadClass,
    val estimatedLoadWorkingSetBytes: Long,
    val estimatedResidentBytes: Long,
    val permissions: Set<KellvynStudioPermission>,
    val toolNames: Set<String>
) {
    init {
        require(moduleId.matches(Regex("[a-z][a-z0-9-]{1,47}")))
        require(version > 0)
        require(capabilities.isNotEmpty())
        require(supportedExtensions.all { it.matches(Regex("[a-z0-9]{1,12}")) })
        require(minSdk in 24..100)
        require(estimatedLoadWorkingSetBytes >= 0L && estimatedResidentBytes >= 0L)
        require(toolNames.isNotEmpty() && toolNames.all {
            it.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,63}"))
        })
        if (requiresNetwork) require(KellvynStudioPermission.NETWORK_ACCESS in permissions)
        if (workloadClass != KellvynWorkloadClass.HEAVY) {
            require(maxOf(estimatedLoadWorkingSetBytes, estimatedResidentBytes) <= 512L * MEBIBYTE) {
                "Módulo não pesado declarou uma carga estimada acima de 512 MiB."
            }
        }
    }

    companion object {
        private const val MEBIBYTE = 1024L * 1024L
    }
}

internal interface KellvynStudioModule : AutoCloseable {
    val manifest: KellvynStudioModuleManifest
    val tools: List<KellvynAgentTool>

    /** Release regenerable buffers/scenes/indexes without deleting durable artifacts. */
    fun trimTransientResources(level: KellvynCachePressure)
}

internal fun interface KellvynStudioModuleFactory {
    suspend fun create(): KellvynStudioModule
}

internal data class KellvynStudioModuleDefinition(
    val manifest: KellvynStudioModuleManifest,
    val factory: KellvynStudioModuleFactory
)

/**
 * Registers manifests without initializing engines. A module is constructed only after a
 * capability is requested and the shared resource broker grants the declared budget.
 * Active leases protect modules from pressure eviction; idle modules are closed on demand.
 */
internal class KellvynStudioModuleManager(
    private val deviceSdkInt: Int,
    private val taskBroker: KellvynTaskBroker,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class Loaded(
        val module: KellvynStudioModule,
        now: Long
    ) {
        val lock = Any()
        var activeLeases = 0
        var evicted = false
        var lastUsedAtMillis = now
    }

    private val definitions = ConcurrentHashMap<String, KellvynStudioModuleDefinition>()
    private val loaded = ConcurrentHashMap<String, Loaded>()
    private val loadMutex = Mutex()

    init {
        require(deviceSdkInt >= 24)
    }

    @Synchronized
    fun register(definition: KellvynStudioModuleDefinition) {
        val manifest = definition.manifest
        require(manifest.minSdk <= deviceSdkInt) {
            "O módulo " + manifest.moduleId + " exige um SDK superior ao disponível."
        }
        val duplicateTools = definitions.values.flatMap { it.manifest.toolNames }
            .intersect(manifest.toolNames)
        require(duplicateTools.isEmpty()) {
            "Ferramentas já registradas por outro módulo: " + duplicateTools.sorted().joinToString()
        }
        require(definitions.putIfAbsent(manifest.moduleId, definition) == null) {
            "O módulo " + manifest.moduleId + " já foi registrado."
        }
    }

    fun manifests(): List<KellvynStudioModuleManifest> =
        definitions.values.map { it.manifest }.sortedBy { it.moduleId }

    fun findByCapability(capability: KellvynStudioCapability): List<KellvynStudioModuleManifest> =
        definitions.values.map { it.manifest }
            .filter { capability in it.capabilities }
            .sortedWith(compareBy<KellvynStudioModuleManifest> { it.estimatedResidentBytes }
                .thenBy { it.moduleId })

    suspend fun acquire(moduleId: String): KellvynStudioModuleLease {
        val definition = definitions[moduleId]
            ?: throw IllegalArgumentException("Módulo não registrado: " + moduleId)
        return loadMutex.withLock {
            val now = clock().also { require(it > 0L) }
            unloadExpiredModules(now)
            val existing = loaded[moduleId]
            if (existing != null) {
                val acquired = synchronized(existing.lock) {
                    if (existing.evicted) false
                    else {
                        existing.activeLeases++
                        existing.lastUsedAtMillis = now
                        true
                    }
                }
                if (acquired) return@withLock lease(moduleId, existing)
                loaded.remove(moduleId, existing)
            }

            val manifest = definition.manifest
            val created = taskBroker.execute(
                KellvynResourceRequest(
                    taskId = ("studio-load-" + moduleId + "-" + now).take(96),
                    workload = manifest.workloadClass,
                    estimatedWorkingSetBytes = maxOf(
                        manifest.estimatedLoadWorkingSetBytes,
                        manifest.estimatedResidentBytes
                    )
                )
            ) {
                definition.factory.create()
            }

            if (created.manifest != manifest) {
                runCatching { created.close() }
                error("O módulo carregado não corresponde ao manifesto registrado.")
            }
            val actualNames = created.tools.map { it.spec.name }
            val duplicateToolNames = actualNames.groupingBy { it }.eachCount()
                .filterValues { it > 1 }.keys
            if (duplicateToolNames.isNotEmpty()) {
                runCatching { created.close() }
                error("O módulo contém ferramentas duplicadas: " + duplicateToolNames.sorted().joinToString() + ".")
            }
            val actualToolSet = actualNames.toSet()
            val undeclaredTools = actualToolSet - manifest.toolNames
            val missingTools = manifest.toolNames - actualToolSet
            if (undeclaredTools.isNotEmpty() || missingTools.isNotEmpty()) {
                runCatching { created.close() }
                error("As ferramentas carregadas divergem do manifesto do módulo.")
            }

            val state = Loaded(created, now).also { it.activeLeases = 1 }
            check(loaded.putIfAbsent(moduleId, state) == null) {
                runCatching { created.close() }
                "Outro carregamento do módulo foi concluído simultaneamente."
            }
            lease(moduleId, state)
        }
    }

    /** Close only idle engines. Active leases are never evicted in the middle of a task. */
    fun trimIdleModules(level: KellvynCachePressure): Int {
        val toClose = mutableListOf<Loaded>()
        loaded.entries.toList().forEach { entry ->
            val state = entry.value
            val mayRemove = synchronized(state.lock) {
                if (state.activeLeases == 0 && !state.evicted) {
                    state.evicted = true
                    true
                } else {
                    false
                }
            }
            if (mayRemove) {
                loaded.remove(entry.key, state)
                runCatching { state.module.trimTransientResources(level) }
                toClose += state
            }
        }
        toClose.forEach { runCatching { it.module.close() } }
        return toClose.size
    }

    /** Unload modules that have been idle longer than the configured TTL; no timer/polling. */
    fun unloadExpiredModules(nowMillis: Long = clock(), maxIdleMillis: Long = DEFAULT_IDLE_TTL_MILLIS): Int {
        require(nowMillis > 0L && maxIdleMillis >= 0L)
        val toClose = mutableListOf<Loaded>()
        loaded.entries.toList().forEach { entry ->
            val state = entry.value
            val mayRemove = synchronized(state.lock) {
                val idleFor = (nowMillis - state.lastUsedAtMillis).coerceAtLeast(0L)
                if (state.activeLeases == 0 && !state.evicted && idleFor >= maxIdleMillis) {
                    state.evicted = true
                    true
                } else {
                    false
                }
            }
            if (mayRemove) {
                loaded.remove(entry.key, state)
                toClose += state
            }
        }
        toClose.forEach { runCatching { it.module.close() } }
        return toClose.size
    }

    fun loadedModuleIds(): Set<String> = loaded.keys.toSet()

    fun activeLeaseCount(moduleId: String): Int =
        loaded[moduleId]?.let { state -> synchronized(state.lock) { state.activeLeases } } ?: 0

    private fun lease(moduleId: String, state: Loaded) = KellvynStudioModuleLease(
        moduleId = moduleId,
        manifest = state.module.manifest,
        tools = state.module.tools
    ) {
        synchronized(state.lock) {
            check(state.activeLeases > 0) { "Lease de módulo liberado mais de uma vez." }
            state.activeLeases--
            state.lastUsedAtMillis = clock()
        }
    }

    private companion object {
        const val DEFAULT_IDLE_TTL_MILLIS = 120_000L
    }
}

internal class KellvynStudioModuleLease internal constructor(
    val moduleId: String,
    val manifest: KellvynStudioModuleManifest,
    val tools: List<KellvynAgentTool>,
    private val releaseAction: () -> Unit
) : AutoCloseable {
    @Volatile
    private var closed = false

    override fun close() {
        if (!closed) {
            synchronized(this) {
                if (!closed) {
                    closed = true
                    releaseAction()
                }
            }
        }
    }
}
