package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAgentDelegationTest {
    @Test
    fun specialistUsesIsolatedContextAndOnlyReadOnlyTools() = runBlocking {
        val engine = DelegationFakeEngine(
            KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.CODE_EXPLORER,
                task = "Encontre a função que valida os caminhos.",
                context = "Trecho selecionado: KellvynAgentLimits.",
                allowedTools = setOf("read_source", "delete_source")
            ),
            KellvynModelDecision.CallTool("read_source", mapOf("path" to "src/KellvynAgentLimits.kt")),
            KellvynModelDecision.Final("A função fica em KellvynAgentLimits."),
            KellvynModelDecision.Final("Revisei o resultado do especialista.")
        )
        val readTool = DelegationFakeTool(
            KellvynToolSpec(
                "read_source",
                "Lê código",
                requiredArguments = setOf("path"),
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolResult(true, "código")
        )
        val deleteTool = DelegationFakeTool(
            KellvynToolSpec(
                "delete_source",
                "Apaga código",
                risk = KellvynToolRisk.DESTRUCTIVE,
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolResult(true, "apagado")
        )
        val agent = KellvynAgent(
            inference = engine,
            tools = KellvynToolRegistry(listOf(readTool, deleteTool)),
            config = KellvynAgentConfig(maxDelegations = 1),
            skillRegistry = KellvynSkillRegistry.empty()
        )

        val result = agent.run("Analise um segredo do contexto principal sem expô-lo.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(4, engine.histories.size)
        assertEquals(setOf("read_source", "delete_source"), engine.toolsSeen[0].map { it.name }.toSet())
        assertEquals(listOf("read_source"), engine.toolsSeen[1].map { it.name })
        assertEquals(listOf("read_source"), engine.toolsSeen[2].map { it.name })
        assertTrue(engine.histories[1].first().content.contains("especialista interno"))
        assertTrue(engine.histories[1].first().content.contains("Explorador de código"))
        assertFalse(engine.histories[1].any { it.content.contains("segredo do contexto principal") })
        assertTrue(engine.histories[2].any {
            it.source == "tool:read_source" &&
                it.trust == KellvynTrustLevel.UNTRUSTED
        })
        assertTrue(engine.histories[3].any {
            it.source == "specialist:code_explorer" &&
                it.trust == KellvynTrustLevel.UNTRUSTED &&
                it.content.contains("A função fica")
        })
        assertEquals(0, deleteTool.executions)
        assertEquals(1, readTool.executions)
    }

    @Test
    fun specialistCannotDelegateToAnotherSpecialist() = runBlocking {
        val engine = DelegationFakeEngine(
            KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.RESEARCHER,
                task = "Resuma a documentação.",
                allowedTools = emptySet()
            ),
            KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.REVIEWER,
                task = "Delegue mais uma tarefa.",
                allowedTools = emptySet()
            ),
            KellvynModelDecision.Final("Não posso delegar; conclusão da pesquisa."),
            KellvynModelDecision.Final("Resumo principal concluído.")
        )
        val agent = KellvynAgent(
            inference = engine,
            tools = KellvynToolRegistry(emptyList()),
            config = KellvynAgentConfig(maxDelegations = 2),
            skillRegistry = KellvynSkillRegistry.empty()
        )

        val result = agent.run("Pesquise e resuma uma questão.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(4, engine.histories.size)
        assertTrue(engine.histories[2].any {
            it.content.contains("especialistas internos não podem delegar novamente")
        })
    }

    private class DelegationFakeEngine(
        vararg decisions: KellvynModelDecision
    ) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        val histories = mutableListOf<List<KellvynAgentMessage>>()
        val toolsSeen = mutableListOf<List<KellvynToolSpec>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            histories += history
            toolsSeen += availableTools
            return pending.removeFirst()
        }
    }

    private class DelegationFakeTool(
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
