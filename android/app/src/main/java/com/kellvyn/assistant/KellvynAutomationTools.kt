package com.kellvyn.assistant

/** Read-only queue diagnostics; scheduling/execution mutations are deliberately not model tools. */
internal object KellvynAutomationTools {
    fun create(
        pendingCountProvider: suspend () -> Int
    ): List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "automation_queue_status",
                description = "Consulta quantas tarefas locais estão aguardando execução ou liberação de recursos.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 5_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                capabilities = setOf(KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS)
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "A ferramenta não aceita argumentos.")
                }
                val pending = pendingCountProvider()
                return KellvynToolResult(
                    true,
                    "Fila local de automação: $pending tarefa(s) pendente(s). " +
                        "A consulta não inicia, cancela ou altera tarefas."
                )
            }
        }
    )
}
