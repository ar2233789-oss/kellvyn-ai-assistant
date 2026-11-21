package com.kellvyn.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class KellvynImageStudioInstrumentedTest {
    @Test
    fun inspectAndResizeCreatesNewVerifiedArtifactWithoutChangingSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storeRoot = File(context.cacheDir, "image-studio-instrumented-${System.nanoTime()}")
        val store = KellvynArtifactStore(storeRoot)
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        val sourceBytes = ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        bitmap.recycle()
        val source = store.put(
            taskId = "image-studio-source",
            fileName = "fixture.png",
            mediaType = "image/png",
            producerId = "test.fixture",
            input = ByteArrayInputStream(sourceBytes)
        )
        val module = KellvynImageStudio.definition(context, store).factory.create()

        try {
            assertEquals(
                setOf(KellvynImageStudio.INSPECT_TOOL, KellvynImageStudio.RESIZE_TOOL),
                module.tools.map { it.spec.name }.toSet()
            )
            val inspect = module.tools.single { it.spec.name == KellvynImageStudio.INSPECT_TOOL }
                .execute(mapOf("artifact_id" to source.id))
            assertTrue(inspect.success)
            assertTrue(inspect.content.contains("width=80"))
            assertTrue(inspect.content.contains("height=40"))

            val resize = module.tools.single { it.spec.name == KellvynImageStudio.RESIZE_TOOL }
                .execute(
                    mapOf(
                        "artifact_id" to source.id,
                        "task_id" to "image-studio-output",
                        "width" to "40",
                        "height" to "20",
                        "format" to "png"
                    )
                )
            assertTrue(resize.success)
            val outputId = Regex("(?m)^artifact_id=([a-f0-9]{64})$")
                .find(resize.content)?.groupValues?.get(1)
            assertNotNull(outputId)
            assertTrue(store.verify(requireNotNull(outputId)))
            assertTrue(store.verify(source.id))

            val outputFile = File.createTempFile("image-studio-output-", ".png", context.cacheDir)
            try {
                FileOutputStream(outputFile).use { store.copyVerifiedTo(requireNotNull(outputId), it) }
                val resultBitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                assertNotNull(resultBitmap)
                assertEquals(40, resultBitmap!!.width)
                assertEquals(20, resultBitmap.height)
                resultBitmap.recycle()
            } finally {
                outputFile.delete()
            }
        } finally {
            module.close()
            storeRoot.deleteRecursively()
        }
    }

    @Test
    fun rasterToolsRejectWrongArtifactTypeAndExcessiveOutputDimensions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storeRoot = File(context.cacheDir, "image-studio-limits-${System.nanoTime()}")
        val store = KellvynArtifactStore(storeRoot)
        val source = store.put(
            taskId = "image-studio-limits",
            fileName = "notes.txt",
            mediaType = "text/plain",
            producerId = "test.fixture",
            input = ByteArrayInputStream("not an image".toByteArray())
        )
        val module = KellvynImageStudio.definition(context, store).factory.create()
        try {
            val wrongType = module.tools.single { it.spec.name == KellvynImageStudio.RESIZE_TOOL }
                .execute(
                    mapOf(
                        "artifact_id" to source.id,
                        "task_id" to "image-studio-output",
                        "width" to "40",
                        "height" to "20",
                        "format" to "png"
                    )
                )
            assertFalse(wrongType.success)

            val excessiveSize = module.tools.single { it.spec.name == KellvynImageStudio.RESIZE_TOOL }
                .execute(
                    mapOf(
                        "artifact_id" to source.id,
                        "task_id" to "image-studio-output",
                        "width" to "8192",
                        "height" to "8192",
                        "format" to "png"
                    )
                )
            assertFalse(excessiveSize.success)
            assertTrue(store.listForTask("image-studio-output").isEmpty())
        } finally {
            module.close()
            storeRoot.deleteRecursively()
        }
    }
}
