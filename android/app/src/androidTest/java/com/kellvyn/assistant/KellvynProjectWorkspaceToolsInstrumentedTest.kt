package com.kellvyn.assistant

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynProjectWorkspaceToolsInstrumentedTest {

    @Test
    fun specialistReadsRealProjectFilesButCannotSeeWorkspaceMutationTools() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.createProject(
            name = "Specialist Workspace Contract ${System.currentTimeMillis()}",
            description = "Temporary specialist fixture.",
            files = listOf(ProjectFileInput("src/Main.java", "class Main { int sum(int a, int b) { return a + b; } }"))
        )
        try {
            val approvals = KellvynProjectWriteApprovalGate()
            val registry = KellvynToolRegistry(KellvynProjectWorkspaceTools.create(workspace, approvals))
            val inference = WorkspaceAgentFakeEngine(
                KellvynModelDecision.Delegate(
                    role = KellvynSpecialistRole.CODE_EXPLORER,
                    task = "Leia src/Main.java e identifique a função.",
                    allowedTools = setOf("workspace_read_file", "workspace_write_file", "workspace_delete_file")
                ),
                KellvynModelDecision.CallTool(
                    "workspace_read_file",
                    mapOf("project_id" to project.id, "path" to "src/Main.java")
                ),
                KellvynModelDecision.Final("A função sum soma dois inteiros."),
                KellvynModelDecision.Final("O especialista confirmou a função sum.")
            )
            val agent = KellvynAgent(
                inference = inference,
                tools = registry,
                authorization = KellvynProjectWorkspaceTools.authorization(approvals),
                config = KellvynAgentConfig(maxDelegations = 1),
                skillRegistry = KellvynSkillRegistry.empty()
            )

            val result = agent.run("Revise a função do arquivo src/Main.java.")

            assertTrue(result is KellvynAgentResult.Completed)
            assertTrue((result as KellvynAgentResult.Completed).answer.contains("sum"))
            assertEquals(4, inference.toolsSeen.size)
            assertTrue(inference.toolsSeen[1].any { it.name == "workspace_read_file" })
            assertFalse(inference.toolsSeen[1].any { it.name == "workspace_write_file" })
            assertFalse(inference.toolsSeen[1].any { it.name == "workspace_delete_file" })
            assertTrue(inference.histories[2].any {
                it.source == "tool:workspace_read_file" &&
                    it.trust == KellvynTrustLevel.UNTRUSTED &&
                    it.content.contains("return a + b")
            })
            assertTrue(inference.histories[3].any {
                it.source == "specialist:code_explorer" &&
                    it.trust == KellvynTrustLevel.UNTRUSTED
            })
        } finally {
            workspace.deleteProject(project.id)
        }
    }

    @Test
    fun coordinatorWritesThroughRealToolOnlyWithExactOwnerApproval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.createProject(
            name = "Coordinator Workspace Contract ${System.currentTimeMillis()}",
            description = "Temporary coordinator fixture.",
            files = listOf(ProjectFileInput("README.md", "Original"))
        )
        try {
            val approvals = KellvynProjectWriteApprovalGate()
            val registry = KellvynToolRegistry(KellvynProjectWorkspaceTools.create(workspace, approvals))
            val newContent = "Written by the approved coordinator tool."
            // The trusted application records the approval; inference receives no
            // random approval token and can only propose the exact approved content.
            approvals.approveWrite(project.id, "README.md", newContent)
            val inference = WorkspaceAgentFakeEngine(
                KellvynModelDecision.CallTool(
                    "workspace_write_file",
                    mapOf(
                        "project_id" to project.id,
                        "path" to "README.md",
                        "content" to newContent
                    )
                ),
                KellvynModelDecision.Final("Arquivo atualizado e verificado.")
            )
            val agent = KellvynAgent(
                inference = inference,
                tools = registry,
                authorization = KellvynProjectWorkspaceTools.authorization(approvals),
                skillRegistry = KellvynSkillRegistry.empty()
            )

            val result = agent.run("Atualize o README aprovado.")
            assertTrue(result is KellvynAgentResult.Completed)
            assertEquals(newContent, workspace.readFile(project.id, "README.md"))
            assertTrue(inference.toolsSeen.first().any { it.name == "workspace_write_file" })
        } finally {
            workspace.deleteProject(project.id)
        }
    }

    private class WorkspaceAgentFakeEngine(
        vararg decisions: KellvynModelDecision
    ) : KellvynInferenceEngine {
        private val pending = ArrayDeque(decisions.toList())
        val histories = mutableListOf<List<KellvynAgentMessage>>()
        val toolsSeen = mutableListOf<List<KellvynToolSpec>>()

        override suspend fun next(
            history: List<KellvynAgentMessage>,
            availableTools: List<KellvynToolSpec>
        ): KellvynModelDecision {
            histories += history
            toolsSeen += availableTools
            return pending.removeFirst()
        }
    }

    @Test
    fun mainAgentCanReadAndOnlyMutateExactFilesAfterOwnerApproval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val project = workspace.createProject(
            name = "Agent Workspace Contract ${System.currentTimeMillis()}",
            description = "Temporary instrumentation fixture.",
            files = listOf(ProjectFileInput("README.md", "Original content"))
        )
        val approvals = KellvynProjectWriteApprovalGate()
        try {
            val registry = KellvynToolRegistry(KellvynProjectWorkspaceTools.create(workspace, approvals))
            val listed = registry.find("workspace_list_files")!!.execute(
                mapOf("project_id" to project.id)
            )
            assertTrue(listed.success)
            assertTrue(listed.content.contains("README.md"))

            val read = registry.find("workspace_read_file")!!.execute(
                mapOf("project_id" to project.id, "path" to "README.md")
            )
            assertTrue(read.success)
            assertTrue(read.content.contains("Original content"))

            val writeTool = registry.find("workspace_write_file")!!
            val newContent = "Updated only after explicit approval."
            val denied = writeTool.execute(
                mapOf(
                    "project_id" to project.id,
                    "path" to "README.md",
                    "content" to newContent,
                    "approval_id" to "0".repeat(48)
                )
            )
            assertFalse(denied.success)
            assertEquals("Original content", workspace.readFile(project.id, "README.md"))

            // The app records approval after owner confirmation; the model does not need
            // to invent or reproduce the random approval identifier.
            approvals.approveWrite(project.id, "README.md", newContent)
            val writeArguments = mapOf(
                "project_id" to project.id,
                "path" to "README.md",
                "content" to newContent
            )
            val authorization = KellvynProjectWorkspaceTools.authorization(approvals)
            assertTrue(authorization.authorize(writeTool.spec, writeArguments, "update approved file"))
            val written = writeTool.execute(writeArguments)
            assertTrue(written.success)
            assertEquals(newContent, workspace.readFile(project.id, "README.md"))
            assertFalse(writeTool.execute(writeArguments).success)

            val deleteTool = registry.find("workspace_delete_file")!!
            val specialistTools = registry.readOnlySubset(registry.names())
            assertTrue(specialistTools.names().contains("workspace_read_file"))
            assertTrue(specialistTools.names().contains("workspace_list_files"))
            assertFalse(specialistTools.names().contains("workspace_write_file"))
            assertFalse(specialistTools.names().contains("workspace_delete_file"))

            approvals.approveDelete(project.id, "README.md")
            val deleteArguments = mapOf(
                "project_id" to project.id,
                "path" to "README.md"
            )
            assertTrue(authorization.authorize(deleteTool.spec, deleteArguments, "delete approved file"))
            val deleted = deleteTool.execute(deleteArguments)
            assertTrue(deleted.success)
            assertFalse(workspace.listFiles(project.id).contains("README.md"))
            assertFalse(deleteTool.execute(deleteArguments).success)
        } finally {
            workspace.deleteProject(project.id)
        }
    }
}
