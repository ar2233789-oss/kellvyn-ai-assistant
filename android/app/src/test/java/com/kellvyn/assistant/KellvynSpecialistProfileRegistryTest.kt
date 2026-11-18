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
        assertTrue(prompt.contains("Pesquisador:"))
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
        }
    }
}
