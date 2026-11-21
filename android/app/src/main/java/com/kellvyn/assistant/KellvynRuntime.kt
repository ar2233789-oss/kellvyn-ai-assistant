package com.kellvyn.assistant

import android.content.Context
import android.net.Uri
import com.kellvyn.assistant.neural.KellvynNeuralModelStore
import com.kellvyn.assistant.neural.KellvynNeuralChatRuntime
import com.kellvyn.assistant.neural.KellvynTrainingConfig
import com.kellvyn.assistant.neural.KellvynTrainingDocument
import com.kellvyn.assistant.neural.KellvynInstructionTrainingExample
import com.kellvyn.assistant.neural.KellvynTrainingManifest
import com.kellvyn.assistant.neural.KellvynTrainingPipeline
import com.kellvyn.assistant.neural.KellvynRestoredNeuralBundle
import com.kellvyn.assistant.neural.KellvynTransformerTrainingConfig
import com.kellvyn.assistant.neural.KellvynTransformerTrainingManifest
import com.kellvyn.assistant.neural.KellvynTransformerTrainingPipeline
import com.kellvyn.assistant.neural.KellvynRestoredTransformerBundle
import com.kellvyn.assistant.neural.KellvynMultiBlockTrainingConfig
import com.kellvyn.assistant.neural.KellvynMultiBlockTrainingManifest
import com.kellvyn.assistant.neural.KellvynMultiBlockTrainingPipeline
import com.kellvyn.assistant.neural.KellvynMultiBlockTrainingQualityGate
import com.kellvyn.assistant.neural.KellvynMultiBlockTransformer
import com.kellvyn.assistant.neural.KellvynRestoredMultiBlockBundle
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Temporary application boundary while the original Kellvyn neural backend is built.
 *
 * This class intentionally contains no downloaded model and no hidden fallback provider.
 * It reports that inference is unavailable instead of pretending the agent is ready.
 * The real runtime will be connected only after the neural model and runtime gates pass.
 */
