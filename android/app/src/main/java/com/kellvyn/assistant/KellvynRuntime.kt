package com.kellvyn.assistant

import android.content.Context
import android.net.Uri
import com.kellvyn.assistant.neural.KellvynNeuralModelStore
import com.kellvyn.assistant.neural.KellvynTrainingConfig
import com.kellvyn.assistant.neural.KellvynTrainingDocument
import com.kellvyn.assistant.neural.KellvynInstructionTrainingExample
import com.kellvyn.assistant.neural.KellvynTrainingManifest
import com.kellvyn.assistant.neural.KellvynTrainingPipeline
import com.kellvyn.assistant.neural.KellvynRestoredNeuralBundle
import com.kellvyn.assistant.neural.KellvynTransformerFineTuneConfig
import com.kellvyn.assistant.neural.KellvynTransformerTrainingConfig
import com.kellvyn.assistant.neural.KellvynTransformerTrainingManifest
import com.kellvyn.assistant.neural.KellvynTransformerTrainingPipeline
import com.kellvyn.assistant.neural.KellvynRestoredTransformerBundle
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

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
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynAgentCreationStudio.definition(
                profiles = KellvynSpecialistProfileRegistry.default(),
                profileStore = specialistProfileStore
            )
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynAgentLabStudio.definition(
                traceStore = agentLabTraceStore,
                moduleManager = resourceRuntime.studioModules,
                resourceSnapshotProvider = resourceRuntime::snapshot,
                traceRecorderHealthProvider = agentLabTraceRecorder::health,
                networkReadEnabled = { KellvynWebAccessGate.isEnabled(appContext) },
                specialistProfilesProvider = { activeSpecialistProfiles() }
            )
        )
        resourceRuntime.studioModules.registerIfAbsent(
            KellvynBotOperationsStudio.definition(appContext) { isReady() }
        )
    }
    private val neuralModelStore = KellvynNeuralModelStore(
        File(appContext.filesDir, "neural")
    )
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

    /** Gives the agent layer access to the same app-private memory store. */
    fun memoryStore(): KellvynMemory = localMemory

    /** A fresh resource snapshot for the operations specialist and diagnostics. */
    suspend fun resourceSnapshot(): KellvynResourceSnapshot = resourceRuntime.snapshot()

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
     * Starts a fresh KN-003 run from this project's random initialization using authorized
     * instruction examples. This intentionally does not continue an existing bundle.
     * To preserve pretrained weights, call fineTuneOwnTransformerModel instead.
     *
     * This operation does not connect the model to chat or replace the KN-002 experiment.
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
     * Pretrains KN-003 on authorized local documents using next-token prediction.
     * The document pipeline keeps validation/test held out and persists the same
     * tokenizer/Transformer bundle format used by supervised instruction training.
     */
    suspend fun pretrainOwnTransformerFromDocuments(
        documents: List<KellvynTrainingDocument>,
        config: KellvynTransformerTrainingConfig = KellvynTransformerTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): KellvynTransformerTrainingManifest {
        val trained = runHeavyTask("pretraining-transformer-${createdAtMillis}") {
            withContext(Dispatchers.Default) {
                val trainingContext = currentCoroutineContext()
                KellvynTransformerTrainingPipeline.pretrainDocuments(
                    documents = documents,
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
     * Fine-tunes an existing pretrained KN-003 bundle with supervised examples.
     * The pipeline clones the current checkpoint and the stored bundle is replaced only
     * after training and held-out evaluation complete successfully.
     */
    suspend fun fineTuneOwnTransformerModel(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynTransformerFineTuneConfig = KellvynTransformerFineTuneConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): KellvynTransformerTrainingManifest {
        val base = loadOwnTransformerModel()
            ?: throw IllegalStateException("Não há um bundle KN-003 pré-treinado para ajuste fino.")
        val trained = runHeavyTask("fine-tuning-transformer-${createdAtMillis}") {
            withContext(Dispatchers.Default) {
                val trainingContext = currentCoroutineContext()
                KellvynTransformerTrainingPipeline.fineTuneInstructions(
                    base = base,
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
        val automationTools = KellvynAutomationTools.create {
            resourceRuntime.pendingScheduledTaskCount()
        }
        val registeredCapabilities = resourceRuntime.studioModules.manifests()
            .flatMap { it.capabilities }
            .toSet()
        val studioTools = resourceRuntime.studioModules.toolsForCapabilities(
            registeredCapabilities
        )
        val memoryAddresses = memoryAddressesFor(activeProjectId)
        val memoryTools = KellvynMemoryTools.create(localMemory) { memoryAddresses }
        return KellvynToolRegistry(
            workspaceTools + resourceTools + automationTools + studioTools + memoryTools
        )
    }

    /**
     * Creates the coordinator with local project tools. Read-only specialists inherit only
     * the read subset; mutations require an exact short-lived approval issued by the owner.
     */
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
        operation: suspend () -> T
    ): T = taskBroker.execute(
        KellvynResourceRequest(taskId = taskId.take(96), workload = KellvynWorkloadClass.HEAVY),
        operation
    )

    fun isReady(): Boolean = false

    fun hasActiveConversation(): Boolean = false

    fun reply(prompt: String, attachmentUri: Uri?, attachmentName: String?, listener: Listener) {
        listener.onStatus("NÚCLEO NEURAL KELLVYN EM CONSTRUÇÃO")
        listener.onError(
            "O cérebro neural próprio da Kellvyn ainda não está conectado. " +
                "Nenhum modelo de terceiros será baixado ou usado como substituto."
        )
    }

    fun resetConversation() = Unit

    fun resetConversationAndWait() = Unit

    fun close() {
        memoryRepository.close()
    }
}
