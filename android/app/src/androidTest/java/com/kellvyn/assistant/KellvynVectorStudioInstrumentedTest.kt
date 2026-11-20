package com.kellvyn.assistant

import android.content.Context
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class KellvynVectorStudioInstrumentedTest {
    @Test
    fun createsEditableSvgAndVerifiedPngPreview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "vector-studio-test-${System.nanoTime()}")
        val store = KellvynArtifactStore(root)
        val module = KellvynVectorStudio.definition(context, store).factory.create()
        val design = """
            {
              "width": 128,
              "height": 96,
              "background": "#101820",
              "shapes": [
                {"type":"rect","x":8,"y":8,"width":112,"height":80,"radius":12,"fill":"#1B4332","stroke":"#52B788","stroke_width":2},
                {"type":"circle","cx":64,"cy":42,"radius":18,"fill":"#F4A261"},
                {"type":"line","x1":18,"y1":78,"x2":110,"y2":78,"stroke":"#FFFFFF","stroke_width":2},
                {"type":"text","x":44,"y":68,"text":"KN","font_size":16,"fill":"#FFFFFF"}
              ]
            }
        """.trimIndent()

        try {
            val tool = module.tools.single { it.spec.name == KellvynVectorStudio.RENDER_TOOL }
            val result = tool.execute(
                mapOf("task_id" to "vector-test-1", "design_json" to design)
            )
            assertTrue(result.success)
            assertTrue(result.content.contains("ARTE VETORIAL CRIADA"))
            val svgId = Regex("(?m)^svg_artifact_id=([a-f0-9]{64})$")
                .find(result.content)?.groupValues?.get(1)
            val pngId = Regex("(?m)^png_artifact_id=([a-f0-9]{64})$")
                .find(result.content)?.groupValues?.get(1)
            assertNotNull(svgId)
            assertNotNull(pngId)
            assertTrue(store.verify(requireNotNull(svgId)))
            assertTrue(store.verify(requireNotNull(pngId)))

            val svgFile = File.createTempFile("vector-source-", ".svg", context.cacheDir)
            val pngFile = File.createTempFile("vector-preview-", ".png", context.cacheDir)
            try {
                FileOutputStream(svgFile).use {
                    store.copyVerifiedTo(requireNotNull(svgId), it)
                }
                FileOutputStream(pngFile).use {
                    store.copyVerifiedTo(requireNotNull(pngId), it)
                }
                val svgText = FileInputStream(svgFile).bufferedReader().use { it.readText() }
                assertTrue(svgText.contains("<svg"))
                assertTrue(svgText.contains("<circle"))
                assertTrue(svgText.contains("KN"))
                val bitmap = BitmapFactory.decodeFile(pngFile.absolutePath)
                assertNotNull(bitmap)
                assertEquals(128, bitmap!!.width)
                assertEquals(96, bitmap.height)
                bitmap.recycle()
            } finally {
                svgFile.delete()
                pngFile.delete()
            }
            assertEquals(2, store.listForTask("vector-test-1").size)
        } finally {
            module.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownOrExcessiveShapesWithoutPublishingArtifacts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "vector-studio-invalid-${System.nanoTime()}")
        val store = KellvynArtifactStore(root)
        val module = KellvynVectorStudio.definition(context, store).factory.create()
        try {
            val tool = module.tools.single { it.spec.name == KellvynVectorStudio.RENDER_TOOL }
            val invalidShape = tool.execute(
                mapOf(
                    "task_id" to "vector-invalid-1",
                    "design_json" to """{"width":128,"height":96,"shapes":[{"type":"script","fill":"#FFFFFF"}]}"""
                )
            )
            val invalidDimensions = tool.execute(
                mapOf(
                    "task_id" to "vector-invalid-2",
                    "design_json" to """{"width":2048,"height":2048,"shapes":[{"type":"circle","cx":10,"cy":10,"radius":4,"fill":"#FFFFFF"}]}"""
                )
            )
            assertFalse(invalidShape.success)
            assertFalse(invalidDimensions.success)
            assertTrue(store.listForTask("vector-invalid-1").isEmpty())
            assertTrue(store.listForTask("vector-invalid-2").isEmpty())
        } finally {
            module.close()
            root.deleteRecursively()
        }
    }
}
