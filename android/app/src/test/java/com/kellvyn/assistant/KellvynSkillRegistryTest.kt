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

        val androidSkills = registry.selectFor("Quero compilar um APK Android e testar no emulador.")

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
        assertTrue(systemMessage.content.contains("Não declare APK funcional"))
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
