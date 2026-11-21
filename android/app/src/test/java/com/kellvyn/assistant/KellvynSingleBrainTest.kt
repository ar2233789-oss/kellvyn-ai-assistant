package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynSingleBrainTest {
    @Test
    fun finalDecisionReturnsFromTheSameLocalBrain() = runBlocking {
        val inference = SequenceInference(
            KellvynModelDecision.Final("Resposta concluída pelo KN.")
        )
        val brain = brain(inference, KellvynToolRegistry(emptyList()))

        val result = brain.run("Responda somente com uma confirmação.")

        assertTrue(result is KellvynBrainResult.Completed)
        assertEquals("Resposta concluída pelo KN.", (result as KellvynBrainResult.Completed).answer)
        assertEquals(1, inference.histories.size)
    }

    @Test
    fun sameBrainCallsValidatedToolReadsItsResultAndThenDecides() = runBlocking {
        var executions = 0
        val tool = object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "local_lookup",
                description = "Consulta um registro local pelo termo informado.",
                requiredArguments = setOf("query"),
                risk = KellvynToolRisk.READ_ONLY
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                executions++
                return KellvynToolResult(true, "registro encontrado: ${arguments.getValue("query")}")
            }
        }
        val inference = SequenceInference(
            KellvynModelDecision.CallTool("local_lookup", mapOf("query" to "KN")),
            KellvynModelDecision.Final("Encontrei o registro KN.")
        )
        val brain = brain(inference, KellvynToolRegistry(listOf(tool)))

        val result = brain.run("Pesquise o registro local KN.")

        assertTrue(result is KellvynBrainResult.Completed)
        assertEquals(1, executions)
        assertEquals(2, inference.histories.size)
        assertTrue(inference.histories[1].any {
            it.role == KellvynMessageRole.TOOL &&
                it.source == "tool:local_lookup" &&
                it.content.contains("registro encontrado: KN")
        })
        assertEquals(1, (result as KellvynBrainResult.Completed).toolCalls)
    }

    @Test
    fun brainCannotExecuteARegisteredToolThatWasNotShownInThisDecision() = runBlocking {
        var hiddenExecutions = 0
        val visible = object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "local_lookup",
                description = "Consulta um registro local pelo termo informado.",
                requiredArguments = setOf("query"),
                risk = KellvynToolRisk.READ_ONLY
            )
            override suspend fun execute(arguments: Map<String, String>) =
                KellvynToolResult(true, "visível")
        }
        val hidden = object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "hidden_export",
                description = "Exporta arquivos para armazenamento remoto.",
                requiredArguments = setOf("destination"),
                risk = KellvynToolRisk.DESTRUCTIVE
            )
            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                hiddenExecutions++
                return KellvynToolResult(true, "não deveria executar")
            }
        }
        val inference = SequenceInference(
            KellvynModelDecision.CallTool(
                "hidden_export",
                mapOf("destination" to "outside")
            ),
            KellvynModelDecision.Final("O KN bloqueou a ferramenta fora do catálogo apresentado.")
        )
        val brain = brain(
            inference = inference,
            tools = KellvynToolRegistry(listOf(visible, hidden)),
            config = KellvynAgentConfig(maxToolsInModelPrompt = 1)
        )

        val result = brain.run("Pesquise o registro local KN.")

        assertTrue(result is KellvynBrainResult.Completed)
        assertEquals(0, hiddenExecutions)
        assertEquals(listOf("local_lookup"), inference.presentedToolNames.first())
        assertTrue(inference.histories[1].any {
            it.source == "tool:hidden_export" &&
                it.content.contains("não estava no catálogo apresentado")
        })
    }

    @Test
    fun externalDecisionIsRejectedWithoutCreatingAnotherIntelligence() = runBlocking {
        val inference = SequenceInference(
            KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.RESEARCHER,
                task = "Transferir a decisão para outra inteligência."
            )
        )
        val brain = brain(inference, KellvynToolRegistry(emptyList()))

        val result = brain.run("Resolva a tarefa usando somente o KN.")

        assertTrue(result is KellvynBrainResult.Failed)
        assertEquals(1, inference.histories.size)
        assertFalse(inference.histories.flatten().any {
            it.source.startsWith("specialist:")
        })
    }

    private fun brain(
        inference: KellvynInferenceEngine,
        tools: KellvynToolRegistry,
        config: KellvynAgentConfig = KellvynAgentConfig()
    ) = KellvynSingleBrain(
        inference = inference,
        tools = tools,
        config = config,
        skillRegistry = KellvynSkillRegistry.empty()
    )

    private class SequenceInference(
        vararg decisions: KellvynModelDecision
    ) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        val histories = mutableListOf<List<KellvynAgentMessage>>()
        val presentedToolNames = mutableListOf<List<String>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            histories += history.toList()
            presentedToolNames += availableTools.map { it.name }
            check(pending.isNotEmpty()) {
                "O cérebro KN pediu mais decisões do que o teste forneceu."
            }
            return pending.removeFirst()
        }
    }
}
