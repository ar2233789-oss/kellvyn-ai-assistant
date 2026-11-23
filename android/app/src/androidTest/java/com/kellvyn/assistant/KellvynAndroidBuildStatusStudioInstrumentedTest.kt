package com.kellvyn.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KellvynAndroidBuildStatusStudioInstrumentedTest {

    @Test
    fun registeredStatusToolReportsCompilerReadinessWithoutBuildingOrInstalling() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = KellvynRuntime(context)
        try {
            val registry = runtime.createProjectWorkspaceToolRegistry()
            val tool = registry.find("android_build_environment_status")
            val academicTool = registry.find(KellvynAcademicResearchStudio.TOOL_NAME)

            assertNotNull(tool)
            assertNotNull(academicTool)
            assertEquals(KellvynToolRisk.NETWORK_READ, academicTool!!.spec.risk)
            assertTrue(academicTool.spec.capabilities.contains(KellvynStudioCapability.WEB_RESEARCH))
            assertEquals(KellvynToolRisk.READ_ONLY, tool!!.spec.risk)
            assertEquals(
                setOf(KellvynStudioCapability.ANDROID_BUILD_DIAGNOSTICS),
                tool.spec.capabilities
            )
            assertTrue(KellvynAndroidBuildStatusStudio.MANIFEST.capabilities.containsAll(tool.spec.capabilities))

            val result = tool.execute(emptyMap())

            assertTrue(result.success)
            assertTrue(result.content.contains("compiler=ECJ_D8_LOCAL"))
            assertTrue(result.content.contains("support_profile=JAVA_ANDROID_LIMITED"))
            assertTrue(result.content.contains("compile_sdk=35"))
            assertTrue(result.content.contains("apk_installation=not_performed"))
            assertTrue(result.content.contains("device_test_status=not_provided_by_this_diagnostic"))
            assertFalse(result.content.contains(context.filesDir.absolutePath))
        } finally {
            runtime.close()
        }
    }


    @Test
    fun localKnowledgeToolSearchesProjectAndDoesNotReturnSecretBearingFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.createProject(
            name = "Knowledge Retrieval Instrumented " + System.currentTimeMillis(),
            description = "Fixture de busca local.",
            files = listOf(
                ProjectFileInput(
                    path = "docs/architecture.md",
                    content = """
                        A arquitetura guarda checkpoints e permite retomada do treinamento interrompido.
                        O índice local recupera evidências por relevância textual.
                    """.trimIndent()
                ),
                ProjectFileInput(
                    path = "docs/internal.md",
                    content = """api_key = "sk-12345678901234567890" """
                )
            )
        )
        val runtime = KellvynRuntime(context)
        try {
            val registry = runtime.createProjectWorkspaceToolRegistry()
            val tool = requireNotNull(
                registry.find(KellvynProjectKnowledgeSearchTool.TOOL_NAME)
            )
            assertEquals(KellvynToolRisk.READ_ONLY, tool.spec.risk)
            assertEquals(
                setOf(KellvynStudioCapability.CODE_NAVIGATION),
                tool.spec.capabilities
            )

            val result = tool.execute(
                mapOf(
                    "project_id" to project.id,
                    "query" to "retomada checkpoint"
                )
            )

            assertTrue(result.success)
            assertTrue(result.content.contains("docs/architecture.md"))
            assertTrue(result.content.contains("checkpoints"))
            assertFalse(result.content.contains("docs/internal.md"))
            assertFalse(result.content.contains("sk-12345678901234567890"))
        } finally {
            runtime.close()
            workspace.deleteProject(project.id)
        }
    }

    @Test
    fun statusToolRejectsArgumentsThatCouldSelectOrModifyAnArtifact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = KellvynRuntime(context)
        try {
            val tool = requireNotNull(
                runtime.createProjectWorkspaceToolRegistry()
                    .find("android_build_environment_status")
            )

            val result = tool.execute(mapOf("project_id" to "some-project"))

            assertFalse(result.success)
            assertTrue(result.content.contains("não aceita argumentos"))
        } finally {
            runtime.close()
        }
    }
}
