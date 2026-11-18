package com.kellvyn.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynOrchestratorTest {

    @Test
    fun recordsMonotonicDurationForCompletedSpecialistDelegation() = runBlocking {
        val engine = RecordingEngine(KellvynModelDecision.Final("Especialista concluiu."))
        val recorder = RecordingTraceRecorder()
        var clockCalls = 0
        val session = orchestrator(
            engine = engine,
            tools = emptyList(),
            monotonicNanos = {
                if (clockCalls++ == 0) 100_000_000L else 108_500_000L
            }
        ).newSession(
            KellvynAgentLabTraceSession.create(
                recorder = recorder,
                clockMillis = { 1_800_000_000_000L }
            )
        )

        session.dispatch(
            KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.RESEARCHER,
                task = "Resuma a fonte.",
                context = "",
                allowedTools = emptySet()
            )
        )

        val finished = recorder.events.single {
            it.kind == KellvynAgentLabEventKind.DELEGATION_FINISHED
        }
        assertEquals(8L, finished.durationMillis)
    }

    @Test
    fun recordsMonotonicDurationWhenSpecialistIsCancelledAndPropagatesCancellation() = runBlocking {
        val engine = object : KellvynInferenceEngine {
            override suspend fun next(
                history: List<KellvynAgentMessage>,
                availableTools: List<KellvynToolSpec>
            ): KellvynModelDecision {
                throw CancellationException("cancelled by test")
            }
        }
        val recorder = RecordingTraceRecorder()
        var clockCalls = 0
        val session = orchestrator(
            engine = engine,
            tools = emptyList(),
            monotonicNanos = {
                if (clockCalls++ == 0) 200_000_000L else 205_000_000L
            }
        ).newSession(
            KellvynAgentLabTraceSession.create(
                recorder = recorder,
                clockMillis = { 1_800_000_000_000L }
            )
        )

        var propagated: CancellationException? = null
        try {
            session.dispatch(
                KellvynModelDecision.Delegate(
                    role = KellvynSpecialistRole.RESEARCHER,
                    task = "Analise a fonte.",
                    context = "",
                    allowedTools = emptySet()
                )
            )
        } catch (cancelled: CancellationException) {
            propagated = cancelled
        }

        assertTrue(propagated != null)
        val finished = recorder.events.single {
            it.kind == KellvynAgentLabEventKind.DELEGATION_FINISHED
        }
        assertEquals(5L, finished.durationMillis)
        assertEquals("CANCELLED", finished.detailCode)
    }

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
        val session = orchestrator(engine, listOf(readTool, deleteTool)).newSession()

        val outcome = session.dispatch(
            decision = KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.CODE_EXPLORER,
                task = "Encontre a função que indexa símbolos.",
                context = "CONTEXTO_DELEGADO_CONTROLADO",
                allowedTools = setOf("read_source", "delete_source")
            )
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
        val engine = RecordingEngine(KellvynModelDecision.Final("Pesquisa concluída."))
        val session = orchestrator(engine, emptyList(), maxDelegations = 1).newSession()
        val decision = KellvynModelDecision.Delegate(
            role = KellvynSpecialistRole.RESEARCHER,
            task = "Pesquise a documentação.",
            context = "",
            allowedTools = emptySet()
        )

        val first = session.dispatch(decision)
        val second = session.dispatch(decision)

        assertTrue(first.consumesDelegationSlot)
        assertFalse(second.consumesDelegationSlot)
        assertTrue(second.content.contains("limite de delegações"))
        assertEquals(1, engine.histories.size)
    }

    @Test
    fun separateSessionsDoNotShareDelegationBudgets() = runBlocking {
        val engine = RecordingEngine(
            KellvynModelDecision.Final("Primeiro especialista concluiu."),
            KellvynModelDecision.Final("Segundo especialista concluiu.")
        )
        val coordinator = orchestrator(engine, emptyList(), maxDelegations = 1)
        val firstSession = coordinator.newSession()
        val secondSession = coordinator.newSession()
        val decision = KellvynModelDecision.Delegate(
            role = KellvynSpecialistRole.RESEARCHER,
            task = "Analise a fonte.",
            context = "",
            allowedTools = emptySet()
        )

        val first = firstSession.dispatch(decision)
        val second = secondSession.dispatch(decision)

        assertTrue(first.consumesDelegationSlot)
        assertTrue(second.consumesDelegationSlot)
        assertEquals(2, engine.histories.size)
    }

    @Test
    fun refusesOversizedDelegatedContextBeforeCreatingSpecialist() = runBlocking {
        val engine = RecordingEngine()
        val session = orchestrator(
            engine,
            emptyList(),
            maxDelegationContextChars = 4
        ).newSession()

        val outcome = session.dispatch(
            decision = KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.RESEARCHER,
                task = "Pesquise.",
                context = "contexto maior",
                allowedTools = emptySet()
            )
        )

        assertFalse(outcome.consumesDelegationSlot)
        assertTrue(outcome.content.contains("excedeu os limites"))
        assertTrue(engine.histories.isEmpty())
    }

    private fun orchestrator(
        engine: KellvynInferenceEngine,
        tools: List<KellvynAgentTool>,
        maxDelegations: Int = 2,
        maxDelegationContextChars: Int = 4_000,
        monotonicNanos: () -> Long = System::nanoTime
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
        specialistProfiles = KellvynSpecialistProfileRegistry.default(),
        monotonicNanos = monotonicNanos
    )

    private class RecordingTraceRecorder : KellvynAgentLabTraceRecorder {
        val events = mutableListOf<KellvynAgentLabEvent>()
        override suspend fun record(event: KellvynAgentLabEvent) {
            events += event
        }
    }

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
