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
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class KellvynAnimationStudioInstrumentedTest {
    @Test
    fun rendersKeyframesIntoVerifiedPngSpritesheetAndManifest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "animation-studio-test-${System.nanoTime()}")
        val store = KellvynArtifactStore(root)
        val module = KellvynAnimationStudio.definition(context, store).factory.create()
        val timeline = """
            {
              "width": 64,
              "height": 64,
              "fps": 2,
              "duration_ms": 1000,
              "columns": 2,
              "background": "#101820",
              "objects": [
                {
                  "type": "circle",
                  "color": "#FFAA22",
                  "radius": 7,
                  "keyframes": [
                    {"time_ms": 0, "x": 8, "y": 32},
                    {"time_ms": 1000, "x": 56, "y": 32}
                  ]
                }
              ]
            }
        """.trimIndent()

        try {
            val tool = module.tools.single { it.spec.name == KellvynAnimationStudio.RENDER_TOOL }
            val result = tool.execute(
                mapOf(
                    "task_id" to "animation-test-1",
                    "timeline_json" to timeline
                )
            )

            assertTrue(result.success)
            assertTrue(result.content.contains("frames=3"))
            assertTrue(result.content.contains("fps=2"))
            val sheetId = Regex("(?m)^spritesheet_artifact_id=([a-f0-9]{64})$")
                .find(result.content)?.groupValues?.get(1)
            val manifestId = Regex("(?m)^manifest_artifact_id=([a-f0-9]{64})$")
                .find(result.content)?.groupValues?.get(1)
            assertNotNull(sheetId)
            assertNotNull(manifestId)
            assertTrue(store.verify(requireNotNull(sheetId)))
            assertTrue(store.verify(requireNotNull(manifestId)))

            val output = File.createTempFile("animation-sheet-test-", ".png", context.cacheDir)
            try {
                FileOutputStream(output).use {
                    store.copyVerifiedTo(requireNotNull(sheetId), it)
                }
                val bitmap = BitmapFactory.decodeFile(output.absolutePath)
                assertNotNull(bitmap)
                assertEquals(128, bitmap!!.width)
                assertEquals(128, bitmap.height)
                bitmap.recycle()
            } finally {
                output.delete()
            }
            assertEquals(2, store.listForTask("animation-test-1").size)
        } finally {
            module.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsupportedTimelineAndFrameBudgetsWithoutArtifacts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "animation-studio-invalid-${System.nanoTime()}")
        val store = KellvynArtifactStore(root)
        val module = KellvynAnimationStudio.definition(context, store).factory.create()
        try {
            val tool = module.tools.single { it.spec.name == KellvynAnimationStudio.RENDER_TOOL }
            val invalidType = tool.execute(
                mapOf(
                    "task_id" to "animation-invalid-1",
                    "timeline_json" to """{"width":64,"height":64,"fps":2,"duration_ms":1000,"objects":[{"type":"script","color":"#FFFFFF","keyframes":[{"time_ms":0,"x":10,"y":10}]}]}"""
                )
            )
            val excessiveFrames = tool.execute(
                mapOf(
                    "task_id" to "animation-invalid-2",
                    "timeline_json" to """{"width":64,"height":64,"fps":24,"duration_ms":5000,"objects":[{"type":"circle","color":"#FFFFFF","radius":8,"keyframes":[{"time_ms":0,"x":10,"y":10}]}]}"""
                )
            )

            assertFalse(invalidType.success)
            assertFalse(excessiveFrames.success)
            assertTrue(store.listForTask("animation-invalid-1").isEmpty())
            assertTrue(store.listForTask("animation-invalid-2").isEmpty())
        } finally {
            module.close()
            root.deleteRecursively()
        }
    }
}
