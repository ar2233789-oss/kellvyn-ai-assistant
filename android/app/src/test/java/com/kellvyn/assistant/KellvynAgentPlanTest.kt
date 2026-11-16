package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAgentPlanTest {
    @Test
    fun validPlanIsPersistedBeforeAnyToolCanExecute() = runBlocking {
        val repository = FakeCheckpointRepository()
        val engine = PlanFakeEngine(KellvynModelDecision.Plan(plan()))
        val tool = FakePlanTool(
            KellvynToolSpec("read_note", "Lê uma nota"),
            KellvynToolResult(true, "conteúdo")
        )
        val agent = agent(engine, repository, listOf(tool))

        val result = agent.run("Inspecione e depois altere o projeto.")

        assertTrue(result is KellvynAgentResult.Planned)
        val planned = result as KellvynAgentResult.Planned
        assertEquals("agent-plan-001", planned.state.plan.id)
        assertNotNull(repository.latest)
        assertEquals(1, repository.appendCount)
        assertEquals(0, tool.executions)
    }

    @Test
    fun planWithUnregisteredToolIsRejectedAndNeverPersisted() = runBlocking {
        val repository = FakeCheckpointRepository()
        val invalid = plan(requiredTool = "run_arbitrary_shell")
        val engine = PlanFakeEngine(
            KellvynModelDecision.Plan(invalid),
            KellvynModelDecision.Final("O plano inválido foi recusado.")
        )
        val result = agent(engine, repository, emptyList())
            .run("Execute uma tarefa com segurança.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(0, repository.appendCount)
        assertTrue(engine.histories.last().any {
            it.content.contains("Plano recusado pelo validador")
        })
    }

    @Test
    fun mutatingPlanWaitsForExplicitApprovalAndStoresNewCheckpoint() = runBlocking {
        val repository = FakeCheckpointRepository()
        val engine = PlanFakeEngine(KellvynModelDecision.Plan(plan(
            requiredTool = "write_note",
            risk = KellvynStepRisk.REVERSIBLE_WRITE
        )))
        val writeTool = FakePlanTool(
            KellvynToolSpec(
                name = "write_note",
                description = "Grava uma nota",
                risk = KellvynToolRisk.REVERSIBLE_WRITE
            ),
            KellvynToolResult(true, "gravado")
        )
        val agent = agent(engine, repository, listOf(writeTool))

        val first = agent.run("Grave a alteração.")

        assertTrue(first is KellvynAgentResult.Planned)
        val waiting = first as KellvynAgentResult.Planned
        assertEquals(KellvynStepStatus.AWAITING_APPROVAL, waiting.state.statuses["work"])
        assertEquals(0, writeTool.executions)

        val approved = agent.approveTaskStep("agent-plan-001", "work")

        assertTrue(approved is KellvynAgentResult.Planned)
        assertEquals(
            KellvynStepStatus.READY,
            (approved as KellvynAgentResult.Planned).state.statuses["work"]
        )
        assertEquals(2, repository.appendCount)
        assertFalse(repository.latest!!.metadata.sha256 == waiting.checkpoint.sha256)
    }

    @Test
    fun planCannotBeAcceptedWithoutDurableCheckpointRepository() = runBlocking {
        val engine = PlanFakeEngine(KellvynModelDecision.Plan(plan()))
        val result = KellvynAgent(
            inference = engine,
            tools = KellvynToolRegistry(emptyList()),
            taskCheckpoints = null
        ).run("Planeje a tarefa.")

        assertTrue(result is KellvynAgentResult.Failed)
        assertTrue((result as KellvynAgentResult.Failed).reason.contains("armazenamento persistente"))
    }

    private fun agent(
        engine: PlanFakeEngine,
        repository: FakeCheckpointRepository,
        tools: List<KellvynAgentTool>
    ) = KellvynAgent(
        inference = engine,
        tools = KellvynToolRegistry(tools),
        taskCheckpoints = repository,
        skillRegistry = KellvynSkillRegistry.empty()
    )

    private fun plan(
        requiredTool: String = "read_note",
        risk: KellvynStepRisk = KellvynStepRisk.READ_ONLY
    ) = KellvynTaskPlan(
        id = "agent-plan-001",
        goal = "Inspecionar o projeto e executar a etapa autorizada.",
        constraints = listOf("Não tocar na interface protegida."),
        steps = listOf(
            KellvynTaskStep(
                id = "work",
                title = "Executar etapa",
                objective = "Executar somente a ferramenta registrada.",
                requiredTools = setOf(requiredTool),
                risk = risk,
                ownerApprovalRequired = risk != KellvynStepRisk.READ_ONLY,
                successCriteria = listOf("A operação foi observada."),
                recoveryPlan = if (risk == KellvynStepRisk.READ_ONLY) null else "Restaurar o checkpoint anterior."
            )
        ),
        createdAtMillis = 1_800_000_000_000L
    )

    private class FakeCheckpointRepository : KellvynTaskCheckpointRepository {
        var latest: KellvynTaskCheckpointSnapshot? = null
            private set
        var appendCount = 0
            private set

        override suspend fun append(
            state: KellvynTaskState,
            createdAtMillis: Long,
            expectedPreviousSha256: String?
        ): KellvynTaskCheckpointMetadata {
            val previous = latest?.metadata?.sha256
            check(previous == expectedPreviousSha256)
            appendCount++
            val digest = appendCount.toString(16).padStart(64, '0')
            val metadata = KellvynTaskCheckpointMetadata(
                taskId = state.plan.id,
                sequence = appendCount.toLong(),
                createdAtMillis = createdAtMillis,
                previousSha256 = previous ?: "0".repeat(64),
                sha256 = digest
            )
            latest = KellvynTaskCheckpointSnapshot(metadata, state)
            return metadata
        }

        override suspend fun loadLatest(taskId: String): KellvynTaskCheckpointSnapshot? =
            latest?.takeIf { it.metadata.taskId == taskId }
    }

    private class PlanFakeEngine(vararg decisions: KellvynModelDecision) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        val histories = mutableListOf<List<KellvynAgentMessage>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            histories += history
            return pending.removeFirst()
        }
    }

    private class FakePlanTool(
        override val spec: KellvynToolSpec,
        private val result: KellvynToolResult
    ) : KellvynAgentTool {
        var executions = 0
        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            executions++
            return result
        }
    }
}
