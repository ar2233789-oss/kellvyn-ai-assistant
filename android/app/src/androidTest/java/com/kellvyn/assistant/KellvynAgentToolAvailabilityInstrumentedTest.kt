package com.kellvyn.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

            val artifactToolNames = registeredSpecs
                .filter { KellvynStudioCapability.ARTIFACT_STORAGE in it.capabilities }
                .map { it.name }
                .toSet()
            assertEquals(
                setOf(
                    "artifact_store_status", "artifact_list_task", "artifact_verify",
                    "image_inspect", "image_resize"
                ),
                artifactToolNames
            )
            assertTrue(registeredSpecs.any {
                KellvynStudioCapability.RASTER_IMAGE_EDIT in it.capabilities
            })
            assertFalse(KellvynStudioCapability.IMAGE_GENERATION in registeredSpecs
                .flatMap { it.capabilities }.toSet())
            assertEquals(
                KellvynToolRisk.READ_ONLY,
                registeredSpecs.single { it.name == KellvynInvisibleStudioCatalogTool.TOOL_NAME }.risk
            )
            assertTrue(registeredSpecs.any {
                KellvynStudioCapability.ANDROID_BUILD_DIAGNOSTICS in it.capabilities
            })
            assertFalse(registeredSpecs.any {
                KellvynStudioCapability.ANDROID_BUILD in it.capabilities
            })
            assertTrue(registeredSpecs.any {
                KellvynStudioCapability.TASK_QUEUE_DIAGNOSTICS in it.capabilities
            })
            assertTrue(registeredSpecs.any {
                KellvynStudioCapability.BOT_DIAGNOSTICS in it.capabilities
            })
            val workflowToolNames = registeredSpecs
                .filter { KellvynStudioCapability.WORKFLOW_AUTOMATION in it.capabilities }
                .map { it.name }
                .toSet()
            assertEquals(
                setOf("automation_schedule_resource_snapshot", "automation_cancel_queued_task"),
                workflowToolNames
            )
            assertEquals(
                KellvynToolRisk.READ_ONLY,
                registeredSpecs.single { it.name == "automation_task_status" }.risk
            )
            val notImplementedYet = setOf(
                KellvynStudioCapability.IMAGE_GENERATION,
                KellvynStudioCapability.ANIMATION,
                KellvynStudioCapability.VIDEO_GENERATION,
                KellvynStudioCapability.AUDIO_GENERATION,
                KellvynStudioCapability.GAME_INTERACTION,
                KellvynStudioCapability.APP_CONTROL,
                KellvynStudioCapability.SCENE_3D,
                KellvynStudioCapability.CODE_EXECUTION
            )
            assertTrue(registeredSpecs.none { spec ->
                spec.capabilities.any { it in notImplementedYet }
            })

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
            val workflowAutomation = setOf("automation_queue_status", KellvynBotOperationsStudio.HEALTH_TOOL)
            val agentDesign = setOf(
                KellvynAgentCreationStudio.CATALOG_TOOL,
                KellvynAgentCreationStudio.INSPECT_TOOL,
                KellvynAgentCreationStudio.PROFILE_HISTORY_TOOL,
                KellvynAgentCreationStudio.PROFILE_PENDING_DIFF_TOOL,
                KellvynAgentCreationStudio.PROFILE_REVISION_PREVIEW_TOOL
            )
            val agentLabTools = setOf(
                KellvynAgentLabStudio.ENVIRONMENT_STATUS_TOOL,
                KellvynAgentLabStudio.TRACE_LIST_TOOL,
                KellvynAgentLabStudio.TRACE_INSPECT_TOOL,
                KellvynAgentLabStudio.TRACE_COMPARE_TOOL,
                KellvynAgentLabStudio.SPECIALIST_MATRIX_TOOL,
                KellvynAgentLabStudio.DELEGATION_METRICS_TOOL,
                KellvynAgentLabStudio.BENCHMARK_READINESS_TOOL
            )
            val agentEvaluation = setOf(
                KellvynAgentCreationStudio.VALIDATE_TOOL,
                KellvynAgentCreationStudio.BENCHMARK_CATALOG_TOOL
            ) + agentLabTools

            val expectedByRole = mapOf(
                KellvynSpecialistRole.RESEARCHER to dataModeling,
                KellvynSpecialistRole.CODE_EXPLORER to codeNavigation + dataModeling,
                KellvynSpecialistRole.REVIEWER to codeNavigation + securityAudit + dataModeling + agentEvaluation,
                KellvynSpecialistRole.TEST_ANALYST to deviceDiagnostics +
                    setOf("android_build_environment_status") + agentEvaluation,
                KellvynSpecialistRole.REASONING_ANALYST to dataModeling + deviceDiagnostics +
                    setOf(KellvynAgentLabStudio.ENVIRONMENT_STATUS_TOOL),
                KellvynSpecialistRole.LEARNING_ANALYST to dataModeling + agentEvaluation,
                KellvynSpecialistRole.PROGRAMMING_ARCHITECT to codeNavigation + dataModeling + agentDesign + agentEvaluation,
                KellvynSpecialistRole.MEMORY_ANALYST to setOf("memory_search"),
                KellvynSpecialistRole.SECURITY_ANALYST to codeNavigation + securityAudit + setOf("android_build_environment_status") + agentEvaluation,
                KellvynSpecialistRole.PLANNING_ANALYST to workflowAutomation + deviceDiagnostics + dataModeling + agentDesign + agentEvaluation,
                KellvynSpecialistRole.DEBUGGER to codeNavigation + deviceDiagnostics + setOf("android_build_environment_status") + agentEvaluation,
                KellvynSpecialistRole.AUDITOR to codeNavigation + securityAudit + dataModeling +
                    setOf("android_build_environment_status") + agentDesign + agentEvaluation,
                KellvynSpecialistRole.OPERATIONS_ANALYST to deviceDiagnostics + workflowAutomation + agentEvaluation,
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
                setOf(
                    "workspace_write_file", "workspace_delete_file", "wikipedia_search",
                    "image_resize", "automation_schedule_resource_snapshot", "automation_cancel_queued_task"
                ),
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
