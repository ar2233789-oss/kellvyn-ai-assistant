package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynSkillRegistryTest {
    @Test
    fun loadsOnlySkillsRelevantToTheCurrentTask() {
        val registry = KellvynSkillRegistry.default()

        val androidSkills = registry.selectFor("Quero compilar um APK Android e testar o app no emulador.")

        assertTrue(androidSkills.isNotEmpty())
        assertTrue(androidSkills.any { it.skill.id == "android-build-verification" })
        assertTrue(androidSkills.any { it.skill.id == "app-functional-testing" })
        assertFalse(androidSkills.any { it.skill.id == "web-research" })
    }

    @Test
    fun researchSkillLoadsForNamedReferenceProducts() {
        val registry = KellvynSkillRegistry.default()

        val skills = registry.selectFor("Pesquisar recursos do Replit e Cursor.")

        assertEquals(listOf("web-research"), skills.map { it.skill.id })
    }

    @Test
    fun selectionIsDeterministicAndRespectsInstructionBudget() {
        val registry = KellvynSkillRegistry(
            listOf(
                skill("b-skill", "segredo-" + "b".repeat(35)),
                skill("a-skill", "segredo-" + "a".repeat(35))
            )
        )

        val selected = registry.selectFor("executar build", limit = 2, maxChars = 50)

        assertEquals(listOf("a-skill"), selected.map { it.skill.id })
        assertTrue(selected.sumOf { it.skill.instructions.length } <= 50)
    }

    @Test
    fun noSkillIsLoadedForUnrelatedGoalOrZeroLimit() {
        val registry = KellvynSkillRegistry.default()

        assertTrue(registry.selectFor("Olá, tudo bem?").isEmpty())
        assertTrue(registry.selectFor("compilar APK", limit = 0).isEmpty())
    }

    @Test
    fun relevantSkillIsInjectedAsTrustedSystemProcedure() = runBlocking {
        val registry = KellvynSkillRegistry.default()
        val model = SkillFakeEngine()
        val agent = KellvynAgent(
            inference = model,
            tools = KellvynToolRegistry(emptyList()),
            skillRegistry = registry
        )

        val result = agent.run("Quero compilar um APK Android.")

        assertTrue(result is KellvynAgentResult.Completed)
        val systemMessage = model.seen.single().first { it.role == KellvynMessageRole.SYSTEM }
        assertEquals(KellvynTrustLevel.TRUSTED, systemMessage.trust)
        assertTrue(systemMessage.content.contains("android-build-verification"))
        assertTrue(systemMessage.content.contains("Nunca declare APK funcional"))
    }

    @Test
    fun appBuilderSkillLoadsForProjectCreationWithoutGrantingCapabilities() {
        val registry = KellvynSkillRegistry.default()
        val matches = registry.selectFor(
            "Crie um aplicativo Android do zero; planeje, construa e teste o projeto."
        )
        val builder = matches.firstOrNull { it.skill.id == "app-builder-workflow" }

        assertTrue(builder != null)
        assertTrue(builder!!.skill.instructions.contains("PLAN → BUILD → TEST"))
        assertTrue(builder.skill.instructions.contains("Uma skill não concede permissões"))
        assertTrue(matches.any { it.skill.id == "android-build-verification" })
    }

    @Test
    fun agentLabSkillLoadsForBenchmarkAndIndependentVerificationTasks() {
        val registry = KellvynSkillRegistry.default()
        val matches = registry.selectFor(
            "Compare o benchmark dos 14 agentes com o baseline e verifique traces e evidências independentes."
        )

        assertTrue(matches.any { it.skill.id == "agent-lab-evaluation" })
        val lab = matches.first { it.skill.id == "agent-lab-evaluation" }
        assertTrue(lab.skill.instructions.contains("REVIEW_REQUIRED"))
        assertTrue(lab.skill.instructions.contains("O próprio agente nunca dá nota"))
    }

    @Test
    fun botOperationsSkillSeparatesTransportFromBrainAndProtectsCredentials() {
        val registry = KellvynSkillRegistry.default()
        val matches = registry.selectFor(
            "Diagnostique o bot do Telegram: confira heartbeat, conexão e prontidão da IA antes de reconectar."
        )
        val bot = matches.firstOrNull { it.skill.id == "bot-operations" }

        assertTrue(bot != null)
        assertTrue(bot!!.skill.instructions.contains("Nunca solicite que o proprietário cole o token"))
        assertTrue(bot.skill.instructions.contains("Transporte conectado não comprova"))
        assertTrue(bot.skill.instructions.contains("WhatsApp permanece indisponível"))
    }

    @Test
    fun productResearchDoesNotLoadTheAppBuilderSkill() {
        val registry = KellvynSkillRegistry.default()

        val matches = registry.selectFor("Pesquisar recursos do Replit e Cursor.")

        assertEquals(listOf("web-research"), matches.map { it.skill.id })
    }

    private fun skill(id: String, instructions: String) = KellvynSkillDefinition(
        id = id,
        description = "Skill de teste.",
        triggerTerms = setOf("build"),
        instructions = instructions
    )

    private class SkillFakeEngine : KellvynInferenceEngine {
        val seen = mutableListOf<List<KellvynAgentMessage>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            seen += history
            return KellvynModelDecision.Final("Procedimento carregado.")
        }
    }
}