internal class KellvynRuntime(context: Context) {
    interface Listener {
        fun onStatus(message: String)
        fun onPartial(text: String)
        fun onComplete(text: String, backend: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val memoryRepository = KellvynEncryptedMemoryRepository(appContext)
    private val localMemory = KellvynMemory(memoryRepository)
    private val projectWorkspace = ProjectWorkspaceStore(appContext)
    private val projectWriteApprovalGate = KellvynProjectWriteApprovalGate()
    private val projectContextRetriever = KellvynProjectContextRetriever(projectWorkspace)
    private val resourceRuntime = KellvynResourceRuntime.get(appContext)
    private val taskBroker = resourceRuntime.taskBroker
    private val agentLabTraceStore = KellvynAgentLabTraceStore(
        File(appContext.filesDir, "agent-lab-traces"),
        KellvynAndroidTaskCheckpointCipher("kellvyn.agent-lab-traces.aes-gcm.v1")
    )
    private val agentLabTraceRecorder = KellvynAgentLabTraceStoreRecorder(agentLabTraceStore)
    private val specialistProfileStore = KellvynSpecialistProfileStore(
        File(appContext.filesDir, "specialist-profiles"),
        KellvynAndroidTaskCheckpointCipher("kellvyn.specialist-profiles.aes-gcm.v1")
    )
    @Volatile
    private var activeSpecialistProfilesSnapshot =
        KellvynSpecialistProfileRegistry.default().profiles()

    init {
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynCodeNavigationStudio.definition(projectWorkspace)
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynImageStudio.definition(appContext, resourceRuntime.artifacts)
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynAnimationStudio.definition(appContext, resourceRuntime.artifacts)
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynVectorStudio.definition(appContext, resourceRuntime.artifacts)
        )
        // O exportador 3D legado foi isolado: seu arquivo-fonte está corrompido.
        // Só será reativado depois de reconstrução e testes próprios.
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynSecurityAuditStudio.definition(projectWorkspace)
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynDataArchitectureStudio.definition(projectWorkspace)
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynAndroidBuildStatusStudio.definition(appContext, projectWorkspace)
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynWebResearchStudio.definition(appContext)
        )
        // Legacy agent studios are intentionally not registered in the central KN runtime.
        // Their sources, tests and encrypted history remain available for migration/audit.
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynBotOperationsStudio.definition(appContext) { isReady() }
        )
    }
    private val neuralModelStore = KellvynNeuralModelStore(
        File(appContext.filesDir, "neural")
    )
    private val neuralChatRuntime = KellvynNeuralChatRuntime(neuralModelStore)
    private val readinessPreferences = appContext.getSharedPreferences(
        "kellvyn-kn004-readiness",
        Context.MODE_PRIVATE
    )
    private val chatScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var centralKernel: KellvynCognitiveKernel? = null
    @Volatile private var activeReplyJob: Job? = null
    @Volatile private var initializationJob: Job? = null
    @Volatile private var initializationDetail: String =
        "KN-004 ainda não foi carregado."
    private val taskCheckpointStore = KellvynTaskCheckpointStore(
        File(appContext.filesDir, "task-checkpoints"),
        KellvynAndroidTaskCheckpointCipher()
    )
    private val taskCheckpointRepository = object : KellvynTaskCheckpointRepository {
        override suspend fun append(
            state: KellvynTaskState,
            createdAtMillis: Long,
            expectedPreviousSha256: String?
        ): KellvynTaskCheckpointMetadata = saveTaskCheckpoint(
            state,
            createdAtMillis,
            expectedPreviousSha256
        )

        override suspend fun loadLatest(
            taskId: String
        ): KellvynTaskCheckpointSnapshot? = restoreLatestTaskCheckpoint(taskId)
    }

    init {
        initializeCentralKernel()
    }

    /** Gives the agent layer access to the same app-private memory store. */
    fun memoryStore(): KellvynMemory = localMemory

    /** A fresh resource snapshot for the operations specialist and diagnostics. */
    suspend fun resourceSnapshot(): KellvynResourceSnapshot = resourceRuntime.snapshot()

    /**
     * Imports one explicitly selected Android image into the private artifact store.
     * This is a trusted Android-content-resolver boundary for the main chat attachment flow;
     * the model never receives a filesystem path or raw image bytes.
     */
    suspend fun importUserImageArtifact(
        uri: Uri,
        displayName: String,
        taskId: String
    ): KellvynArtifactRef = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(uri)
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: throw IOException("O Android não informou o tipo do anexo selecionado.")
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException(
                "O importador local aceita somente PNG, JPEG e WebP."
            )
        }
        val stem = displayName.substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.', displayName)
            .replace(Regex("[^A-Za-z0-9_-]+"), "-")
            .trim('-', '_')
            .take(96)
            .ifBlank { "imagem" }
        val safeName = "$stem.$extension"
        val input = resolver.openInputStream(uri)
            ?: throw IOException("Não foi possível abrir o anexo selecionado.")
        input.use { stream ->
            resourceRuntime.artifacts.put(
                taskId = taskId,
                fileName = safeName,
                mediaType = mimeType,
                producerId = "user.import",
                input = BoundedArtifactInputStream(stream, MAX_IMPORTED_IMAGE_BYTES)
            )
        }
    }

    /**
     * Persists a complete planner snapshot in app-private storage.
     * The caller must pass the last observed hash when continuing an existing task.
     */
    suspend fun saveTaskCheckpoint(
        state: KellvynTaskState,
        createdAtMillis: Long,
        expectedPreviousSha256: String? = null
    ): KellvynTaskCheckpointMetadata = withContext(Dispatchers.IO) {
        taskCheckpointStore.append(state, createdAtMillis, expectedPreviousSha256)
    }

    /** Restores only after the store has verified every hash in the task's history. */
    suspend fun restoreLatestTaskCheckpoint(
        taskId: String
    ): KellvynTaskCheckpointSnapshot? = withContext(Dispatchers.IO) {
        taskCheckpointStore.loadLatest(taskId)
    }

    /** Returns the verified checkpoint chain for displaying or auditing task history. */
    suspend fun taskCheckpointHistory(
        taskId: String
    ): List<KellvynTaskCheckpointMetadata> = withContext(Dispatchers.IO) {
        taskCheckpointStore.history(taskId)
    }

    /**
     * Trains the original Kellvyn model only from explicit local samples and saves the
     * tokenizer, weights, optimizer state and provenance manifest in app-private storage.
     *
     * This does not enable chat inference; evaluation and runtime integration remain separate gates.
     */
    suspend fun trainOwnNeuralModel(
        documents: List<KellvynTrainingDocument>,
        config: KellvynTrainingConfig = KellvynTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): KellvynTrainingManifest {
        val trained = runHeavyTask("training-documents-${createdAtMillis}") {
            withContext(Dispatchers.Default) {
                val trainingContext = currentCoroutineContext()
                KellvynTrainingPipeline.train(
                    documents = documents,
                    config = config,
                    createdAtMillis = createdAtMillis,
                    cancellationCheck = { trainingContext.ensureActive() }
                )
            }
        }
        withContext(Dispatchers.IO) {
            neuralModelStore.save(trained)
        }
        return trained.manifest
    }

    /**
     * Trains the original Kellvyn model from explicitly authorized prompt/response examples.
     * Only response tokens are optimized; the prompt is used as conditioning context.
     */
    suspend fun trainOwnInstructionModel(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynTrainingConfig = KellvynTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): KellvynTrainingManifest {
        val trained = runHeavyTask("training-instructions-${createdAtMillis}") {
            withContext(Dispatchers.Default) {
                val trainingContext = currentCoroutineContext()
                KellvynTrainingPipeline.trainInstructions(
                    examples = examples,
                    config = config,
                    createdAtMillis = createdAtMillis,
                    cancellationCheck = { trainingContext.ensureActive() }
                )
            }
        }
        withContext(Dispatchers.IO) {
            neuralModelStore.save(trained)
        }
        return trained.manifest
    }

    /**
     * Trains the original KN-003 decoder-only Transformer from authorized local instruction
     * examples and stores its own tokenizer, weights, optimizer moments and manifest.
     * This does not connect the model to chat or silently replace the KN-002 experiment.
     */
    suspend fun trainOwnTransformerModel(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynTransformerTrainingConfig = KellvynTransformerTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): KellvynTransformerTrainingManifest {
        val trained = runHeavyTask("training-transformer-${createdAtMillis}") {
            withContext(Dispatchers.Default) {
                val trainingContext = currentCoroutineContext()
                KellvynTransformerTrainingPipeline.train(
                    examples = examples,
                    config = config,
                    createdAtMillis = createdAtMillis,
                    cancellationCheck = { trainingContext.ensureActive() }
                )
            }
        }
        withContext(Dispatchers.IO) {
            neuralModelStore.saveTransformer(trained)
        }
        return trained.manifest
    }

    /**
     * Trains the original KN-004 multi-block Transformer from explicitly authorized local
     * examples. The checkpoint is stored separately from KN-003 and is not connected to chat.
     */
    suspend fun trainOwnMultiBlockModel(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): KellvynMultiBlockTrainingManifest {
        val modelConfig = KellvynMultiBlockTransformer.Config(
            vocabularySize = 256 + config.maxMerges + 1,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            headCount = config.headCount,
            feedForwardSize = config.feedForwardSize,
            depth = config.depth,
            seed = config.seed
        )
        val trained = runHeavyTask(
            taskId = "training-kn004-$createdAtMillis",
            estimatedWorkingSetBytes = modelConfig.estimatedTrainingWorkingSetBytes()
        ) {
            withContext(Dispatchers.Default) {
                val trainingContext = currentCoroutineContext()
                KellvynMultiBlockTrainingPipeline.train(
                    examples = examples,
                    config = config,
                    createdAtMillis = createdAtMillis,
                    cancellationCheck = { trainingContext.ensureActive() }
                )
            }
        }
        // Test the candidate in memory first. The currently installed checkpoint remains
        // untouched unless both the numeric quality gate and held-out TEST benchmark pass.
        val status = withContext(Dispatchers.Default) {
            neuralChatRuntime.initializeCandidate(trained)
        }
        if (!status.ready || !status.qualityApprovedForBenchmark || status.modelFingerprint == null) {
            neuralChatRuntime.unload()
            return trained.manifest
        }
        val behavior = withContext(Dispatchers.Default) {
            neuralChatRuntime.runBehaviorBenchmark(
                examples = examples.filter {
                    it.split == com.kellvyn.assistant.neural.KellvynTrainingSplit.TEST
                }
            )
        }
        if (!behavior.eligibleForIntegrationReview) {
            neuralChatRuntime.unload()
            return trained.manifest
        }
        withContext(Dispatchers.IO) {
            neuralModelStore.saveMultiBlock(trained)
        }
        readinessPreferences.edit()
            .putString(KEY_APPROVED_FINGERPRINT, behavior.modelFingerprint)
            .putInt(KEY_APPROVED_TEST_COUNT, behavior.sampleCount)
            .putFloat(KEY_APPROVED_EXACT_RATE, behavior.exactMatchRate.toFloat())
            .putFloat(KEY_APPROVED_TOKEN_F1, behavior.meanTokenF1.toFloat())
            .putLong(KEY_APPROVED_AT_MILLIS, System.currentTimeMillis())
            .apply()
        neuralChatRuntime.unload()
        refreshCentralKernel()
        return trained.manifest
    }

    /** Loads the independently versioned KN-004 tokenizer, model and provenance bundle. */
    suspend fun loadOwnMultiBlockModel(): KellvynRestoredMultiBlockBundle? =
        withContext(Dispatchers.IO) {
            neuralModelStore.loadMultiBlock()
        }

    /**
     * Creates a strict KN-004 inference adapter for controlled benchmarks only.
     * MainActivity.isReady() remains false until the full behavioral/device gates pass.
     */
    suspend fun createOwnMultiBlockInferenceEngine(
        config: KellvynMultiBlockInferenceEngine.Config? = null
    ): KellvynMultiBlockInferenceEngine? {
        val bundle = withContext(Dispatchers.IO) {
            neuralModelStore.loadMultiBlockForInference()
        } ?: return null
        val contextLength = bundle.model.config.contextLength
        if (contextLength < 128) return null
        val quality = KellvynMultiBlockTrainingQualityGate.evaluate(bundle.manifest)
        if (!quality.eligibleForProtocolBenchmark) return null
        val safeDefault = KellvynMultiBlockInferenceEngine.Config(
            maxGenerationTokens = minOf(
                256,
                maxOf(64, contextLength / 8),
                contextLength - 1
            )
        )
        return KellvynMultiBlockInferenceEngine(
            model = bundle.model,
            tokenizer = bundle.tokenizer,
            config = config ?: safeDefault
        )
    }

    /**
     * Creates the single central KN cognition loop over the validated KN-004 model.
     * This is a controlled runtime API; it does not connect or enable MainActivity chat.
     */
    suspend fun createOwnMultiBlockCognitiveKernel(
        activeProjectId: String? = null,
        inferenceConfig: KellvynMultiBlockInferenceEngine.Config? = null,
        agentConfig: KellvynAgentConfig = KellvynAgentConfig()
    ): KellvynCognitiveKernel? {
        val inference = createOwnMultiBlockInferenceEngine(inferenceConfig) ?: return null
        return createCentralCognition(
            inference = inference,
            activeProjectId = activeProjectId,
            config = agentConfig
        )
    }

    /**
     * Restores a locally trained KN-003 model and wraps it in the strict inference adapter.
     * This makes the adapter available to controlled tests; it does not enable MainActivity chat.
     */
    suspend fun createOwnTransformerInferenceEngine(
        config: KellvynTransformerInferenceEngine.Config? = null
    ): KellvynTransformerInferenceEngine? {
        val bundle = loadOwnTransformerModel() ?: return null
        val contextLength = bundle.model.config.contextLength
        // This is only an entry gate for the stricter KELLVYN/1 protocol benchmark. It does
        // not establish conversational ability or production readiness.
        if (contextLength < 128) return null
        val quality = KellvynTransformerTrainingQualityGate.evaluate(bundle.manifest)
        if (!quality.eligibleForProtocolBenchmark) return null
        val safeDefault = KellvynTransformerInferenceEngine.Config(
            maxGenerationTokens =
                KellvynTransformerInferenceEngine.safeDefaultGenerationTokens(contextLength)
        )
        return KellvynTransformerInferenceEngine(
            model = bundle.model,
            tokenizer = bundle.tokenizer,
            config = config ?: safeDefault
        )
    }

    /** Loads the KN-003 bundle only after validating its checksum and manifest. */
    suspend fun loadOwnTransformerModel(): KellvynRestoredTransformerBundle? =
        withContext(Dispatchers.IO) {
            neuralModelStore.loadTransformer()
        }

    /** Loads the last integrity-checked local tokenizer/model bundle, if one exists. */
    suspend fun loadOwnNeuralModel(): KellvynRestoredNeuralBundle? =
        withContext(Dispatchers.IO) {
            neuralModelStore.load()
        }

    /** Deletes the locally trained model and all recovery files. */
    suspend fun deleteOwnNeuralModel() = withContext(Dispatchers.IO) {
        neuralModelStore.delete()
    }

    /**
     * Records owner approval for one exact file write. The returned one-use ID must be
     * attached to the same project/path/content; changing any field invalidates it.
     * The UI must call this only after the owner explicitly confirms the proposed diff.
     */
    fun approveProjectFileWrite(projectId: String, path: String, content: String): String =
        projectWriteApprovalGate.approveWrite(projectId, path, content)

    /** Records a separate one-use owner approval for deleting one exact file. */
    fun approveProjectFileDeletion(projectId: String, path: String): String =
        projectWriteApprovalGate.approveDelete(projectId, path)

    /** Builds the local workspace tool catalogue. Specialist delegation filters this to READ_ONLY. */
    fun createProjectWorkspaceToolRegistry(
        activeProjectId: String? = null
    ): KellvynToolRegistry {
        val workspaceTools = KellvynProjectWorkspaceTools.create(projectWorkspace, projectWriteApprovalGate)
        val resourceTools = KellvynResourceTools.create { resourceSnapshot() }
        val automationTools = KellvynAutomationTools.create(
            pendingCountProvider = { resourceRuntime.pendingScheduledTaskCount() },
            scheduleResourceSnapshot = { delayMillis ->
                resourceRuntime.enqueueResourceSnapshotProbe(delayMillis)
            },
            taskStatusProvider = { taskId ->
                resourceRuntime.scheduledTaskStatus(taskId)
            },
            cancelQueuedTask = { taskId ->
                resourceRuntime.cancelQueuedScheduledTask(taskId)
            }
        )
        val registeredCapabilities = resourceRuntime.studioModules.manifests()
            .flatMap { it.capabilities }
            .toSet()
        val studioTools = resourceRuntime.studioModules.toolsForCapabilities(
            registeredCapabilities
        )
        val memoryAddresses = memoryAddressesFor(activeProjectId)
        val memoryTools = KellvynMemoryTools.create(localMemory) { memoryAddresses }
        val artifactTools = KellvynArtifactTools.create(resourceRuntime.artifacts)
        // Keep lazy module adapters separate from true central tools so the inventory
        // can report REGISTERED_LAZY modules without counting them as core engines.
        val centralTools = workspaceTools + resourceTools + automationTools + memoryTools + artifactTools
        val allTools = centralTools + studioTools
        val catalogTool = KellvynInvisibleStudioCatalogTool.create(
            moduleManager = resourceRuntime.studioModules,
            coreToolSpecs = centralTools.map { it.spec }
        )
        return KellvynToolRegistry(allTools + catalogTool)
    }

    /**
     * Creates the single central KN decision loop with the local memory, planner,
     * workspace tools, resource governor, traces and network-read gate.
     *
     * Delegation is disabled inside KellvynCognitiveKernel regardless of the supplied
     * config. The caller must provide an explicitly approved inference engine.
     */
    fun createCentralCognition(
        inference: KellvynInferenceEngine,
        activeProjectId: String? = null,
        config: KellvynAgentConfig = KellvynAgentConfig()
    ): KellvynCognitiveKernel {
        val addresses = memoryAddressesFor(activeProjectId)
        return KellvynCognitiveKernel(
            inference = inference,
            tools = createProjectWorkspaceToolRegistry(activeProjectId),
            authorization = KellvynProjectWorkspaceTools.authorization(projectWriteApprovalGate),
            config = config,
            memory = localMemory,
            memoryAddresses = addresses,
            projectContextRetriever = projectContextRetriever,
            activeProjectId = activeProjectId,
            taskCheckpoints = taskCheckpointRepository,
            taskBroker = taskBroker,
            networkReadEnabled = { KellvynWebAccessGate.isEnabled(appContext) },
            traceSessionFactory = {
                KellvynAgentLabTraceSession.create(agentLabTraceRecorder)
            },
            resourceSnapshotProvider = { resourceRuntime.snapshot() }
        )
    }

    /**
     * Legacy compatibility API. The default configuration now disables delegation.
     * New application code must use createCentralCognition.
     */
    @Deprecated("Use createCentralCognition; specialist-agent orchestration is legacy.")
    suspend fun createProjectWorkspaceAgent(
        inference: KellvynInferenceEngine,
        activeProjectId: String? = null,
        config: KellvynAgentConfig = KellvynAgentConfig()
    ): KellvynAgent = createAgent(
        inference = inference,
        tools = createProjectWorkspaceToolRegistry(activeProjectId),
        activeProjectId = activeProjectId,
        authorization = KellvynProjectWorkspaceTools.authorization(projectWriteApprovalGate),
        config = config.copy(
            systemInstruction = config.systemInstruction +
                "\n\nWORKSPACE LOCAL: use workspace_list_projects, workspace_list_files e " +
                "workspace_read_file para inspecionar. workspace_write_file e workspace_delete_file " +
                "só podem ser chamadas quando o aplicativo fornecer um approval_id válido para " +
                "o mesmo projeto, caminho e conteúdo/operação. Nunca invente, altere ou reutilize " +
                "approval_id. Se não houver aprovação, pare e solicite autorização explícita."
        )
    )

    /**
     * Creates the same Kellvyn agent with local memory and optional active-project context.
     * No inference backend is created here; the caller must supply an approved engine.
     */
    suspend fun createAgent(
        inference: KellvynInferenceEngine,
        tools: KellvynToolRegistry,
        activeProjectId: String? = null,
        authorization: KellvynToolAuthorization = ReadOnlyKellvynAuthorization,
        config: KellvynAgentConfig = KellvynAgentConfig()
    ): KellvynAgent {
        val addresses = memoryAddressesFor(activeProjectId)
        val scopedTools = tools.names()
            .filter { it != "memory_search" }
            .map { name -> requireNotNull(tools.find(name)) } +
            KellvynMemoryTools.create(localMemory) { addresses }

        return KellvynAgent(
            inference = inference,
            tools = KellvynToolRegistry(scopedTools),
            authorization = KellvynNetworkReadAuthorization(
                delegate = authorization,
                networkReadEnabled = { KellvynWebAccessGate.isEnabled(appContext) }
            ),
            config = config,
            memory = localMemory,
            memoryAddresses = addresses,
            projectContextRetriever = projectContextRetriever,
            activeProjectId = activeProjectId,
            taskCheckpoints = taskCheckpointRepository,
            taskBroker = taskBroker,
            networkReadEnabled = { KellvynWebAccessGate.isEnabled(appContext) },
            specialistProfiles = activeSpecialistProfileRegistry(),
            traceSessionFactory = {
                KellvynAgentLabTraceSession.create(agentLabTraceRecorder)
            },
            resourceSnapshotProvider = { resourceRuntime.snapshot() }
        )
    }

    /**
     * Runs a sequential, independently verified specialist suite with active or experimental
     * candidate profiles and registered tools. Candidate profiles are evaluated in memory only:
     * this method never stages, approves, promotes or rolls them back. Every case is attached to one
     * encrypted local experiment trace. Executor/verifier are supplied by trusted application code.
     */
    suspend fun runAgentLabBenchmarkSuite(
        executor: KellvynSpecialistBenchmarkExecutor,
        verifier: KellvynSpecialistBenchmarkVerifier,
        cases: List<KellvynSpecialistBenchmarkCase> = KellvynSpecialistBenchmarkRegistry.default().cases(),
        activeProjectId: String? = null,
        candidateProfiles: List<KellvynSpecialistProfile>? = null
    ): KellvynSpecialistBenchmarkReport {
        val profiles = candidateProfiles ?: activeSpecialistProfiles()
        val availableTools = createProjectWorkspaceToolRegistry(activeProjectId).descriptions()
        val harness = KellvynSpecialistEvaluationHarness(
            executor = executor,
            verifier = verifier,
            traceSessionFactory = {
                KellvynAgentLabTraceSession.create(agentLabTraceRecorder)
            }
        )
        return harness.runSuite(
            cases = cases,
            profiles = profiles,
            availableTools = availableTools,
            allowNetworkRead = KellvynWebAccessGate.isEnabled(appContext)
        )
    }

    /**
     * Convenience entry point for a real local inference engine. It constructs the production
     * benchmark executor from this runtime's registered tools and shared resource broker.
     * A separately supplied verifier is still mandatory; the executor never grades itself.
     */
    suspend fun runAgentLabBenchmarkSuite(
        inference: KellvynInferenceEngine,
        verifier: KellvynSpecialistBenchmarkVerifier,
        config: KellvynAgentConfig = KellvynAgentConfig(),
        cases: List<KellvynSpecialistBenchmarkCase> = KellvynSpecialistBenchmarkRegistry.default().cases(),
        activeProjectId: String? = null,
        candidateProfiles: List<KellvynSpecialistProfile>? = null
    ): KellvynSpecialistBenchmarkReport {
        val executor = KellvynSpecialistAgentBenchmarkExecutor(
            inference = inference,
            registeredTools = createProjectWorkspaceToolRegistry(activeProjectId),
            config = config,
            taskBroker = taskBroker,
            networkReadEnabled = { KellvynWebAccessGate.isEnabled(appContext) },
            traceSessionFactory = {
                KellvynAgentLabTraceSession.create(agentLabTraceRecorder)
            },
            resourceSnapshotProvider = { resourceRuntime.snapshot() }
        )
        return runAgentLabBenchmarkSuite(
            executor = executor,
            verifier = verifier,
            cases = cases,
            activeProjectId = activeProjectId,
            candidateProfiles = candidateProfiles
        )
    }

    /** Returns whether local trace persistence is currently healthy. */
    fun agentLabTraceRecorderHealth(): KellvynAgentLabTraceRecorderHealth =
        agentLabTraceRecorder.health()

    /**
     * Explicit owner-only cleanup. The store removes only complete, terminal traces whose
     * entire authenticated hash chain validates and whose final event predates the cutoff.
     * This operation is never registered as an agent tool.
     */
    suspend fun pruneAgentLabTracesBefore(
        cutoffMillis: Long,
        ownerConfirmed: Boolean
    ): Int {
        require(ownerConfirmed) {
            "A limpeza de traces exige confirmação explícita do proprietário no código confiável."
        }
        return withContext(Dispatchers.IO) {
            agentLabTraceStore.pruneCompletedBefore(cutoffMillis)
        }
    }

    /** Returns up to 16 newest local trace summaries after validating each event chain. */
    suspend fun agentLabTraceSummaries(): List<KellvynAgentLabTraceSummary> =
        withContext(Dispatchers.IO) { agentLabTraceStore.summaries().take(16) }

    /** Returns a bounded, integrity-checked timeline for the requested local trace. */
    suspend fun agentLabTraceHistory(
        traceId: String
    ): List<KellvynAgentLabTraceRecord> =
        withContext(Dispatchers.IO) { agentLabTraceStore.historyTail(traceId, 48) }

    /** Returns the owner-approved profiles; staged drafts never affect this list. */
    suspend fun activeSpecialistProfiles(): List<KellvynSpecialistProfile> =
        withContext(Dispatchers.IO) {
            val loaded = specialistProfileStore.activeProfiles(
                KellvynSpecialistProfileRegistry.default().profiles()
            )
            activeSpecialistProfilesSnapshot = loaded
            loaded
        }

    /** A point-in-time snapshot used by optimistic concurrency checks in the hidden Studio. */
    suspend fun specialistProfileLedgerHash(): String? =
        withContext(Dispatchers.IO) { specialistProfileStore.latestRecordSha256() }

    suspend fun specialistProfileHistory(
        role: KellvynSpecialistRole
    ): List<KellvynSpecialistProfileVersionMetadata> =
        withContext(Dispatchers.IO) { specialistProfileStore.history(role) }

    suspend fun specialistProfileDiff(
        role: KellvynSpecialistRole,
        candidate: KellvynSpecialistProfile
    ): List<String> = withContext(Dispatchers.IO) {
        require(candidate.role == role) { "O papel do candidato não corresponde à revisão solicitada." }
        val active = specialistProfileStore.activeProfile(
            KellvynSpecialistProfileRegistry.default().requireProfile(role)
        )
        specialistProfileStore.diff(active, candidate)
    }

    /**
     * Saves a proposed revision without activating it. This method is an internal app API,
     * not an agent tool. The caller must first show and review the diff with the owner.
     */
    suspend fun stageSpecialistProfileRevision(
        candidate: KellvynSpecialistProfile,
        expectedActiveProfileSha256: String,
        expectedLedgerSha256: String?,
        reason: String
    ): KellvynSpecialistProfileVersionMetadata = withContext(Dispatchers.IO) {
        specialistProfileStore.stage(
            candidate = candidate,
            fallbackActiveProfile = KellvynSpecialistProfileRegistry.default()
                .requireProfile(candidate.role),
            expectedActiveProfileSha256 = expectedActiveProfileSha256,
            expectedLedgerSha256 = expectedLedgerSha256,
            actor = "agent-creation-studio",
            reason = reason,
            createdAtMillis = System.currentTimeMillis()
        )
    }

    /**
     * Activates only the exact reviewed candidate hash. ownerConfirmed must originate from
     * an explicit owner confirmation in trusted application code; the agent cannot invoke it.
     */
    suspend fun approveSpecialistProfileRevision(
        role: KellvynSpecialistRole,
        candidateVersion: Int,
        candidateSha256: String,
        expectedActiveProfileSha256: String,
        expectedLedgerSha256: String?,
        reason: String,
        ownerConfirmed: Boolean
    ): KellvynSpecialistProfileVersionMetadata = withContext(Dispatchers.IO) {
        val approved = specialistProfileStore.approve(
            role = role,
            candidateVersion = candidateVersion,
            candidateSha256 = candidateSha256,
            fallbackActiveProfile = KellvynSpecialistProfileRegistry.default().requireProfile(role),
            expectedActiveProfileSha256 = expectedActiveProfileSha256,
            expectedLedgerSha256 = expectedLedgerSha256,
            approver = "owner",
            reason = reason,
            ownerConfirmed = ownerConfirmed,
            createdAtMillis = System.currentTimeMillis()
        )
        activeSpecialistProfilesSnapshot = specialistProfileStore.activeProfiles(
            KellvynSpecialistProfileRegistry.default().profiles()
        )
        approved
    }

    /** Explicitly restores a previously approved profile revision; never deletes history. */
    suspend fun rollbackSpecialistProfileRevision(
        role: KellvynSpecialistRole,
        targetVersion: Int,
        expectedActiveProfileSha256: String,
        expectedLedgerSha256: String?,
        reason: String,
        ownerConfirmed: Boolean
    ): KellvynSpecialistProfileVersionMetadata = withContext(Dispatchers.IO) {
        val rolledBack = specialistProfileStore.rollback(
            role = role,
            targetVersion = targetVersion,
            fallbackActiveProfile = KellvynSpecialistProfileRegistry.default().requireProfile(role),
            expectedActiveProfileSha256 = expectedActiveProfileSha256,
            expectedLedgerSha256 = expectedLedgerSha256,
            approver = "owner",
            reason = reason,
            ownerConfirmed = ownerConfirmed,
            createdAtMillis = System.currentTimeMillis()
        )
        activeSpecialistProfilesSnapshot = specialistProfileStore.activeProfiles(
            KellvynSpecialistProfileRegistry.default().profiles()
        )
        rolledBack
    }

    private suspend fun activeSpecialistProfileRegistry(): KellvynSpecialistProfileRegistry =
        KellvynSpecialistProfileRegistry(activeSpecialistProfiles())

    private fun memoryAddressesFor(activeProjectId: String?): List<KellvynMemoryAddress> {
        val addresses = mutableListOf(KellvynMemoryAddress.global())
        if (!activeProjectId.isNullOrBlank()) {
            require(activeProjectId.matches(Regex("[a-z0-9][a-z0-9-]{0,60}"))) {
                "Identificador de projeto ativo inválido."
            }
            require(projectWorkspace.getProject(activeProjectId) != null) {
                "O projeto ativo não existe no workspace privado."
            }
            addresses += KellvynMemoryAddress.project(activeProjectId)
        }
        return addresses.toList()
    }

    private suspend fun <T> runHeavyTask(
        taskId: String,
        estimatedWorkingSetBytes: Long = 0L,
        operation: suspend () -> T
    ): T = taskBroker.execute(
        KellvynResourceRequest(
            taskId = taskId.take(96),
            workload = KellvynWorkloadClass.HEAVY,
            estimatedWorkingSetBytes = estimatedWorkingSetBytes
        ),
        operation
    )

    /** A tela só é liberada quando os gates aprovam exatamente este fingerprint. */
    fun isReady(): Boolean = centralKernel != null

    /** O histórico textual continua pertencendo à tela principal; não duplicamos sessões. */
    fun hasActiveConversation(): Boolean = false

    private fun initializeCentralKernel() {
        if (centralKernel != null || initializationJob?.isActive == true) return
        initializationJob = chatScope.launch {
            try {
                initializationDetail = "Validando checkpoint KN-004 local..."
                val status = withContext(Dispatchers.IO) { neuralChatRuntime.initialize() }
                if (!status.ready) {
                    neuralChatRuntime.unload()
                    initializationDetail = status.detail
                    return@launch
                }
                if (!status.qualityApprovedForBenchmark) {
                    neuralChatRuntime.unload()
                    initializationDetail = status.qualityReasons.joinToString(" ")
                        .ifBlank { "O checkpoint KN-004 não passou pelo gate numérico." }
                    return@launch
                }
                val approvedFingerprint = readinessPreferences.getString(
                    KEY_APPROVED_FINGERPRINT,
                    null
                )
                if (approvedFingerprint.isNullOrBlank() ||
                    approvedFingerprint != status.modelFingerprint
                ) {
                    neuralChatRuntime.unload()
                    initializationDetail =
                        "O checkpoint passou pela checagem numérica, mas ainda não foi aprovado " +
                        "pelo benchmark comportamental TEST deste mesmo conjunto de pesos."
                    return@launch
                }
                // Release the benchmark loader before loading the same weights into the
                // central tool-calling engine; never keep two inference copies resident.
                neuralChatRuntime.unload()
                val kernel = withContext(Dispatchers.IO) {
                    createOwnMultiBlockCognitiveKernel()
                }
                if (kernel == null) {
                    initializationDetail =
                        "Não foi possível montar o kernel central a partir do checkpoint aprovado."
                    return@launch
                }
                centralKernel = kernel
                initializationDetail = "KN-004 aprovado e carregado para a conversa principal."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                centralKernel = null
                initializationDetail = error.message?.take(500)
                    ?: "Falha ao inicializar o kernel KN-004."
            }
        }
    }

    private fun refreshCentralKernel() {
        centralKernel = null
        initializationJob?.cancel()
        initializeCentralKernel()
    }

    fun reply(prompt: String, attachmentUri: Uri?, attachmentName: String?, listener: Listener) {
        if (prompt.isBlank()) {
            listener.onError("A mensagem está vazia.")
            return
        }
        val kernel = centralKernel
        if (kernel == null) {
            initializeCentralKernel()
            listener.onStatus("Verificando o checkpoint KN-004...")
            listener.onError(initializationDetail)
            return
        }
        if (activeReplyJob?.isActive == true) {
            listener.onError("A Kellvyn já está executando esta mensagem.")
            return
        }
        activeReplyJob = chatScope.launch {
            try {
                listener.onStatus("KN-004: planejando e executando com ferramentas locais...")
                var effectivePrompt = prompt
                if (attachmentUri != null) {
                    val taskId = "chat-${System.currentTimeMillis()}"
                    val artifact = withContext(Dispatchers.IO) {
                        importUserImageArtifact(
                            uri = attachmentUri,
                            displayName = attachmentName ?: "imagem",
                            taskId = taskId
                        )
                    }
                    effectivePrompt += "\n\n[ANEXO DE IMAGEM IMPORTADO NO ARMAZENAMENTO PRIVADO: " +
                        "artifact_id=${artifact.id}; media_type=${artifact.mediaType}; " +
                        "sha256=${artifact.sha256}. Use as ferramentas de imagem registradas " +
                        "se precisar inspecionar ou transformar o artefato.]"
                }
                when (val result = withContext(Dispatchers.Default) {
                    kernel.process(effectivePrompt)
                }) {
                    is KellvynAgentResult.Completed -> listener.onComplete(
                        result.answer,
                        "KN-004 local"
                    )
                    is KellvynAgentResult.Planned -> listener.onComplete(
                        "Plano registrado e salvo com ${result.state.plan.steps.size} etapas. " +
                            "Ele ainda aguarda a aprovação explícita das etapas de execução.",
                        "KN-004 • plano salvo"
                    )
                    is KellvynAgentResult.Failed -> listener.onError(result.reason)
                    is KellvynAgentResult.LimitReached -> listener.onError(result.reason)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                listener.onError(
                    error.message?.take(800) ?: "A execução KN-004 falhou sem uma mensagem detalhada."
                )
            } finally {
                activeReplyJob = null
            }
        }
    }

    fun resetConversation() {
        activeReplyJob?.cancel()
        activeReplyJob = null
    }

    fun resetConversationAndWait() = resetConversation()

    fun close() {
        activeReplyJob?.cancel()
        chatScope.cancel()
        neuralChatRuntime.unload()
        memoryRepository.close()
    }

    private class BoundedArtifactInputStream(
        input: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                bytesRead++
                if (bytesRead > maxBytes) throw IOException("O anexo excede o limite de 64 MiB.")
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) {
                bytesRead += count.toLong()
                if (bytesRead > maxBytes) throw IOException("O anexo excede o limite de 64 MiB.")
            }
            return count
        }
    }

    private companion object {
        const val MAX_IMPORTED_IMAGE_BYTES = 64L * 1024L * 1024L
        const val KEY_APPROVED_FINGERPRINT = "kn004.behavior.fingerprint"
        const val KEY_APPROVED_TEST_COUNT = "kn004.behavior.test_count"
        const val KEY_APPROVED_EXACT_RATE = "kn004.behavior.exact_rate"
        const val KEY_APPROVED_TOKEN_F1 = "kn004.behavior.token_f1"
        const val KEY_APPROVED_AT_MILLIS = "kn004.behavior.approved_at"
    }
}
