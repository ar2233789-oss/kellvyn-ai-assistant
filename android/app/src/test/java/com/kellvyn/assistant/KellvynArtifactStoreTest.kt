package com.kellvyn.assistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class KellvynArtifactStoreTest {
    @Test
    fun storesTaskScopedRecordsAndDeduplicatesIdenticalBinaryContent() {
        val root = Files.createTempDirectory("kellvyn-artifacts").toFile()
        try {
            val store = KellvynArtifactStore(root)
            val bytes = "PNG fixture bytes".toByteArray()
            val first = store.put(
                taskId = "task-image-01",
                fileName = "preview.png",
                mediaType = "image/png",
                producerId = "image.studio",
                input = ByteArrayInputStream(bytes),
                createdAtMillis = 1_800_000_000_000L
            )
            val second = store.put(
                taskId = "task-animation-02",
                fileName = "frame.png",
                mediaType = "image/png",
                producerId = "animation",
                input = ByteArrayInputStream(bytes),
                createdAtMillis = 1_800_000_000_001L
            )

            assertTrue(store.verify(first.id))
            assertTrue(store.verify(second.id))
            assertEquals(1, store.listForTask("task-image-01").size)
            assertEquals(1, store.listForTask("task-animation-02").size)
            assertEquals(bytes.size.toLong(), store.storedBytes())
            assertFalse(first.id == second.id)

            val output = ByteArrayOutputStream()
            val copied = store.copyVerifiedTo(first.id, output)
            assertEquals(first, copied)
            assertArrayEquals(bytes, output.toByteArray())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptedBinaryFailsHashVerificationAndCopy() {
        val root = Files.createTempDirectory("kellvyn-artifacts-corrupt").toFile()
        try {
            val store = KellvynArtifactStore(root)
            val ref = store.put(
                taskId = "task-corrupt",
                fileName = "result.glb",
                mediaType = "model/gltf-binary",
                producerId = "scene3d",
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                createdAtMillis = 1_800_000_000_100L
            )
            val objectFile = File(root, "objects/\${ref.sha256.take(2)}/\${ref.sha256}.blob")
            objectFile.writeBytes(byteArrayOf(4, 3, 2, 1))

            assertFalse(store.verify(ref.id))
            val result = runCatching {
                store.copyVerifiedTo(ref.id, ByteArrayOutputStream())
            }
            assertTrue(result.isFailure)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsafeMetadataAndOversizedContentWithoutPublishingRecords() {
        val root = Files.createTempDirectory("kellvyn-artifacts-limits").toFile()
        try {
            val store = KellvynArtifactStore(root, maxArtifactBytes = 4L, maxStoreBytes = 8L)

            val unsafeName = runCatching {
                store.put(
                    "task-safe",
                    "../outside.png",
                    "image/png",
                    "image.studio",
                    ByteArrayInputStream(byteArrayOf(1)),
                    1_800_000_000_200L
                )
            }
            val oversized = runCatching {
                store.put(
                    "task-safe",
                    "large.bin",
                    "application/octet-stream",
                    "image.studio",
                    ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                    1_800_000_000_201L
                )
            }

            assertTrue(unsafeName.isFailure)
            assertTrue(oversized.isFailure)
            assertTrue(store.listForTask("task-safe").isEmpty())
            assertEquals(0L, store.storedBytes())
        } finally {
            root.deleteRecursively()
        }
    }
}
