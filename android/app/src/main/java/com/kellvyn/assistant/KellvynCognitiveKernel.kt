package com.kellvyn.assistant

/**
 * The single central decision loop for Kellvyn.
 *
 * Tools, memory, planning and verification are capabilities owned by this kernel.
 * It deliberately disables specialist delegation: the neural model remains the only
 * decision-making intelligence, while registered tools are deterministic executors.
 *
 * The legacy KellvynAgent class is retained internally during migration so existing
 * checkpoint, tool-policy and verification code remains compatible. No specialist is
 * constructed by this kernel.
 */
internal class KellvynCognitiveKernel(
    inference: KellvynInferenceEngine,
    tools: KellvynToolRegistry,
    authorization: KellvynToolAuthorization = ReadOnlyKellvynAuthorization,
    config: KellvynAgentConfig = KellvynAgentConfig(),
    memory: KellvynMemory? = null,
    memoryAddresses: List<KellvynMemoryAddress> = emptyList(),
    projectContextRetriever: KellvynProjectContextSearch? = null,
    activeProjectId: String? = null,
    skillRegistry: KellvynSkillRegistry = KellvynSkillRegistry.default(),
    taskCheckpoints: KellvynTaskCheckpointRepository? = null,
    taskBroker: KellvynTaskBroker? = null,
    networkReadEnabled: () -> Boolean = { false },
    traceSessionFactory: () -> KellvynAgentLabTraceSession = {
        KellvynAgentLabTraceSession.create()
    },
    resourceSnapshotProvider: (suspend () -> KellvynResourceSnapshot)? = null
) {
    private val centralConfig = config.copy(
        maxDelegations = 0,
        allowDelegation = false,
        systemInstruction = config.systemInstruction + "\n\n" + CENTRAL_ONLY_INSTRUCTION
    )

    private val executionLoop = KellvynAgent(
        inference = inference,
        tools = tools,
        authorization = authorization,
        config = centralConfig,
        memory = memory,
        memoryAddresses = memoryAddresses,
        projectContextRetriever = projectContextRetriever,
        activeProjectId = activeProjectId,
        skillRegistry = skillRegistry,
        taskCheckpoints = taskCheckpoints,
        taskBroker = taskBroker,
        networkReadEnabled = networkReadEnabled,
        traceSessionFactory = traceSessionFactory,
        resourceSnapshotProvider = resourceSnapshotProvider
    )

    /** Main entry point: the central intelligence owns the complete task lifecycle. */
    suspend fun process(goal: String): KellvynAgentResult = executionLoop.run(goal)

    /** Restores only a fully validated and authenticated plan checkpoint chain. */
    suspend fun restoreTaskPlan(taskId: String): KellvynTaskCheckpointSnapshot? =
        executionLoop.restoreTaskPlan(taskId)

    /** Records owner approval for one exact planned step. */
    suspend fun approveTaskStep(taskId: String, stepId: String): KellvynAgentResult =
        executionLoop.approveTaskStep(taskId, stepId)

    /** Executes one approved step and requires independent evidence before completion. */
    suspend fun executePlannedStep(
        taskId: String,
        stepId: String,
        toolName: String,
        arguments: Map<String, String>,
        verifier: KellvynIndependentVerifier,
        preconditionEvidence: KellvynPreconditionEvidence? = null
    ): KellvynAgentResult = executionLoop.executePlannedStep(
        taskId = taskId,
        stepId = stepId,
        toolName = toolName,
        arguments = arguments,
        verifier = verifier,
        preconditionEvidence = preconditionEvidence
    )

    private companion object {
        const val CENTRAL_ONLY_INSTRUCTION =
            "POLÍTICA CENTRAL OBRIGATÓRIA: você é a única inteligência que decide e raciocina. " +
            "Não solicite, crie ou delegue trabalho a agentes, especialistas ou outras IAs. " +
            "Use diretamente suas ferramentas registradas, memória, pesquisa e planejamento. " +
            "Ferramentas executam operações; não são inteligências que assumem o controle. " +
            "Quando faltar capacidade ou evidência, explique a limitação em vez de inventar uma conclusão."
    }
}
