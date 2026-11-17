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
    private val resourceGovernor = KellvynResourceGovernor()
    private val resourceMonitor = KellvynAndroidResourceMonitor(appContext)
    private val taskBroker = KellvynTaskBroker(resourceGovernor) {
        withContext(Dispatchers.IO) { resourceMonitor.snapshot() }
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
    suspend fun resourceSnapshot(): KellvynResourceSnapshot =
        withContext(Dispatchers.IO) { resourceMonitor.snapshot() }

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
            maxGenerationTokens = minOf(64, (contextLength / 4).coerceAtLeast(16))
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
    fun createProjectWorkspaceToolRegistry(): KellvynToolRegistry =
        KellvynToolRegistry(
            KellvynProjectWorkspaceTools.create(projectWorkspace, projectWriteApprovalGate)
        )

    /**
     * Creates the coordinator with local project tools. Read-only specialists inherit only
     * the read subset; mutations require an exact short-lived approval issued by the owner.
     */
    fun createProjectWorkspaceAgent(
        inference: KellvynInferenceEngine,
        activeProjectId: String? = null,
        config: KellvynAgentConfig = KellvynAgentConfig()
    ): KellvynAgent = createAgent(
        inference = inference,
        tools = createProjectWorkspaceToolRegistry(),
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
    fun createAgent(
        inference: KellvynInferenceEngine,
        tools: KellvynToolRegistry,
        activeProjectId: String? = null,
        authorization: KellvynToolAuthorization = ReadOnlyKellvynAuthorization,
        config: KellvynAgentConfig = KellvynAgentConfig()
    ): KellvynAgent {
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
        return KellvynAgent(
            inference = inference,
            tools = tools,
            authorization = authorization,
            config = config,
            memory = localMemory,
            memoryAddresses = addresses,
            projectContextRetriever = projectContextRetriever,
            activeProjectId = activeProjectId,
            taskCheckpoints = taskCheckpointRepository,
            taskBroker = taskBroker
        )
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
