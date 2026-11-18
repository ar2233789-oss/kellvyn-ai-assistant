package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynSpecialistProfileRegistryTest {
    @Test
    fun defaultRegistryContainsExactlyFourteenUniqueVersionedProfiles() {
        val registry = KellvynSpecialistProfileRegistry.default()
        assertEquals(14, registry.profiles().size)
        assertEquals(
            KellvynSpecialistRole.values().toSet(),
            registry.profiles().map { it.role }.toSet()
        )
        assertTrue(registry.profiles().all { it.version == 1 })
        assertTrue(registry.profiles().all { it.requiredOutputSections.size == 4 })
        assertTrue(registry.validationErrors().isEmpty())
    }

    @Test
    fun profileCompilerProducesFourteenStableHashesAndRejectsTamperedContentByHashChange() {
        val registry = KellvynSpecialistProfileRegistry.default()
        val compiler = KellvynSpecialistProfileCompiler()
        val first = compiler.compileAll(registry.profiles())
        val second = compiler.compileAll(registry.profiles())

        assertTrue(first.success)
        assertEquals(14, first.profiles.size)
        assertEquals(
            first.profiles.map { it.role to it.profileSha256 },
            second.profiles.map { it.role to it.profileSha256 }
        )
        assertTrue(first.profiles.all { it.profileSha256.matches(Regex("[0-9a-f]{64}")) })

        val changed = registry.profiles().map { profile ->
            if (profile.role == KellvynSpecialistRole.RESEARCHER) {
                profile.copy(instruction = profile.instruction + " Registrar a data de consulta.")
            } else {
                profile
            }
        }
        val changedResearcher = compiler.compileAll(changed).profiles
            .single { it.role == KellvynSpecialistRole.RESEARCHER }
        val originalResearcher = first.profiles
            .single { it.role == KellvynSpecialistRole.RESEARCHER }
        assertTrue(changedResearcher.profileSha256 != originalResearcher.profileSha256)
    }

    @Test
    fun profileHashPreservesOutputSectionBoundaries() {
        val original = KellvynSpecialistProfileRegistry.default()
            .requireProfile(KellvynSpecialistRole.RESEARCHER)
            .copy(requiredOutputSections = listOf("alpha", "beta", "gamma", "delta"))
        val resegmented = original.copy(
            requiredOutputSections = listOf("alpha\\u001fbeta", "gamma", "delta")
        )
        val compiler = KellvynSpecialistProfileCompiler()
        val originalHash = compiler.compile(original)?.profileSha256
        val resegmentedHash = compiler.compile(resegmented)?.profileSha256

        assertTrue(originalHash != null)
        assertTrue(resegmentedHash != null)
        assertTrue(originalHash != resegmentedHash)
    }

    @Test
    fun profilesCannotEnableMutationOrNestedDelegation() {
        val profiles = KellvynSpecialistProfileRegistry.default().profiles()
        assertTrue(profiles.all { !it.allowMutation })
        assertTrue(profiles.all { !it.allowDelegation })
    }

    @Test
    fun allProfilesHaveCapabilitiesInTheCentralPolicy() {
        val registry = KellvynSpecialistProfileRegistry.default()
        assertTrue(
            registry.profiles().all {
                KellvynSpecialistToolPolicy.capabilitiesFor(it.role).isNotEmpty()
            }
        )
    }

    @Test
    fun generatedInstructionContainsRoleBoundaryAndEvidenceContract() {
        val profile = KellvynSpecialistProfileRegistry.default()
            .requireProfile(KellvynSpecialistRole.RESEARCHER)
        val prompt = profile.systemInstruction(setOf("dangerous_tool"))
        assertTrue(prompt.contains("PAPEL: RESEARCHER — Pesquisador"))
        assertTrue(prompt.contains("não delegue"))
        assertTrue(prompt.contains("não altere arquivos"))
        assertTrue(prompt.contains("Evidências observadas"))
        assertTrue(prompt.contains("dangerous_tool"))
    }

    @Test
    fun hiddenStudioCanListInspectAndValidateProfiles() = runBlocking {
        val studio = KellvynAgentCreationStudio(KellvynSpecialistProfileRegistry.default())

        val catalogue = studio.tools
            .single { it.spec.name == KellvynAgentCreationStudio.CATALOG_TOOL }
            .execute(emptyMap())
        assertTrue(catalogue.success)
        assertTrue(catalogue.content.contains("CATÁLOGO INTERNO DOS 14 PAPÉIS"))
        assertTrue(catalogue.content.contains("VISUAL_ANALYST"))
        assertTrue(catalogue.content.contains("RESEARCHER"))

        val inspected = studio.tools
            .single { it.spec.name == KellvynAgentCreationStudio.INSPECT_TOOL }
            .execute(mapOf("role" to "AUDITOR"))
        assertTrue(inspected.success)
        assertTrue(inspected.content.contains("Auditor independente"))
        assertTrue(inspected.content.contains("MUTAÇÃO: desativada"))

        val validation = studio.tools
            .single { it.spec.name == KellvynAgentCreationStudio.VALIDATE_TOOL }
            .execute(emptyMap())
        assertTrue(validation.success)
        assertTrue(validation.content.contains("14/14"))
        assertTrue(validation.content.contains("SHA-256"))
    }

    @Test
    fun hiddenStudioRejectsUnknownRolesAndUnexpectedArguments() = runBlocking {
        val studio = KellvynAgentCreationStudio(KellvynSpecialistProfileRegistry.default())
        val inspect = studio.tools.single { it.spec.name == KellvynAgentCreationStudio.INSPECT_TOOL }
        val invalidRole = inspect.execute(mapOf("role" to "ROOT"))
        val extraArgument = inspect.execute(mapOf("role" to "AUDITOR", "write" to "true"))
        val catalogueExtraArgument = studio.tools
            .single { it.spec.name == KellvynAgentCreationStudio.CATALOG_TOOL }
            .execute(mapOf("project_id" to "not-accepted"))

        assertFalse(invalidRole.success)
        assertFalse(extraArgument.success)
        assertFalse(catalogueExtraArgument.success)
    }

    @Test
    fun benchmarkRegistryCoversAllFourteenRolesWithTwoCasesEach() {
        val benchmark = KellvynSpecialistBenchmarkRegistry.default()
        assertEquals(28, benchmark.cases().size)
        assertTrue(benchmark.validationErrors().isEmpty())
        for (role in KellvynSpecialistRole.values()) {
            assertEquals(2, benchmark.forRole(role).size)
            assertTrue(
                benchmark.forRole(role).all {
                    KellvynSpecialistToolPolicy.capabilitiesFor(role)
                        .containsAll(it.requiredCapabilities)
                }
            )
        }
    }

    @Test
    fun hiddenStudioListsBenchmarkScenariosWithoutClaimingTheyWereExecuted() = runBlocking {
        val studio = KellvynAgentCreationStudio(KellvynSpecialistProfileRegistry.default())
        val result = studio.tools
            .single { it.spec.name == KellvynAgentCreationStudio.BENCHMARK_CATALOG_TOOL }
            .execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("28 casos"))
        assertTrue(result.content.contains("não um resultado de competência"))
        assertTrue(result.content.contains("research-date-conflict"))
        assertTrue(result.content.contains("visual-accessibility-review"))

        val detailed = studio.tools
            .single { it.spec.name == KellvynAgentCreationStudio.BENCHMARK_CATALOG_TOOL }
            .execute(mapOf("role" to "RESEARCHER"))
        assertTrue(detailed.success)
        assertTrue(detailed.content.contains("Distingue data do evento"))
        assertTrue(detailed.content.contains("Inventar URL ou citação"))

        val invalidRole = studio.tools
            .single { it.spec.name == KellvynAgentCreationStudio.BENCHMARK_CATALOG_TOOL }
            .execute(mapOf("role" to "ROOT"))
        assertFalse(invalidRole.success)
    }

    @Test
    fun profileToolsAreScopedToDesignAndEvaluationRoles() {
        val specs = KellvynAgentCreationStudio.MANIFEST.toolSpecs
        val designRoles = setOf(
            KellvynSpecialistRole.PROGRAMMING_ARCHITECT,
            KellvynSpecialistRole.PLANNING_ANALYST,
            KellvynSpecialistRole.AUDITOR
        )
        val evaluationRoles = setOf(
            KellvynSpecialistRole.TEST_ANALYST,
            KellvynSpecialistRole.LEARNING_ANALYST,
            KellvynSpecialistRole.AUDITOR
        )

        for (role in KellvynSpecialistRole.values()) {
            val allowed = KellvynSpecialistToolPolicy.permittedToolNames(
                role,
                specs,
                allowNetworkRead = false
            )
            assertEquals(role in designRoles, KellvynAgentCreationStudio.CATALOG_TOOL in allowed)
            assertEquals(role in designRoles, KellvynAgentCreationStudio.INSPECT_TOOL in allowed)
            assertEquals(role in evaluationRoles, KellvynAgentCreationStudio.VALIDATE_TOOL in allowed)
            assertEquals(role in evaluationRoles, KellvynAgentCreationStudio.BENCHMARK_CATALOG_TOOL in allowed)
        }
    }
}
