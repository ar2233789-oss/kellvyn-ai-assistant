package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynCognitiveKernelTest {
    @Test
    fun centralModeIsTheDefaultAndDisablesDelegation() {
        val config = KellvynAgentConfig()

        assertFalse(config.allowDelegation)
        assertEquals(0, config.maxDelegations)
        assertTrue(config.systemInstruction.contains("inteligência central única"))
        assertTrue(config.systemInstruction.contains("Não delegue"))
    }

    @Test
    fun modelDelegationRequestIsRejectedAndCentralLoopContinuesWithoutSpecialists() = runBlocking {
        val engine = CentralKernelFakeInference(
            KellvynModelDecision.Delegate(
                role = KellvynSpecialistRole.RESEARCHER,
                task = "Peça a outro agente para pesquisar.",
                allowedTools = emptySet()
            ),
            KellvynModelDecision.Final(
                "Analisei a solicitação diretamente e concluí sem delegar."
            )
        )
        val kernel = KellvynCognitiveKernel(
            inference = engine,
            tools = KellvynToolRegistry(emptyList()),
            skillRegistry = KellvynSkillRegistry.empty()
        )

        val result = kernel.process("Resolva esta tarefa usando a inteligência central.")

        assertTrue(result is KellvynAgentResult.Completed)
        assertEquals(2, engine.histories.size)
        assertTrue(engine.histories[1].any {
            it.source == "central-policy" &&
                it.trust == KellvynTrustLevel.UNTRUSTED &&
                it.content.contains("inteligência central")
        })
        assertFalse(engine.histories.any { history ->
            history.any { it.source.startsWith("specialist:") }
        })
    }

    private class CentralKernelFakeInference(
        vararg decisions: KellvynModelDecision
    ) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        val histories = mutableListOf<List<KellvynAgentMessage>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            histories += history.toList()
            check(pending.isNotEmpty()) { "O kernel solicitou uma decisão além das previstas." }
            return pending.removeFirst()
        }
    }
}
