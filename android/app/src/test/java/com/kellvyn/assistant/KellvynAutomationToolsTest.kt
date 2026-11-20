package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAutomationToolsTest {
    @Test
    fun queueStatusIsDiagnosticOnlyAndDoesNotClaimWorkflowControl() = runBlocking {
        val tool = KellvynAutomationTools.create { 3 }.single()

        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("3 tarefa(s) pendente(s)"))
        assertEquals(
            setOf(KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS),
            tool.spec.capabilities
        )
        assertFalse(KellvynStudioCapability.WORKFLOW_AUTOMATION in tool.spec.capabilities)
    }

    @Test
    fun queueStatusRejectsArguments() = runBlocking {
        val tool = KellvynAutomationTools.create { 0 }.single()

        val result = tool.execute(mapOf("task_id" to "unexpected"))

        assertFalse(result.success)
    }
}
