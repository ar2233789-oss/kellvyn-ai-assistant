package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynTaskPlannerTest {
    private val planner = KellvynTaskPlanner()

    @Test
    fun dependentStepsOnlyBecomeReadyAfterVerifiedPrerequisites() {
        val plan = plan(
            readStep(),
            writeStep(dependencies = setOf("read"))
        )
        var state = planner.createState(plan)

        assertEquals(KellvynStepStatus.READY, state.statuses["read"])
        assertEquals(KellvynStepStatus.WAITING, state.statuses["write"])

        state = planner.startStep(state, "read")
        state = planner.completeStep(state, "read", outcome(), validVerifier())

        assertEquals(KellvynStepStatus.SUCCEEDED, state.statuses["read"])
        assertEquals(KellvynStepStatus.AWAITING_APPROVAL, state.statuses["write"])

        state = planner.approveStep(state, "write")
        assertEquals(KellvynStepStatus.READY, state.statuses["write"])
    }

    @Test
    fun planWithMissingDependencyIsRejected() {
        val errors = planner.validate(plan(
            readStep(dependencies = setOf("missing"))
        ))

        assertTrue(errors.any { it.contains("Dependências inexistentes") })
    }

    @Test
    fun cyclicDependenciesAreRejected() {
        val errors = planner.validate(plan(
            readStep(id = "a", dependencies = setOf("b")),
            readStep(id = "b", dependencies = setOf("a"))
        ))

        assertTrue(errors.any { it.contains("ciclo") })
    }

    @Test
    fun planCannotDowngradeRiskOfRegisteredTool() {
        val step = readStep(requiredTools = setOf("delete_file"))
        val errors = planner.validate(
            plan(step),
            mapOf("delete_file" to KellvynStepRisk.DESTRUCTIVE)
        )

        assertTrue(errors.any { it.contains("risco declarado é inferior") })
    }

    @Test
    fun unregisteredToolCannotEnterExecutablePlan() {
        val errors = planner.validate(plan(readStep(requiredTools = setOf("unknown_tool"))))

        assertTrue(errors.any { it.contains("Ferramentas não registradas") })
    }

    @Test
    fun mutatingStepCannotDisableOwnerApproval() {
        val unsafe = writeStep(ownerApprovalRequired = false)
        val errors = planner.validate(plan(unsafe))

        assertTrue(errors.any { it.contains("aprovação explícita") })
    }

    @Test
    fun dependentStepCannotStartBeforePrerequisite() {
        val state = planner.createState(plan(
            readStep(),
            writeStep(dependencies = setOf("read"))
        ))

        try {
            planner.startStep(state, "write")
            throw AssertionError("A etapa dependente foi iniciada antes da hora.")
        } catch (_: IllegalStateException) {
            assertEquals(KellvynStepStatus.WAITING, state.statuses["write"])
        }
    }

    @Test
    fun modelAssertionAloneCannotCompleteStepWithoutAllVerifiedCriteria() {
        var state = planner.createState(plan(readStep()))
        state = planner.startStep(state, "read")
        val incompleteVerifier = KellvynIndependentVerifier { step, _ ->
            KellvynStepProof(
                verifiedBy = "test-harness",
                verifiedCriteria = emptySet(),
                summary = "O modelo afirmou que concluiu.",
                evidenceSha256 = "a".repeat(64),
                observedAtMillis = 1L
            )
        }

        try {
            planner.completeStep(state, "read", outcome(), incompleteVerifier)
            throw AssertionError("A etapa foi aprovada sem evidência dos critérios.")
        } catch (_: IllegalArgumentException) {
            assertEquals(KellvynStepStatus.RUNNING, state.statuses["read"])
        }
    }

    @Test
    fun failedPrerequisiteBlocksDependentWork() {
        var state = planner.createState(plan(
            readStep(),
            writeStep(dependencies = setOf("read"))
        ))
        state = planner.startStep(state, "read")
        state = planner.failStep(state, "read", "O arquivo esperado não foi encontrado.")

        assertEquals(KellvynStepStatus.FAILED, state.statuses["read"])
        assertEquals(KellvynStepStatus.BLOCKED, state.statuses["write"])
    }

    @Test
    fun cancellationStopsPendingAndRunningStepsButKeepsCompletedEvidence() {
        var state = planner.createState(plan(readStep(), readStep(id = "other")))
        state = planner.startStep(state, "read")
        state = planner.completeStep(state, "read", outcome(), validVerifier())
        state = planner.cancel(state)

        assertEquals(KellvynStepStatus.SUCCEEDED, state.statuses["read"])
        assertEquals(KellvynStepStatus.CANCELLED, state.statuses["other"])
        assertTrue(planner.isFinished(state))
        assertFalse(state.proofs["read"] == null)
    }

    private fun readStep(
        id: String = "read",
        dependencies: Set<String> = emptySet(),
        requiredTools: Set<String> = emptySet()
    ) = KellvynTaskStep(
        id = id,
        title = "Inspecionar",
        objective = "Ler o recurso delimitado.",
        dependencies = dependencies,
        requiredTools = requiredTools,
        successCriteria = listOf("O conteúdo foi lido", "A origem foi registrada")
    )

    private fun writeStep(
        dependencies: Set<String> = emptySet(),
        ownerApprovalRequired: Boolean = true
    ) = KellvynTaskStep(
        id = "write",
        title = "Alterar",
        objective = "Aplicar uma alteração delimitada.",
        dependencies = dependencies,
        risk = KellvynStepRisk.REVERSIBLE_WRITE,
        ownerApprovalRequired = ownerApprovalRequired,
        successCriteria = listOf("O diff foi aplicado", "Os testes passaram"),
        recoveryPlan = "Reverter o diff pelo checkpoint da tarefa."
    )

    private fun plan(vararg steps: KellvynTaskStep) = KellvynTaskPlan(
        id = "task-001",
        goal = "Executar a tarefa de laboratório.",
        constraints = listOf("Não alterar a interface protegida."),
        steps = steps.toList(),
        createdAtMillis = 1L
    )

    private fun outcome() = KellvynTaskOutcome(
        summary = "Resultado observado no executor de teste.",
        exitCode = 0,
        artifactRefs = setOf("artifact:test"),
        observedAtMillis = 2L
    )

    private fun validVerifier() = KellvynIndependentVerifier { step, _ ->
        KellvynStepProof(
            verifiedBy = "test-harness",
            verifiedCriteria = step.successCriteria.toSet(),
            summary = "Critérios comprovados pelo executor de teste.",
            evidenceSha256 = "b".repeat(64),
            observedAtMillis = 3L
        )
    }
}
