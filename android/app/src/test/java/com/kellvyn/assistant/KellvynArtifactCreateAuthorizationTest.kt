package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynArtifactCreateAuthorizationTest {
    @Test
    fun appendOnlyArtifactCreationRequiresArtifactCapabilityAndSourceReference() = runBlocking {
        val auth = KellvynProjectWorkspaceTools.authorization(KellvynProjectWriteApprovalGate())
        val validSpec = KellvynToolSpec(
            name = "image_resize",
            description = "Creates a new immutable image artifact.",
            requiredArguments = setOf("artifact_id", "task_id"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            capabilities = setOf(
                KellvynStudioCapability.RASTER_IMAGE_EDIT,
                KellvynStudioCapability.ARTIFACT_STORAGE
            )
        )
        val validArguments = mapOf(
            "artifact_id" to "a".repeat(64),
            "task_id" to "image-task-1"
        )

        assertTrue(auth.authorize(validSpec, validArguments, "Redimensionar imagem"))
        assertFalse(auth.authorize(validSpec, mapOf("task_id" to "image-task-1"), "Redimensionar"))
    }

    @Test
    fun appendOnlyArtifactCreationCannotWriteToWorkspaceOrWithoutArtifactCapability() = runBlocking {
        val auth = KellvynProjectWorkspaceTools.authorization(KellvynProjectWriteApprovalGate())
        val missingCapability = KellvynToolSpec(
            name = "untrusted_writer",
            description = "An untrusted writer.",
            requiredArguments = setOf("artifact_id", "task_id"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
        )
        val arguments = mapOf(
            "artifact_id" to "b".repeat(64),
            "task_id" to "task-2"
        )

        assertFalse(auth.authorize(missingCapability, arguments, "Modify project"))
    }
}
