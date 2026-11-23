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
            val developerTool = registry.find(KellvynDeveloperKnowledgeStudio.TOOL_NAME)
            assertNotNull(developerTool)
            assertEquals(KellvynToolRisk.NETWORK_READ, developerTool!!.spec.risk)

            assertTrue(academicTool.spec.capabilities.contains(KellvynStudioCapability.WEB_RESEARCH))
            val localHistory = listOf(
                KellvynAgentMessage(
                    role = KellvynMessageRole.USER,
                    trust = KellvynTrustLevel.USER_PROVIDED,
                    source = "user",
                    content = "Pesquise conteúdo do projeto sobre memória e checkpoints"
                )
            )
            val academicHistory = listOf(
                KellvynAgentMessage(
                    role = KellvynMessageRole.USER,
                    trust = KellvynTrustLevel.USER_PROVIDED,
                    source = "user",
                    content = "Pesquisar artigos científicos sobre modelos de linguagem"
                )
            )
            assertTrue(
                registry.descriptionsForPrompt(localHistory, 8)
                    .any { it.name == KellvynProjectKnowledgeSearchTool.TOOL_NAME }
            )
            assertTrue(
                registry.descriptionsForPrompt(academicHistory, 8)
                    .any { it.name == KellvynAcademicResearchStudio.TOOL_NAME }
            )
            val developerHistory = listOf(
                KellvynAgentMessage(
                    role = KellvynMessageRole.USER,
                    trust = KellvynTrustLevel.USER_PROVIDED,
                    source = "user",
                    content = "Como resolver IllegalStateException no Kotlin com stacktrace?"
                )
            )
            assertTrue(
                registry.descriptionsForPrompt(developerHistory, 8)
                    .any { it.name == KellvynDeveloperKnowledgeStudio.TOOL_NAME }
            )


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
            files = buildList {
                add(
                    ProjectFileInput(
                        path = "000-ignored.bin",
                        content = "Arquivo de formato não suportado."
                    )
                )
                repeat(60) { index ->
                    val number = index.toString().padStart(2, '0')
                    add(
                        ProjectFileInput(
                            path = "bulk/decoy-$number.md",
                            content = "Notas sem relação com checkpoint ou recuperação."
                        )
                    )
                }
                add(
                    ProjectFileInput(
                        path = "docs/checkpoint-recovery.md",
                        content = """
                            O checkpoint local permite recuperação do treinamento interrompido.
                            O histórico registra o estado e a origem de cada etapa.
                        """.trimIndent()
                    )
                )
                add(
                    ProjectFileInput(
                        path = "docs/internal.md",
                        content = """client_secret = "sk-12345678901234567890" """
                    )
                )
            }
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
                    "query" to "checkpoint recovery"
                )
            )
            val secretResult = tool.execute(
                mapOf(
                    "project_id" to project.id,
                    "query" to "internal settings"
                )
            )

            assertTrue(result.success)
            assertTrue(result.content.contains("docs/checkpoint-recovery.md"))
            assertTrue(result.content.contains("checkpoint local"))
            assertTrue(secretResult.success)
            assertFalse(secretResult.content.contains("docs/internal.md"))
            assertFalse(secretResult.content.contains("sk-12345678901234567890"))
            assertTrue(secretResult.content.contains("Arquivos analisados: 59; ignorados: 4"))
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
