package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAutomationToolsTest {
    @Test
    fun queueStatusIsDiagnosticOnly() = runBlocking {
        val tools = createTools()
        val tool = tools.single { it.spec.name == "automation_queue_status" }

        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("3 tarefa(s) pendente(s)"))
        assertEquals(
            setOf(KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS),
            tool.spec.capabilities
        )
        assertEquals(KellvynToolRisk.READ_ONLY, tool.spec.risk)
    }

    @Test
    fun scheduleUsesOnlyTheFixedResourceProbeAndBoundsDelay() = runBlocking {
        var scheduledDelay = -1L
        val tools = createTools(schedule = { delay ->
            scheduledDelay = delay
            "scheduled-task-1"
        })
        val tool = tools.single { it.spec.name == "automation_schedule_resource_snapshot" }

        val result = tool.execute(mapOf("delay_ms" to "60000"))

        assertTrue(result.success)
        assertEquals(60_000L, scheduledDelay)
        assertTrue(result.content.contains("task_id=scheduled-task-1"))
        assertTrue(result.content.contains("handler=resource_snapshot_probe"))
        assertEquals(KellvynToolRisk.APPEND_ONLY_TASK_CREATE, tool.spec.risk)
        assertEquals(
            setOf(KellvynStudioCapability.WORKFLOW_AUTOMATION),
            tool.spec.capabilities
        )
    }

    @Test
    fun scheduleRejectsModelSuppliedHandlersScriptsAndOutOfRangeDelay() = runBlocking {
        val tools = createTools()
        val tool = tools.single { it.spec.name == "automation_schedule_resource_snapshot" }

        val handlerInjection = tool.execute(mapOf("handler_id" to "arbitrary"))
        val excessiveDelay = tool.execute(mapOf("delay_ms" to "604800001"))

        assertFalse(handlerInjection.success)
        assertFalse(excessiveDelay.success)
    }

    @Test
    fun taskStatusDoesNotExposePersistedPayload() = runBlocking {
        val tools = createTools()
        val tool = tools.single { it.spec.name == "automation_task_status" }

        val result = tool.execute(mapOf("task_id" to "queued-task-1"))

        assertTrue(result.success)
        assertTrue(result.content.contains("state=QUEUED"))
        assertTrue(result.content.contains("payload=HIDDEN"))
        assertFalse(result.content.contains("internal-secret-payload"))
        assertEquals(KellvynToolRisk.READ_ONLY, tool.spec.risk)
    }

    @Test
    fun cancellationIsLimitedToTheQueueAndDoesNotClaimRunningWorkWasStopped() = runBlocking {
        var cancelledId = ""
        val tools = createTools(cancel = { id ->
            cancelledId = id
            false
        })
        val tool = tools.single { it.spec.name == "automation_cancel_queued_task" }

        val result = tool.execute(mapOf("task_id" to "running-task-2"))

        assertFalse(result.success)
        assertEquals("running-task-2", cancelledId)
        assertTrue(result.content.contains("nenhum trabalho em execução foi interrompido"))
        assertEquals(KellvynToolRisk.CANCEL_QUEUED_TASK, tool.spec.risk)
    }

    @Test
    fun toolsRejectUnexpectedArguments() = runBlocking {
        val tools = createTools()
        val status = tools.single { it.spec.name == "automation_queue_status" }
            .execute(mapOf("task_id" to "unexpected"))
        val taskStatus = tools.single { it.spec.name == "automation_task_status" }
            .execute(mapOf("task_id" to "task-1", "payload" to "unexpected"))

        assertFalse(status.success)
        assertFalse(taskStatus.success)
    }

    private fun createTools(
        schedule: suspend (Long) -> String = { "scheduled-task-1" },
        cancel: suspend (String) -> Boolean = { true }
    ): List<KellvynAgentTool> = KellvynAutomationTools.create(
        pendingCountProvider = { 3 },
        scheduleResourceSnapshot = schedule,
        taskStatusProvider = { id -> if (id == "queued-task-1") queuedTask() else null },
        cancelQueuedTask = cancel
    )

    private fun queuedTask() = KellvynQueuedTask(
        id = "queued-task-1",
        handlerId = "resource_snapshot_probe",
        payload = "internal-secret-payload",
        priority = 60,
        workloadClass = KellvynWorkloadClass.LIGHT,
        estimatedWorkingSetBytes = 16L * 1024L * 1024L,
        createdAtMillis = 1_800_000_000_000L,
        notBeforeMillis = 0L,
        attempts = 0,
        maxAttempts = 2,
        state = KellvynQueuedTaskState.QUEUED,
        lastError = null,
        resultSummary = null,
        updatedAtMillis = 1_800_000_000_000L
    )
}
