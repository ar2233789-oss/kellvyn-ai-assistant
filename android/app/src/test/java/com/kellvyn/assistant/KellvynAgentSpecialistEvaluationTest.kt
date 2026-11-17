package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for every hidden specialist role.
 *
 * These tests validate routing, context isolation, least-privilege tool filtering
 * and trust boundaries. They do NOT claim that an untrained neural model is good
 * at research, code review or testing; capability benchmarks are tracked separately.
 */
class KellvynAgentSpecialistEvaluationTest {

    @Test
    fun everySpecialistReceivesOnlyItsTaskAndReadOnlyTools() = runBlocking {
        val roleCases = listOf(
            RoleCase(
                role = KellvynSpecialistRole.RESEARCHER,
                expectedInstruction = "Pesquisador interno"
            ),
            RoleCase(
                role = KellvynSpecialistRole.CODE_EXPLORER,
                expectedInstruction = "Explorador de código"
            ),
            RoleCase(
                role = KellvynSpecialistRole.REVIEWER,
                expectedInstruction = "Revisor interno"
            ),
            RoleCase(
                role = KellvynSpecialistRole.TEST_ANALYST,
                expectedInstruction = "Analista de testes"
            )
        )

        for (roleCase in roleCases) {
            val delegatedTask = "Analise exclusivamente a tarefa ${roleCase.role.name}."
            val engine = SpecialistEvaluationEngine(
                KellvynModelDecision.Delegate(
                    role = roleCase.role,
                    task = delegatedTask,
                    context = "CONTEXTO_DELEGADO_CONTROLADO",
                    allowedTools = setOf("read_source", "write_source")
                ),
                KellvynModelDecision.Final("Síntese interna com evidência observada."),
                KellvynModelDecision.Final("Resultado revisado pelo Kellvyn principal.")
            )
            val readTool = SpecialistEvaluationTool(
                KellvynToolSpec("read_source", "Lê um arquivo autorizado")
            )
            val writeTool = SpecialistEvaluationTool(
                KellvynToolSpec(
                    name = "write_source",
                    description = "Altera um arquivo",
                    risk = KellvynToolRisk.REVERSIBLE_WRITE
                )
            )
            val agent = KellvynAgent(
                inference = engine,
                tools = KellvynToolRegistry(listOf(readTool, writeTool)),
                config = KellvynAgentConfig(maxDelegations = 1),
                skillRegistry = KellvynSkillRegistry.empty()
            )

            val result = agent.run("ORIGINAL_PRIVATE_CONTEXT_DO_COORDENADOR")

            assertTrue(
                "A coordenação deveria concluir após o retorno de ${roleCase.role.name}.",
                result is KellvynAgentResult.Completed
            )
            assertEquals("O fluxo deve consultar principal, especialista e principal.", 3, engine.histories.size)

            val specialistHistory = engine.histories[1]
            val specialistSystem = specialistHistory.first().content
            assertTrue(specialistSystem.contains(roleCase.expectedInstruction))
            assertTrue(specialistHistory.any { it.content.contains(delegatedTask) })
            assertTrue(specialistHistory.any { it.content.contains("CONTEXTO_DELEGADO_CONTROLADO") })
            assertFalse(
                "O especialista ${roleCase.role.name} recebeu contexto privado do coordenador.",
                specialistHistory.joinToString("\n") { it.content }
                    .contains("ORIGINAL_PRIVATE_CONTEXT_DO_COORDENADOR")
            )

            assertEquals(
                "O especialista ${roleCase.role.name} só pode receber ferramentas READ_ONLY.",
                listOf("read_source"),
                engine.toolsSeen[1].map { it.name }
            )
            assertEquals(0, readTool.executions)
            assertEquals(0, writeTool.executions)

            val returnedObservation = engine.histories[2].firstOrNull {
                it.source == "specialist:${roleCase.role.name.lowercase()}"
            }
            assertNotNull(returnedObservation)
            assertEquals(KellvynTrustLevel.UNTRUSTED, returnedObservation?.trust)
            assertTrue(returnedObservation?.content.orEmpty().contains("Síntese interna"))
        }
    }

    private data class RoleCase(
        val role: KellvynSpecialistRole,
        val expectedInstruction: String
    )

    private class SpecialistEvaluationEngine(
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
            check(pending.isNotEmpty()) { "O teste consumiu mais decisões do que o esperado." }
            return pending.removeFirst()
        }
    }

    private class SpecialistEvaluationTool(
        override val spec: KellvynToolSpec
    ) : KellvynAgentTool {
        var executions = 0

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            executions++
            return KellvynToolResult(true, "resultado de teste")
        }
    }
}
