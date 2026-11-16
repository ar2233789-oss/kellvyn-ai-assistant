package com.kellvyn.assistant

import android.content.Context
import android.net.Uri
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.Dispatchers
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
    private val projectContextRetriever = KellvynProjectContextRetriever(projectWorkspace)
    private val taskCheckpointStore = KellvynTaskCheckpointStore(
        File(appContext.filesDir, "task-checkpoints")
    )

    /** Gives the agent layer access to the same app-private memory store. */
    fun memoryStore(): KellvynMemory = localMemory

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
            activeProjectId = activeProjectId
        )
    }
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
