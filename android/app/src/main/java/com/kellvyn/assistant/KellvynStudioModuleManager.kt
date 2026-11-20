package com.kellvyn.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
    WORKFLOW_AUTOMATION,
    WEB_RESEARCH,
    MEMORY_READ,
    SECURITY_AUDIT,
    AGENT_DESIGN,
    AGENT_EVALUATION,
    // Precise operational domains: diagnostics must not imply the action engine exists.
    TASK_QUEUE_DIAGNOSTICS,
    BOT_DIAGNOSTICS,
    ANDROID_BUILD_DIAGNOSTICS,
    IMAGE_GENERATION,
    ANIMATION,
    VIDEO_EDITING,
    VIDEO_GENERATION,
    AUDIO_PERCEPTION,
    AUDIO_GENERATION,
    GAME_INTERACTION,
    APP_CONTROL,
    SCREEN_CAPTURE,
    CODE_EXECUTION,
    ARTIFACT_STORAGE
}

internal enum class KellvynStudioPermission {
    PRIVATE_WORKSPACE_READ,
    PRIVATE_WORKSPACE_WRITE,
    NETWORK_ACCESS,
    INSTALL_APK,
    ACCESSIBILITY_CONTROL,
    CAMERA_CAPTURE,
    MICROPHONE_CAPTURE,
    USER_MEDIA_READ,
    PRIVATE_ARTIFACT_WRITE,
    WORKFLOW_CONTROL
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
    val toolSpecs: List<KellvynToolSpec>
) {
    val toolNames: Set<String> get() = toolSpecs.map { it.name }.toSet()
    val toolRisks: Map<String, KellvynToolRisk> get() = toolSpecs.associate { it.name to it.risk }
    init {
        require(moduleId.matches(Regex("[a-z][a-z0-9-]{1,47}")))
        require(version > 0)
        require(capabilities.isNotEmpty())
        require(supportedExtensions.all { it.matches(Regex("[a-z0-9]{1,12}")) })
        require(minSdk in 24..100)
        require(estimatedLoadWorkingSetBytes >= 0L && estimatedResidentBytes >= 0L)
        require(toolSpecs.isNotEmpty())
        require(toolNames.size == toolSpecs.size) { "O manifesto contém nomes de ferramenta duplicados." }
        require(toolSpecs.all { it.name.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,63}")) })
        require(toolSpecs.all { spec ->
            spec.capabilities.isNotEmpty() && spec.capabilities.all { it in capabilities }
        }) {
            "Cada ferramenta precisa declarar capacidades contidas no manifesto do estúdio."
        }
        toolRisks.forEach { (name, risk) ->
            val requiredPermission = when (risk) {
                KellvynToolRisk.READ_ONLY -> null
                KellvynToolRisk.NETWORK_READ -> KellvynStudioPermission.NETWORK_ACCESS
                KellvynToolRisk.REVERSIBLE_WRITE,
                KellvynToolRisk.DESTRUCTIVE -> KellvynStudioPermission.PRIVATE_WORKSPACE_WRITE
                KellvynToolRisk.DEVICE_CONTROL -> KellvynStudioPermission.ACCESSIBILITY_CONTROL
                KellvynToolRisk.ARTIFACT_CREATE -> KellvynStudioPermission.PRIVATE_ARTIFACT_WRITE
                KellvynToolRisk.APPEND_ONLY_TASK_CREATE,
                KellvynToolRisk.CANCEL_QUEUED_TASK -> KellvynStudioPermission.WORKFLOW_CONTROL
            }
            require(requiredPermission == null || requiredPermission in permissions) {
                "A ferramenta " + name + " não declara a permissão necessária para seu risco."
            }
        }
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
    private val taskSequence = AtomicLong(0L)

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

    /**
     * Creates lightweight tool adapters from registered manifests. The engine itself remains
     * unloaded until the selected tool is actually executed; each call holds a lease for safety.
     */
    fun toolsForCapabilities(capabilities: Set<KellvynStudioCapability>): List<KellvynAgentTool> {
        if (capabilities.isEmpty()) return emptyList()
        val selected = definitions.values.map { it.manifest }
            .filter { manifest -> manifest.capabilities.any { it in capabilities } }
            .sortedBy { it.moduleId }
        val adapters = selected.flatMap { manifest ->
            manifest.toolSpecs.map { declaredSpec ->
                object : KellvynAgentTool, KellvynResourceManagedAgentTool {
                    override val spec = declaredSpec

                    override suspend fun execute(arguments: Map<String, String>): KellvynToolResult =
                        executeWithResourceControl(arguments)

                    override suspend fun executeWithResourceControl(
                        arguments: Map<String, String>
                    ): KellvynToolResult {
                        val workload = if (manifest.workloadClass.ordinal > declaredSpec.workloadClass.ordinal) {
                            manifest.workloadClass
                        } else {
                            declaredSpec.workloadClass
                        }
                        val estimatedBytes = maxOf(
                            manifest.estimatedLoadWorkingSetBytes,
                            manifest.estimatedResidentBytes,
                            declaredSpec.estimatedWorkingSetBytes
                        )
                        return taskBroker.execute(
                            KellvynResourceRequest(
                                taskId = ("studio-tool-" + manifest.moduleId + "-" +
                                    declaredSpec.name + "-" + taskSequence.incrementAndGet()).take(96),
                                workload = workload,
                                estimatedWorkingSetBytes = estimatedBytes
                            )
                        ) {
                            try {
                                withTimeout(declaredSpec.timeoutMs) {
                                    val lease = acquireAlreadyAdmitted(manifest.moduleId)
                                    try {
                                        val delegate = lease.tools.singleOrNull {
                                            it.spec.name == declaredSpec.name
                                        } ?: return@withTimeout KellvynToolResult(
                                            false,
                                            "A ferramenta declarada não existe no motor carregado."
                                        )
                                        if (delegate.spec != declaredSpec) {
                                            return@withTimeout KellvynToolResult(
                                                false,
                                                "O contrato da ferramenta mudou após o carregamento."
                                            )
                                        }
                                        delegate.execute(arguments)
                                    } finally {
                                        lease.close()
                                    }
                                }
                            } catch (timeout: TimeoutCancellationException) {
                                currentCoroutineContext().ensureActive()
                                KellvynToolResult(
                                    false,
                                    "A ferramenta excedeu o tempo limite depois da admissão dos recursos."
                                )
                            }
                        }
                    }
                }
            }
        }
        val duplicateNames = adapters.groupingBy { it.spec.name }.eachCount()
            .filterValues { it > 1 }.keys
        require(duplicateNames.isEmpty()) {
            "Há ferramentas duplicadas entre os estúdios: " + duplicateNames.sorted().joinToString()
        }
        return adapters
    }

    fun registerIfAbsent(definition: KellvynStudioModuleDefinition) {
        val existing = definitions[definition.manifest.moduleId]
        if (existing != null) {
            require(existing.manifest == definition.manifest) {
                "O módulo já existe com um manifesto diferente: " + definition.manifest.moduleId
            }
            return
        }
        register(definition)
    }

    suspend fun acquire(moduleId: String): KellvynStudioModuleLease =
        acquireInternal(moduleId, resourceAdmissionAlreadyGranted = false)

    private suspend fun acquireAlreadyAdmitted(moduleId: String): KellvynStudioModuleLease =
        acquireInternal(moduleId, resourceAdmissionAlreadyGranted = true)

    private suspend fun acquireInternal(
        moduleId: String,
        resourceAdmissionAlreadyGranted: Boolean
    ): KellvynStudioModuleLease {
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
            val createModule: suspend () -> KellvynStudioModule = {
                definition.factory.create()
            }
            val created = if (resourceAdmissionAlreadyGranted) {
                createModule()
            } else {
                taskBroker.execute(
                    KellvynResourceRequest(
                        taskId = ("studio-load-" + moduleId + "-" + now).take(96),
                        workload = manifest.workloadClass,
                        estimatedWorkingSetBytes = maxOf(
                            manifest.estimatedLoadWorkingSetBytes,
                            manifest.estimatedResidentBytes
                        )
                    ),
                    operation = createModule
                )
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
            val actualSpecs = created.tools.map { it.spec }
            if (actualSpecs.toSet() != manifest.toolSpecs.toSet() ||
                actualSpecs.size != manifest.toolSpecs.size
            ) {
                runCatching { created.close() }
                error("Os contratos de ferramenta carregados divergem do manifesto do módulo.")
            }

            val state = Loaded(created, now).also { it.activeLeases = 1 }
            check(loaded.putIfAbsent(moduleId, state) == null) {
                runCatching { created.close() }
                "Outro carregamento do módulo foi concluído simultaneamente."
            }
            lease(moduleId, state)
        }
    }

    /**
     * Trim transient caches in every loaded engine, including leased engines, then close only
     * idle engines. Reclaimers are required to be thread-safe and non-blocking; durable state
     * and active operation inputs must not be invalidated.
     */
    fun trimIdleModules(level: KellvynCachePressure): Int {
        val toClose = mutableListOf<Loaded>()
        loaded.entries.toList().forEach { entry ->
            val state = entry.value
            runCatching { state.module.trimTransientResources(level) }
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
