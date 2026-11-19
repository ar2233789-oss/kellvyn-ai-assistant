package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAgentCreationStudioTest {

    @Test
    fun revisionPreviewCompilesCandidateWithoutPersistingOrChangingActiveProfile() = runBlocking {
        val registry = KellvynSpecialistProfileRegistry.default()
        val active = registry.requireProfile(KellvynSpecialistRole.RESEARCHER)
        val studio = KellvynAgentCreationStudio(registry)
        val tool = studio.tools.single {
            it.spec.name == KellvynAgentCreationStudio.PROFILE_REVISION_PREVIEW_TOOL
        }

        val result = tool.execute(
            mapOf(
                "role" to "RESEARCHER",
                "display_name" to "Pesquisador de evidências",
                "mission" to "Comparar fontes e separar evidência, hipótese e inferência.",
                "instruction" to "Registre a origem de cada afirmação, compare datas e declare incertezas."
            )
        )

        assertTrue(result.success)
        assertTrue(result.content.contains("PRÉVIA DE REVISÃO"))
        assertTrue(result.content.contains("NÃO SALVA E NÃO ATIVA"))
        assertTrue(result.content.contains("VERSÃO CANDIDATA: ${active.version + 1}"))
        assertTrue(result.content.contains("SHA-256 CANDIDATO:"))
        assertTrue(result.content.contains("CAPACIDADES FIXADAS PELA POLÍTICA CENTRAL"))
        assertTrue(result.content.contains("DELEGAÇÃO ANINHADA: DESATIVADA"))
        assertTrue(result.content.contains("MUTAÇÃO: DESATIVADA"))
        assertTrue(result.content.contains("PREVIEW_ONLY"))
        assertEquals(active, registry.requireProfile(KellvynSpecialistRole.RESEARCHER))
        assertEquals(14, registry.profiles().size)
    }

    @Test
    fun revisionPreviewRejectsUnknownRoleOrArguments() = runBlocking {
        val tool = KellvynAgentCreationStudio(
            KellvynSpecialistProfileRegistry.default()
        ).tools.single {
            it.spec.name == KellvynAgentCreationStudio.PROFILE_REVISION_PREVIEW_TOOL
        }

        val unknownRole = tool.execute(
            mapOf(
                "role" to "UNTRUSTED_ROLE",
                "mission" to "Missão.",
                "instruction" to "Instrução."
            )
        )
        val unknownArgument = tool.execute(
            mapOf(
                "role" to "RESEARCHER",
                "mission" to "Missão.",
                "instruction" to "Instrução.",
                "allow_mutation" to "true"
            )
        )

        assertFalse(unknownRole.success)
        assertFalse(unknownArgument.success)
    }

    @Test
    fun revisionPreviewRejectsInvalidCandidateWithoutChangingPolicyOrActiveProfile() = runBlocking {
        val registry = KellvynSpecialistProfileRegistry.default()
        val active = registry.requireProfile(KellvynSpecialistRole.CODE_EXPLORER)
        val allowedBefore = KellvynSpecialistToolPolicy.capabilitiesFor(active.role)
        val tool = KellvynAgentCreationStudio(registry).tools.single {
            it.spec.name == KellvynAgentCreationStudio.PROFILE_REVISION_PREVIEW_TOOL
        }

        val invalid = tool.execute(
            mapOf(
                "role" to "CODE_EXPLORER",
                "mission" to "",
                "instruction" to "Instrução válida."
            )
        )

        assertFalse(invalid.success)
        assertEquals(active, registry.requireProfile(KellvynSpecialistRole.CODE_EXPLORER))
        assertEquals(allowedBefore, KellvynSpecialistToolPolicy.capabilitiesFor(active.role))
    }
}
