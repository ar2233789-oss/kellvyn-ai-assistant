package com.kellvyn.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-checks policy against the actual ToolSpecs registered by the Android runtime.
 * This test detects both missing engines and accidental capability leakage.
 */
@RunWith(AndroidJUnit4::class)
class KellvynAgentToolAvailabilityInstrumentedTest {

    @Test
    fun eachSpecialistReceivesOnlyTheReadOnlyToolsBackedByRegisteredModules() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = KellvynRuntime(context)
        try {
            val registeredSpecs = runtime.createProjectWorkspaceToolRegistry().descriptions()

            val codeNavigation = setOf(
                "workspace_list_projects",
                "workspace_list_files",
                "workspace_read_file",
                "code_find_symbols",
                "code_find_references",
                "code_file_outline"
            )
            val securityAudit = setOf("security_scan_project")
            val dataModeling = setOf("data_inspect_structure")
            val deviceDiagnostics = setOf("device_resource_snapshot")
            val workflowAutomation = setOf("automation_queue_status")

            val expectedByRole = mapOf(
                KellvynSpecialistRole.RESEARCHER to dataModeling,
                KellvynSpecialistRole.CODE_EXPLORER to codeNavigation + dataModeling,
                KellvynSpecialistRole.REVIEWER to codeNavigation + securityAudit + dataModeling,
                KellvynSpecialistRole.TEST_ANALYST to deviceDiagnostics + setOf("android_build_environment_status"),
                KellvynSpecialistRole.REASONING_ANALYST to dataModeling + deviceDiagnostics,
                KellvynSpecialistRole.LEARNING_ANALYST to dataModeling,
                KellvynSpecialistRole.PROGRAMMING_ARCHITECT to codeNavigation + dataModeling,
                KellvynSpecialistRole.MEMORY_ANALYST to setOf("memory_search"),
                KellvynSpecialistRole.SECURITY_ANALYST to codeNavigation + securityAudit + setOf("android_build_environment_status"),
                KellvynSpecialistRole.PLANNING_ANALYST to workflowAutomation + deviceDiagnostics + dataModeling,
                KellvynSpecialistRole.DEBUGGER to codeNavigation + deviceDiagnostics + setOf("android_build_environment_status"),
                KellvynSpecialistRole.AUDITOR to codeNavigation + securityAudit + dataModeling + setOf("android_build_environment_status"),
                KellvynSpecialistRole.OPERATIONS_ANALYST to deviceDiagnostics + workflowAutomation,
                KellvynSpecialistRole.VISUAL_ANALYST to emptySet()
            )

            assertEquals(KellvynSpecialistRole.values().toSet(), expectedByRole.keys)

            expectedByRole.forEach { (role, expectedNames) ->
                assertEquals(
                    "Ferramentas READ_ONLY reais divergiram da matriz para $role.",
                    expectedNames,
                    KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(role, registeredSpecs)
                )
            }

            assertEquals(
                setOf("wikipedia_search"),
                registeredSpecs
                    .filter { it.risk == KellvynToolRisk.NETWORK_READ }
                    .map { it.name }
                    .toSet()
            )
            assertEquals(
                "Somente o Pesquisador pode receber NETWORK_READ para WEB_RESEARCH quando habilitado.",
                setOf("wikipedia_search"),
                KellvynSpecialistToolPolicy.permittedToolNames(
                    KellvynSpecialistRole.RESEARCHER,
                    registeredSpecs,
                    allowNetworkRead = true
                ) - KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(
                    KellvynSpecialistRole.RESEARCHER,
                    registeredSpecs
                )
            )
            KellvynSpecialistRole.values()
                .filter { it != KellvynSpecialistRole.RESEARCHER }
                .forEach { role ->
                    assertEquals(
                        emptySet<String>(),
                        KellvynSpecialistToolPolicy.permittedToolNames(
                            role,
                            registeredSpecs,
                            allowNetworkRead = true
                        ) - KellvynSpecialistToolPolicy.permittedReadOnlyToolNames(role, registeredSpecs)
                    )
                }

            // Only workspace mutations remain outside the specialist registry.
            assertEquals(
                setOf("workspace_write_file", "workspace_delete_file", "wikipedia_search"),
                registeredSpecs
                    .filter { it.risk != KellvynToolRisk.READ_ONLY }
                    .map { it.name }
                    .toSet()
            )
        } finally {
            runtime.close()
        }
    }
}
