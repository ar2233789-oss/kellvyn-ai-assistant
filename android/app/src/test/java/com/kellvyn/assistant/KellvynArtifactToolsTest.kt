package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class KellvynArtifactToolsTest {
    @Test
    fun statusListAndVerifyExposeMetadataWithoutBinaryContents() = runBlocking {
        val root = Files.createTempDirectory("kellvyn-artifact-tools").toFile()
        try {
            val store = KellvynArtifactStore(root)
            val payload = "private rendered bytes".toByteArray()
            val ref = store.put(
                taskId = "render-task-01",
                fileName = "preview.png",
                mediaType = "image/png",
                producerId = "image.studio",
                input = ByteArrayInputStream(payload),
                createdAtMillis = 1_800_000_000_300L
            )
            val tools = KellvynArtifactTools.create(store).associateBy { it.spec.name }

            val status = tools.getValue("artifact_store_status").execute(emptyMap())
            val list = tools.getValue("artifact_list_task").execute(mapOf("task_id" to ref.taskId))
            val verify = tools.getValue("artifact_verify").execute(mapOf("artifact_id" to ref.id))

            assertTrue(status.success)
            assertTrue(status.content.contains("records=1"))
            assertTrue(status.content.contains("binary_payloads_in_model_context=false"))
            assertTrue(list.success)
            assertTrue(list.content.contains("preview.png"))
            assertTrue(verify.success)
            assertTrue(verify.content.contains("integrity=VERIFIED"))
            assertFalse(verify.content.contains(String(payload)))
            assertEquals(
                setOf(KellvynStudioCapability.ARTIFACT_STORAGE),
                tools.getValue("artifact_verify").spec.capabilities
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptedArtifactIsReportedAsFailedIntegrity() = runBlocking {
        val root = Files.createTempDirectory("kellvyn-artifact-tools-corrupt").toFile()
        try {
            val store = KellvynArtifactStore(root)
            val ref = store.put(
                taskId = "render-task-02",
                fileName = "mesh.glb",
                mediaType = "model/gltf-binary",
                producerId = "scene3d",
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                createdAtMillis = 1_800_000_000_301L
            )
            File(root, "objects/\${ref.sha256.take(2)}/\${ref.sha256}.blob")
                .writeBytes(byteArrayOf(3, 2, 1))
            val verify = KellvynArtifactTools.create(store)
                .single { it.spec.name == "artifact_verify" }
                .execute(mapOf("artifact_id" to ref.id))

            assertFalse(verify.success)
            assertTrue(verify.content.contains("integrity=FAILED"))
        } finally {
            root.deleteRecursively()
        }
    }
}
