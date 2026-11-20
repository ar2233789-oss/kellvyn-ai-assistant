package com.kellvyn.assistant

import kotlinx.coroutines.CancellationException

/** Model-facing workflow controls are restricted to one internal read-only resource probe. */
internal object KellvynAutomationTools {
    private const val MAX_DELAY_MILLIS = 7L * 24L * 60L * 60L * 1_000L

    fun create(
        pendingCountProvider: suspend () -> Int,
        scheduleResourceSnapshot: suspend (Long) -> String,
        taskStatusProvider: suspend (String) -> KellvynQueuedTask?,
        cancelQueuedTask: suspend (String) -> Boolean
    ): List<KellvynAgentTool> = listOf(
        queueStatusTool(pendingCountProvider),
        taskStatusTool(taskStatusProvider),
        scheduleTool(scheduleResourceSnapshot),
        cancelTool(cancelQueuedTask)
    )

    private fun queueStatusTool(
        pendingCountProvider: suspend () -> Int
    ) = object : KellvynAgentTool {
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
                return KellvynToolResult(false, "A consulta da fila não aceita argumentos.")
            }
            val pending = pendingCountProvider()
            return KellvynToolResult(
                true,
                "Fila local: $pending tarefa(s) pendente(s). Para agendamento há somente o probe interno de recursos."
            )
        }
    }

    private fun taskStatusTool(
        taskStatusProvider: suspend (String) -> KellvynQueuedTask?
    ) = object : KellvynAgentTool {
        override val spec = KellvynToolSpec(
            name = "automation_task_status",
            description = "Consulta estado, tentativas e resultado resumido de uma tarefa persistente sem revelar seu payload.",
            requiredArguments = setOf("task_id"),
            risk = KellvynToolRisk.READ_ONLY,
            timeoutMs = 5_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            capabilities = setOf(KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS),
            argumentSchemas = listOf(taskIdSchema())
        )

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            if (arguments.keys != setOf("task_id")) {
                return KellvynToolResult(false, "A consulta exige somente task_id.")
            }
            return try {
                val task = taskStatusProvider(arguments.getValue("task_id"))
                    ?: return KellvynToolResult(false, "Tarefa não encontrada.")
                KellvynToolResult(
                    true,
                    buildString {
                        appendLine("TAREFA AUTOMATIZADA")
                        appendLine("task_id=${task.id}")
                        appendLine("state=${task.state}")
                        appendLine("handler=${task.handlerId}")
                        appendLine("workload=${task.workloadClass}")
                        appendLine("priority=${task.priority}")
                        appendLine("attempts=${task.attempts}/${task.maxAttempts}")
                        appendLine("not_before_ms=${task.notBeforeMillis}")
                        appendLine("updated_at_ms=${task.updatedAtMillis}")
                        task.lastError?.let { appendLine("last_error=${it.take(240)}") }
                        task.resultSummary?.let { appendLine("result=${it.take(500)}") }
                        append("payload=HIDDEN")
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível consultar a tarefa: " +
                        (error.message ?: error.javaClass.simpleName).take(240)
                )
            }
        }
    }

    private fun scheduleTool(
        scheduleResourceSnapshot: suspend (Long) -> String
    ) = object : KellvynAgentTool {
        override val spec = KellvynToolSpec(
            name = "automation_schedule_resource_snapshot",
            description = "Agenda uma leitura local de memória, heap, temperatura e bateria. O handler e o payload são fixos e permitidos; o atraso é opcional e limitado a sete dias.",
            optionalArguments = setOf("delay_ms"),
            risk = KellvynToolRisk.APPEND_ONLY_TASK_CREATE,
            timeoutMs = 8_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedWorkingSetBytes = 64L * 1024L,
            capabilities = setOf(KellvynStudioCapability.WORKFLOW_AUTOMATION),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "delay_ms",
                    type = KellvynToolArgumentType.INTEGER,
                    required = false,
                    maxLength = 9,
                    minInteger = 0L,
                    maxInteger = MAX_DELAY_MILLIS
                )
            )
        )

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            if ((arguments.keys - setOf("delay_ms")).isNotEmpty()) {
                return KellvynToolResult(false, "A automação não aceita handler, script ou payload fornecido pelo modelo.")
            }
            val delay = if (arguments.containsKey("delay_ms")) {
                arguments["delay_ms"]?.toLongOrNull()
                    ?: return KellvynToolResult(false, "delay_ms precisa ser um inteiro válido.")
            } else {
                0L
            }
            if (delay !in 0L..MAX_DELAY_MILLIS) {
                return KellvynToolResult(false, "O atraso precisa ficar entre 0 e sete dias.")
            }
            return try {
                val taskId = scheduleResourceSnapshot(delay)
                KellvynToolResult(
                    true,
                    "Tarefa agendada.\ntask_id=$taskId\nhandler=resource_snapshot_probe\n" +
                        "delay_ms=$delay\npayload=FIXED_READ_ONLY_PROBE"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível agendar a tarefa: " +
                        (error.message ?: error.javaClass.simpleName).take(240)
                )
            }
        }
    }

    private fun cancelTool(
        cancelQueuedTask: suspend (String) -> Boolean
    ) = object : KellvynAgentTool {
        override val spec = KellvynToolSpec(
            name = "automation_cancel_queued_task",
            description = "Cancela uma tarefa somente se ela ainda estiver aguardando (QUEUED ou WAITING_RESOURCES). Não interrompe trabalhos já iniciados.",
            requiredArguments = setOf("task_id"),
            risk = KellvynToolRisk.CANCEL_QUEUED_TASK,
            timeoutMs = 5_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            capabilities = setOf(KellvynStudioCapability.WORKFLOW_AUTOMATION),
            argumentSchemas = listOf(taskIdSchema())
        )

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            if (arguments.keys != setOf("task_id")) {
                return KellvynToolResult(false, "O cancelamento exige somente task_id.")
            }
            return try {
                val taskId = arguments.getValue("task_id")
                val task = taskStatusProvider(taskId)
                    ?: return KellvynToolResult(false, "Tarefa não encontrada.")
                if (task.handlerId != KellvynResourceSnapshotTaskHandler.HANDLER_ID) {
                    return KellvynToolResult(false, "Esta ferramenta só controla probes internos de recursos.")
                }
                if (task.state !in setOf(
                        KellvynQueuedTaskState.QUEUED,
                        KellvynQueuedTaskState.WAITING_RESOURCES
                    )
                ) {
                    return KellvynToolResult(false, "A tarefa já saiu da fila; nenhuma execução foi interrompida.")
                }
                val cancelled = cancelQueuedTask(taskId)
                KellvynToolResult(
                    cancelled,
                    if (cancelled) "Tarefa cancelada antes do início."
                    else "A tarefa deixou a fila antes do cancelamento."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível cancelar a tarefa: " +
                        (error.message ?: error.javaClass.simpleName).take(240)
                )
            }
        }
    }

    private fun taskIdSchema() = KellvynToolArgumentSchema(
        name = "task_id",
        type = KellvynToolArgumentType.TEXT,
        maxLength = 96,
        pattern = "^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$"
    )
}
