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

            val writeApproval = approvals.approveWrite(project.id, "README.md", newContent)
            val writeArguments = mapOf(
                "project_id" to project.id,
                "path" to "README.md",
                "content" to newContent,
                "approval_id" to writeApproval
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

            val deleteApproval = approvals.approveDelete(project.id, "README.md")
            val deleteArguments = mapOf(
                "project_id" to project.id,
                "path" to "README.md",
                "approval_id" to deleteApproval
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
