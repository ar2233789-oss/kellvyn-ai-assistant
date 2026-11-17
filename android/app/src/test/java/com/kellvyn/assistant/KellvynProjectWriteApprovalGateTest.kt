package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynProjectWriteApprovalGateTest {
    @Test
    fun writeApprovalIsBoundToProjectPathAndExactContentAndIsSingleUse() = runBlocking {
        val gate = KellvynProjectWriteApprovalGate()
        val approvedContent = "package example; class Main {}"
        val id = gate.approveWrite("sample-app", "src/Main.java", approvedContent)
        val authorization = KellvynProjectWorkspaceTools.authorization(gate)
        val spec = KellvynToolSpec(
            name = "workspace_write_file",
            description = "write",
            requiredArguments = setOf("project_id", "path", "content", "approval_id"),
            risk = KellvynToolRisk.REVERSIBLE_WRITE
        )

        assertFalse(authorization.authorize(
            spec,
            mapOf(
                "project_id" to "another-project",
                "path" to "src/Main.java",
                "content" to approvedContent,
                "approval_id" to id
            ),
            "write"
        ))
        assertFalse(authorization.authorize(
            spec,
            mapOf(
                "project_id" to "sample-app",
                "path" to "src/Main.java",
                "content" to approvedContent + "// changed",
                "approval_id" to id
            ),
            "write"
        ))

        val exact = mapOf(
            "project_id" to "sample-app",
            "path" to "src/Main.java",
            "content" to approvedContent,
            "approval_id" to id
        )
        assertTrue(authorization.authorize(spec, exact, "write"))
        assertTrue(gate.consumeWrite(exact))
        assertFalse(authorization.authorize(spec, exact, "write"))
        assertFalse(gate.consumeWrite(exact))
    }

    @Test
    fun deleteApprovalCannotAuthorizeWriteAndMustMatchExactPath() = runBlocking {
        val gate = KellvynProjectWriteApprovalGate()
        val id = gate.approveDelete("sample-app", "README.md")
        val authorization = KellvynProjectWorkspaceTools.authorization(gate)
        val deleteSpec = KellvynToolSpec(
            name = "workspace_delete_file",
            description = "delete",
            requiredArguments = setOf("project_id", "path", "approval_id"),
            risk = KellvynToolRisk.DESTRUCTIVE
        )
        val writeSpec = KellvynToolSpec(
            name = "workspace_write_file",
            description = "write",
            requiredArguments = setOf("project_id", "path", "content", "approval_id"),
            risk = KellvynToolRisk.REVERSIBLE_WRITE
        )
        val exactDelete = mapOf(
            "project_id" to "sample-app",
            "path" to "README.md",
            "approval_id" to id
        )
        assertFalse(authorization.authorize(
            writeSpec,
            exactDelete + ("content" to "replacement"),
            "write"
        ))
        assertFalse(authorization.authorize(
            deleteSpec,
            exactDelete + ("path" to "src/Main.java"),
            "delete"
        ))
        assertTrue(authorization.authorize(deleteSpec, exactDelete, "delete"))
        assertTrue(gate.consumeDelete(exactDelete))
        assertFalse(gate.consumeDelete(exactDelete))
    }

    @Test
    fun expiredApprovalIsRejected() {
        var now = 1_800_000_000_000L
        val gate = KellvynProjectWriteApprovalGate(
            clock = { now },
            ttlMillis = 1_000L
        )
        val id = gate.approveWrite("sample-app", "README.md", "approved")
        val arguments = mapOf(
            "project_id" to "sample-app",
            "path" to "README.md",
            "content" to "approved",
            "approval_id" to id
        )
        now += 1_001L
        assertFalse(gate.authorizesWrite(arguments))
        assertFalse(gate.consumeWrite(arguments))
    }

    @Test
    fun unsafePathsAndOversizedApprovalPayloadsAreRefused() {
        val gate = KellvynProjectWriteApprovalGate()
        val invalidPaths = listOf(
            "../outside.txt",
            "/absolute.txt",
            "src/../../outside.txt",
            "C:/outside.txt",
            "src\\Main.java",
            ".git/config"
        )
        invalidPaths.forEach { path ->
            runCatching { gate.approveWrite("sample-app", path, "safe") }
                .onSuccess { throw AssertionError("Path should have been rejected: $path") }
        }
        runCatching {
            gate.approveWrite(
                "sample-app",
                "large.txt",
                "x".repeat(KellvynProjectWriteApprovalGate.MAX_CONTENT_CHARS + 1)
            )
        }.onSuccess { throw AssertionError("Oversized content should have been rejected.") }
    }

    @Test
    fun authorizationDoesNotGrantNetworkOrDeviceControl() = runBlocking {
        val gate = KellvynProjectWriteApprovalGate()
        val authorization = KellvynProjectWorkspaceTools.authorization(gate)
        val read = KellvynToolSpec(
            name = "workspace_read_file",
            description = "read"
        )
        val network = KellvynToolSpec(
            name = "network_fetch",
            description = "network",
            risk = KellvynToolRisk.NETWORK_READ
        )
        val device = KellvynToolSpec(
            name = "device_control",
            description = "device",
            risk = KellvynToolRisk.DEVICE_CONTROL
        )
        assertTrue(authorization.authorize(read, emptyMap(), "read"))
        assertFalse(authorization.authorize(network, emptyMap(), "fetch"))
        assertFalse(authorization.authorize(device, emptyMap(), "control"))
    }
}
