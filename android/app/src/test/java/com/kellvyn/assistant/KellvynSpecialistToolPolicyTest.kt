package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynSpecialistToolPolicyTest {
    private val specs = listOf(
        KellvynToolSpec(
            name = "code_find_symbols",
            description = "Busca símbolos.",
            capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
        ),
        KellvynToolSpec(
            name = "device_resource_snapshot",
            description = "Lê recursos.",
            capabilities = setOf(KellvynStudioCapability.DEVICE_DIAGNOSTICS)
        ),
        KellvynToolSpec(
            name = "workspace_write_file",
            description = "Escreve arquivo.",
            risk = KellvynToolRisk.REVERSIBLE_WRITE,
            capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
        ),
        KellvynToolSpec(
            name = "unclassified_tool",
            description = "Sem capacidade declarada."
        )
    )

    @Test
    fun codeExplorerCanRequestCodeNavigationButNotDeviceDiagnostics() {
        assertEquals(
            setOf("code_find_symbols"),
            KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.CODE_EXPLORER,
                specs
            )
        )
    }

    @Test
    fun operationsAnalystReceivesDeviceDiagnosticsOnly() {
        assertEquals(
            setOf("device_resource_snapshot"),
            KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.OPERATIONS_ANALYST,
                specs
            )
        )
    }

    @Test
    fun aSpecialistCannotGrantItselfAWriteTool() {
        val denied = KellvynSpecialistToolPolicy.deniedRequestedNames(
            KellvynSpecialistRole.CODE_EXPLORER,
            setOf("workspace_write_file"),
            specs
        )
        assertEquals(setOf("workspace_write_file"), denied)
        assertFalse(
            "workspace_write_file" in KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.CODE_EXPLORER,
                specs
            )
        )
    }

    @Test
    fun unclassifiedToolsAreDeniedByDefault() {
        assertTrue(
            "unclassified_tool" !in KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                KellvynSpecialistRole.REVIEWER,
                specs
            )
        )
    }

    @Test
    fun researchRoleDoesNotInheritCodeNavigationWithoutAnExplicitCapability() {
        val permitted = KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
            KellvynSpecialistRole.RESEARCHER,
            specs
        )
        assertFalse("code_find_symbols" in permitted)
        assertFalse("workspace_write_file" in permitted)
    }


    @Test
    fun allFourteenRolesHaveAnExplicitCapabilityMatrixAndFailClosedToolFilter() {
        val expected = mapOf(
            KellvynSpecialistRole.RESEARCHER to setOf(
                KellvynStudioCapability.WEB_RESEARCH,
                KellvynStudioCapability.DATA_MODELING
            ),
            KellvynSpecialistRole.CODE_EXPLORER to setOf(
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.DATA_MODELING
            ),
            KellvynSpecialistRole.REVIEWER to setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.SECURITY_AUDIT,
                KellvynStudioCapability.DATA_MODELING,
                KellvynStudioCapability.UI_LAYOUT
            ),
            KellvynSpecialistRole.TEST_ANALYST to setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.ANDROID_BUILD,
                KellvynStudioCapability.ANDROID_DEVICE_TEST,
                KellvynStudioCapability.DEVICE_DIAGNOSTICS
            ),
            KellvynSpecialistRole.REASONING_ANALYST to setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.WEB_RESEARCH,
                KellvynStudioCapability.DATA_MODELING,
                KellvynStudioCapability.DEVICE_DIAGNOSTICS
            ),
            KellvynSpecialistRole.LEARNING_ANALYST to setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.NEURAL_TRAINING,
                KellvynStudioCapability.NEURAL_INFERENCE,
                KellvynStudioCapability.DATA_MODELING
            ),
            KellvynSpecialistRole.PROGRAMMING_ARCHITECT to setOf(
                KellvynStudioCapability.AGENT_DESIGN,
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.UI_LAYOUT,
                KellvynStudioCapability.DATA_MODELING
            ),
            KellvynSpecialistRole.MEMORY_ANALYST to setOf(
                KellvynStudioCapability.MEMORY_READ
            ),
            KellvynSpecialistRole.SECURITY_ANALYST to setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.SECURITY_AUDIT,
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.ANDROID_BUILD
            ),
            KellvynSpecialistRole.PLANNING_ANALYST to setOf(
                KellvynStudioCapability.AGENT_DESIGN,
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.WORKFLOW_AUTOMATION,
                KellvynStudioCapability.DEVICE_DIAGNOSTICS,
                KellvynStudioCapability.DATA_MODELING
            ),
            KellvynSpecialistRole.DEBUGGER to setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.ANDROID_BUILD,
                KellvynStudioCapability.ANDROID_DEVICE_TEST,
                KellvynStudioCapability.DEVICE_DIAGNOSTICS
            ),
            KellvynSpecialistRole.AUDITOR to setOf(
                KellvynStudioCapability.AGENT_DESIGN,
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.CODE_NAVIGATION,
                KellvynStudioCapability.ANDROID_BUILD,
                KellvynStudioCapability.ANDROID_DEVICE_TEST,
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.SECURITY_AUDIT,
                KellvynStudioCapability.DATA_MODELING
            ),
            KellvynSpecialistRole.OPERATIONS_ANALYST to setOf(
                KellvynStudioCapability.AGENT_EVALUATION,
                KellvynStudioCapability.DEVICE_DIAGNOSTICS,
                KellvynStudioCapability.WORKFLOW_AUTOMATION
            ),
            KellvynSpecialistRole.VISUAL_ANALYST to setOf(
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.UI_LAYOUT,
                KellvynStudioCapability.RASTER_IMAGE_EDIT,
                KellvynStudioCapability.VECTOR_GRAPHICS,
                KellvynStudioCapability.SCENE_3D,
                KellvynStudioCapability.CAD_PARAMETRIC
            )
        )

        assertEquals(KellvynSpecialistRole.values().toSet(), expected.keys)

        val readOnlySpecs = KellvynStudioCapability.values().map { capability ->
            KellvynToolSpec(
                name = "read_${capability.name.lowercase()}",
                description = "Read-only capability probe for $capability.",
                capabilities = setOf(capability)
            )
        }
        val restrictedSpecs = KellvynStudioCapability.values().flatMap { capability ->
            KellvynToolRisk.values()
                .filter { it != KellvynToolRisk.READ_ONLY }
                .map { risk ->
                    KellvynToolSpec(
                        name = "blocked_${capability.name.lowercase()}_${risk.name.lowercase()}",
                        description = "Restricted capability probe for $capability.",
                        risk = risk,
                        capabilities = setOf(capability)
                    )
                }
        }
        val unclassified = KellvynToolSpec(
            name = "unclassified_probe",
            description = "A tool without a declared capability."
        )
        val allSpecs = readOnlySpecs + restrictedSpecs + unclassified

        expected.forEach { (role, capabilities) ->
            assertEquals(
                "A matriz de capabilities mudou inesperadamente para $role.",
                capabilities,
                KellvynSpecialistToolPolicy.capabilitiesFor(role)
            )
            val expectedNames = capabilities.map {
                "read_${it.name.lowercase()}"
            }.toSet()
            assertEquals(
                "O filtro READ_ONLY divergiu da matriz para $role.",
                expectedNames,
                KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(role, allSpecs)
            )
            assertEquals(
                "Ferramentas fora da allowlist precisam ser negadas para $role.",
                allSpecs.map { it.name }.toSet() - expectedNames,
                KellvynSpecialistToolPolicy.deniedRequestedNames(
                    role,
                    allSpecs.map { it.name }.toSet(),
                    allSpecs
                )
            )
        }
    }



    @Test
    fun webResearchNetworkReadIsGrantedOnlyToExplicitRolesWhenOwnerSwitchIsEnabled() {
        val networkTool = KellvynToolSpec(
            name = "wikipedia_search",
            description = "Pesquisa na Wikipédia em português.",
            risk = KellvynToolRisk.NETWORK_READ,
            capabilities = setOf(KellvynStudioCapability.WEB_RESEARCH)
        )
        val allSpecs = specs + networkTool

        assertFalse(
            "wikipedia_search" in KellvynSpecialistToolPolicy.permittedToolNames(
                KellvynSpecialistRole.RESEARCHER,
                allSpecs,
                allowNetworkRead = false
            )
        )
        assertTrue(
            "wikipedia_search" in KellvynSpecialistToolPolicy.permittedToolNames(
                KellvynSpecialistRole.RESEARCHER,
                allSpecs,
                allowNetworkRead = true
            )
        )

        assertTrue(
            "O analista de raciocínio recebe pesquisa apenas porque a capability foi concedida.",
            "wikipedia_search" in KellvynSpecialistToolPolicy.permittedToolNames(
                KellvynSpecialistRole.REASONING_ANALYST,
                allSpecs,
                allowNetworkRead = true
            )
        )
        assertFalse(
            "O analista de raciocínio não recebe rede com o controle desligado.",
            "wikipedia_search" in KellvynSpecialistToolPolicy.permittedToolNames(
                KellvynSpecialistRole.REASONING_ANALYST,
                allSpecs,
                allowNetworkRead = false
            )
        )

        KellvynSpecialistRole.values()
            .filter {
                it != KellvynSpecialistRole.RESEARCHER &&
                    it != KellvynSpecialistRole.REASONING_ANALYST
            }
            .forEach { role ->
                assertFalse(
                    "O papel $role não deve receber pesquisa Web.",
                    "wikipedia_search" in KellvynSpecialistToolPolicy.permittedToolNames(
                        role,
                        allSpecs,
                        allowNetworkRead = true
                    )
                )
            }

        assertEquals(
            setOf("wikipedia_search"),
            KellvynSpecialistToolPolicy.deniedRequestedNames(
                KellvynSpecialistRole.RESEARCHER,
                setOf("wikipedia_search"),
                allSpecs,
                allowNetworkRead = false
            )
        )
        assertEquals(
            setOf("wikipedia_search"),
            KellvynSpecialistToolPolicy.deniedRequestedNames(
                KellvynSpecialistRole.REASONING_ANALYST,
                setOf("wikipedia_search"),
                allSpecs,
                allowNetworkRead = false
            )
        )
    }

    @Test
    fun webResearchPermissionDoesNotAuthorizeNetworkReadForAnotherCapability() {
        val misclassified = KellvynToolSpec(
            name = "network_device_scan",
            description = "Tool de rede sem capability de pesquisa.",
            risk = KellvynToolRisk.NETWORK_READ,
            capabilities = setOf(KellvynStudioCapability.DEVICE_DIAGNOSTICS)
        )
        assertFalse(
            "network_device_scan" in KellvynSpecialistToolPolicy.permittedToolNames(
                KellvynSpecialistRole.OPERATIONS_ANALYST,
                specs + misclassified,
                allowNetworkRead = true
            )
        )
    }

}
