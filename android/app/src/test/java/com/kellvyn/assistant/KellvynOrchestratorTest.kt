package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynOrchestratorTest {

    @Test
    fun dispatchesAProfileBoundSpecialistWithIsolatedContextAndOnlyPermittedTools() = runBlocking {
        val engine = RecordingEngine(KellvynModelDecision.Final("O símbolo está em SourceIndex.find()."))
        val readTool = RecordingTool(
            KellvynToolSpec(
                name = "read_source",
                description = "Lê um arquivo",
                requiredArguments = setOf("path"),
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            )
        )
        val deleteTool = RecordingTool(
            KellvynToolSpec(
                name = "delete_source",
                description = "Remove um arquivo",
                risk = KellvynToolRisk.DESTRUCTIVE,
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            )
        )
        val orchestrator = orchestrator(engine, listOf(readTool, deleteTool))

        val outcome = orchestrator.dispatch(
            decision = KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.CODE_EXPLORER,
                task = "Encontre a função que indexa símbolos.",
                context = "CONTEXTO_DELEGADO_CONTROLADO",
                allowedTools = setOf("read_source", "delete_source")
            ),
            completedDelegations = 0
        )

        assertTrue(outcome.consumesDelegationSlot)
        assertEquals("specialist:code_explorer", outcome.source)
        assertTrue(outcome.content.contains("PROFILE_SHA256:"))
        assertTrue(outcome.content.contains("O símbolo está em SourceIndex.find()."))
        assertEquals(1, engine.histories.size)
        val specialistPrompt = engine.histories.single().joinToString("\n") { it.content }
        assertTrue(specialistPrompt.contains("Explorador de código"))
        assertTrue(specialistPrompt.contains("Encontre a função que indexa símbolos."))
        assertTrue(specialistPrompt.contains("CONTEXTO_DELEGADO_CONTROLADO"))
        assertFalse(specialistPrompt.contains("PRIVATE_COORDINATOR_CONTEXT"))
        assertEquals(listOf("read_source"), engine.toolsSeen.single().map { it.name })
        assertEquals(0, readTool.executions)
        assertEquals(0, deleteTool.executions)
    }

    @Test
    fun refusesDelegationOutsideBudgetWithoutCallingTheModel() = runBlocking {
        val engine = RecordingEngine()
        val orchestrator = orchestrator(engine, emptyList(), maxDelegations = 1)

        val outcome = orchestrator.dispatch(
            decision = KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.RESEARCHER,
                task = "Pesquise a documentação.",
                context = "",
                allowedTools = emptySet()
            ),
            completedDelegations = 1
        )

        assertFalse(outcome.consumesDelegationSlot)
        assertTrue(outcome.content.contains("limite de delegações"))
        assertTrue(engine.histories.isEmpty())
    }

    @Test
    fun refusesOversizedDelegatedContextBeforeCreatingSpecialist() = runBlocking {
        val engine = RecordingEngine()
        val orchestrator = orchestrator(
            engine,
            emptyList(),
            maxDelegationContextChars = 4
        )

        val outcome = orchestrator.dispatch(
            decision = KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.RESEARCHER,
                task = "Pesquise.",
                context = "contexto maior",
                allowedTools = emptySet()
            ),
            completedDelegations = 0
        )

        assertFalse(outcome.consumesDelegationSlot)
        assertTrue(outcome.content.contains("excedeu os limites"))
        assertTrue(engine.histories.isEmpty())
    }

    private fun orchestrator(
        engine: RecordingEngine,
        tools: List<KellvynAgentTool>,
        maxDelegations: Int = 2,
        maxDelegationContextChars: Int = 4_000
    ) = KellvynOrchestrator(
        inference = engine,
        tools = KellvynToolRegistry(tools),
        config = KellvynAgentConfig(
            maxDelegations = maxDelegations,
            maxDelegationContextChars = maxDelegationContextChars
        ),
        skillRegistry = KellvynSkillRegistry.empty(),
        taskBroker = null,
        networkReadEnabled = { false },
        specialistProfiles = KellvynSpecialistProfileRegistry.default()
    )

    private class RecordingEngine(
        vararg decisions: KellvynModelDecision
    ) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        val histories = mutableListOf<List<KellvynAgentMessage>>()
        val toolsSeen = mutableListOf<List<KellvynToolSpec>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            histories += history.toList()
            toolsSeen += availableTools.toList()
            check(pending.isNotEmpty()) { "O teste pediu uma decisão extra ao modelo." }
            return pending.removeFirst()
        }
    }

    private class RecordingTool(
        override val spec: KellvynToolSpec
    ) : KellvynAgentTool {
        var executions = 0

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            executions++
            return KellvynToolResult(true, "resultado de leitura")
        }
    }
}
