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
        val generatedTimelineSpec = KellvynToolSpec(
            name = "animation_render_spritesheet",
            description = "Creates a new private animation spritesheet.",
            requiredArguments = setOf("task_id", "timeline_json"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            capabilities = setOf(
                KellvynStudioCapability.ANIMATION,
                KellvynStudioCapability.ARTIFACT_STORAGE
            )
        )
        assertTrue(
            auth.authorize(
                generatedTimelineSpec,
                mapOf("task_id" to "animation-task-1", "timeline_json" to "{}"),
                "Renderizar animação"
            )
        )
        val vectorSpec = KellvynToolSpec(
            name = "vector_render_artwork",
            description = "Creates an SVG and PNG artwork pair.",
            requiredArguments = setOf("task_id", "design_json"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            capabilities = setOf(
                KellvynStudioCapability.VECTOR_GRAPHICS,
                KellvynStudioCapability.ARTIFACT_STORAGE
            )
        )
        assertTrue(
            auth.authorize(
                vectorSpec,
                mapOf("task_id" to "vector-task-1", "design_json" to "{}"),
                "Criar arte vetorial"
            )
        )
        assertFalse(auth.authorize(validSpec, mapOf("artifact_id" to "a".repeat(64)), "Redimensionar"))
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
