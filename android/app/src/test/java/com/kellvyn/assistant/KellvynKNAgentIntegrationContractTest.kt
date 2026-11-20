package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-layer contract test: wire protocol -> typed decision -> main agent ->
 * specialist orchestration -> read-only tool -> untrusted handoff -> main response.
 *
 * The scripted engine deliberately supplies valid KELLVYN/1 messages. This verifies
 * the integration contract, not the competence of a trained neural checkpoint.
 */
class KellvynKNAgentIntegrationContractTest {
    @Test
    fun protocolDelegationRunsSpecialistWithLeastPrivilegeAndUntrustedHandoff() = runBlocking {
        val engine = ProtocolScriptedEngine(
            delegate(
                role = KellvynSpecialistRole.CODE_EXPLORER,
                task = "Leia o arquivo e identifique a validação.",
                context = "Somente o trecho autorizado.",
                tools = setOf("read_source", "delete_source")
            ),
            callTool(
                name = "read_source",
                arguments = mapOf("path" to "src/Validator.kt")
            ),
            final("A validação rejeita caminhos fora do workspace."),
            final("O especialista confirmou a validação de caminhos.")
        )
        val readTool = RecordingTool(
            KellvynToolSpec(
                name = "read_source",
                description = "Lê um arquivo do workspace.",
                requiredArguments = setOf("path"),
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolResult(true, "class Validator { fun checkPath() = rejectTraversal() }")
        )
        val deleteTool = RecordingTool(
            KellvynToolSpec(
                name = "delete_source",
                description = "Remove um arquivo do workspace.",
                risk = KellvynToolRisk.DESTRUCTIVE,
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolResult(true, "deleted")
        )
        val recorder = RecordingTraceRecorder()
        val agent = KellvynAgent(
            inference = engine,
            tools = KellvynToolRegistry(listOf(readTool, deleteTool)),
            config = KellvynAgentConfig(maxDelegations = 1, allowDelegation = true),
            skillRegistry = KellvynSkillRegistry.empty(),
            traceSessionFactory = {
                KellvynAgentLabTraceSession.create(recorder)
            }
        )

        val result = agent.run(
            "Revise o validador. Não revele o contexto privado do coordenador."
        )

        assertTrue(result is KellvynAgentResult.Completed)
        assertTrue((result as KellvynAgentResult.Completed).answer.contains("especialista confirmou"))
        assertEquals(4, engine.histories.size)
        assertEquals(setOf("read_source", "delete_source"), engine.toolsSeen[0].map { it.name }.toSet())
        assertEquals(listOf("read_source"), engine.toolsSeen[1].map { it.name })
        assertEquals(listOf("read_source"), engine.toolsSeen[2].map { it.name })
        assertEquals(1, readTool.executions)
        assertEquals(0, deleteTool.executions)

        assertTrue(engine.histories[1].first().content.contains("Explorador de código"))
        assertTrue(engine.histories[1].joinToString("\n") { it.content }.contains("Somente o trecho autorizado"))
        assertFalse(engine.histories[1].joinToString("\n") { it.content }
            .contains("contexto privado do coordenador"))

        assertTrue(engine.histories[2].any {
            it.source == "tool:read_source" && it.trust == KellvynTrustLevel.UNTRUSTED
        })
        assertTrue(engine.histories[3].any {
            it.source == "specialist:code_explorer" &&
                it.trust == KellvynTrustLevel.UNTRUSTED &&
                it.content.contains("A validação rejeita caminhos")
        })
        assertTrue(recorder.events.any { it.kind == KellvynAgentLabEventKind.DELEGATION_STARTED })
        assertTrue(recorder.events.any { it.kind == KellvynAgentLabEventKind.DELEGATION_FINISHED })
    }

    @Test
    fun malformedWireDecisionFailsBeforeAnyToolExecution() = runBlocking {
        val engine = ProtocolScriptedEngine(
            "KELLVYN/1\nCALL_TOOL\nNAME\t../delete\nEND"
        )
        val tool = RecordingTool(
            KellvynToolSpec(
                name = "read_source",
                description = "Lê um arquivo.",
                requiredArguments = setOf("path"),
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolResult(true, "conteúdo")
        )
        val agent = KellvynAgent(
            inference = engine,
            tools = KellvynToolRegistry(listOf(tool)),
            skillRegistry = KellvynSkillRegistry.empty()
        )

        var rejected = false
        try {
            agent.run("Leia o arquivo.")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(0, tool.executions)
    }

    private fun final(text: String): String = listOf(
        "KELLVYN/1",
        "FINAL",
        "TEXT\t${KellvynDecisionProtocol.encodeText(text)}",
        "END"
    ).joinToString("\n")

    private fun callTool(name: String, arguments: Map<String, String>): String = buildList {
        add("KELLVYN/1")
        add("CALL_TOOL")
        add("NAME\t${KellvynDecisionProtocol.encodeText(name)}")
        arguments.toSortedMap().forEach { (key, value) ->
            add("ARG\t${KellvynDecisionProtocol.encodeText(key)}\t${KellvynDecisionProtocol.encodeText(value)}")
        }
        add("END")
    }.joinToString("\n")

    private fun delegate(
        role: KellvynSpecialistRole,
        task: String,
        context: String,
        tools: Set<String>
    ): String = buildList {
        add("KELLVYN/1")
        add("DELEGATE")
        add("ROLE\t${role.name}")
        add("TASK\t${KellvynDecisionProtocol.encodeText(task)}")
        add("CONTEXT\t${KellvynDecisionProtocol.encodeText(context)}")
        tools.sorted().forEach { add("TOOL\t${KellvynDecisionProtocol.encodeText(it)}") }
        add("END")
    }.joinToString("\n")

    private class ProtocolScriptedEngine(
        vararg wireDecisions: String
    ) : KellvynInferenceEngine {
        private val pending = ArrayDeque(wireDecisions.toList())
        val histories = mutableListOf<List<KellvynAgentMessage>>()
        val toolsSeen = mutableListOf<List<KellvynToolSpec>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            histories += history.toList()
            toolsSeen += availableTools.toList()
            val wire = pending.removeFirst()
            return when (val parsed = KellvynDecisionProtocol.parse(wire)) {
                is KellvynDecisionProtocol.ParseResult.Accepted -> parsed.decision
                is KellvynDecisionProtocol.ParseResult.Rejected ->
                    throw IllegalArgumentException(parsed.reason)
            }
        }
    }

    private class RecordingTool(
        override val spec: KellvynToolSpec,
        private val result: KellvynToolResult
    ) : KellvynAgentTool {
        var executions = 0
        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            executions++
            return result
        }
    }

    private class RecordingTraceRecorder : KellvynAgentLabTraceRecorder {
        val events = mutableListOf<KellvynAgentLabEvent>()
        override suspend fun record(event: KellvynAgentLabEvent) {
            events += event
        }
    }
}
