package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynTaskPlannerResourceRetryTest {
    @Test
    fun deferredStepReturnsToReadyWithoutLosingOwnerApprovalOrAttemptHistory() {
        val planner = KellvynTaskPlanner()
        val plan = KellvynTaskPlan(
            id = "resource-retry-001",
            goal = "Executar uma etapa depois que houver recursos disponíveis.",
            constraints = listOf("Não perder a aprovação já concedida."),
            steps = listOf(
                KellvynTaskStep(
                    id = "build",
                    title = "Compilar",
                    objective = "Executar a compilação autorizada.",
                    requiredTools = setOf("compile_project"),
                    risk = KellvynStepRisk.REVERSIBLE_WRITE,
                    ownerApprovalRequired = true,
                    preconditions = listOf("workspace_valid"),
                    successCriteria = listOf("APK_VALID"),
                    recoveryPlan = "Preservar os arquivos e tentar novamente."
                )
            ),
            createdAtMillis = 1_800_000_000_000L
        )
        val waiting = planner.createState(
            plan,
            registeredToolRisks = mapOf("compile_project" to KellvynStepRisk.REVERSIBLE_WRITE)
        )
        assertEquals(KellvynStepStatus.AWAITING_APPROVAL, waiting.statuses["build"])

        val approved = planner.approveStep(waiting, "build")
        assertEquals(KellvynStepStatus.READY, approved.statuses["build"])

        val running = planner.startStep(
            approved,
            "build",
            KellvynPreconditionEvidence(
                checkedBy = "workspace-validator",
                verifiedConditions = setOf("workspace_valid"),
                summary = "Workspace íntegro.",
                evidenceSha256 = "a".repeat(64),
                observedAtMillis = 1_800_000_000_100L
            )
        )
        assertEquals(KellvynStepStatus.RUNNING, running.statuses["build"])
        assertEquals(1, running.attempts["build"])
        assertTrue(running.preconditionProofs.containsKey("build"))

        val readyAgain = planner.returnStepToReady(running, "build")

        assertEquals(KellvynStepStatus.READY, readyAgain.statuses["build"])
        assertTrue("A aprovação do proprietário deve sobreviver ao adiamento.", "build" in readyAgain.approvedStepIds)
        assertEquals(1, readyAgain.attempts["build"])
        assertFalse("A evidência de pré-condição precisa ser verificada novamente.", readyAgain.preconditionProofs.containsKey("build"))
        assertTrue(readyAgain.failureReasons.isEmpty())
    }
}
