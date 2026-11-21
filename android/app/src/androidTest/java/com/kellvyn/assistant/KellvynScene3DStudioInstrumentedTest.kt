package com.kellvyn.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class KellvynScene3DStudioInstrumentedTest {
    @Test
    fun buildsVerifiedGlb20WithParametricObjectsAndTransforms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "scene3d-studio-test-" + System.nanoTime())
        val store = KellvynArtifactStore(root)
        val module = KellvynScene3DStudio.definition(context, store).factory.create()
        val scene = """
            {
              "name": "KN Workshop",
              "objects": [
                {"name":"Main Cube","primitive":"cube","position":[0,0,0],"rotation":[0,20,0],"scale":[2,1,1],"color":"#22AA88"},
                {"name":"Sphere","primitive":"sphere","position":[2,0,0],"segments":16,"color":"#4488FF"},
                {"name":"Cylinder","primitive":"cylinder","position":[-2,0,0],"segments":12,"color":"#FFAA22"},
                {"name":"Ground","primitive":"plane","position":[0,-1,0],"scale":[6,1,6],"color":"#777777"}
              ]
            }
        """.trimIndent()

        try {
            val tool = module.tools.single { it.spec.name == KellvynScene3DStudio.BUILD_TOOL }
            val result = tool.execute(
                mapOf("task_id" to "scene3d-test-1", "scene_json" to scene)
            )
            assertTrue(result.content, result.success)
            assertTrue(result.content.contains("format=glTF 2.0 binary (GLB)"))
            assertTrue(result.content.contains("objects=4"))
            val artifactId = Regex("(?m)^glb_artifact_id=([a-f0-9]{64})$")
                .find(result.content)?.groupValues?.get(1)
            assertNotNull(artifactId)
            assertTrue(store.verify(requireNotNull(artifactId)))

            val output = File.createTempFile("scene3d-test-", ".glb", context.cacheDir)
            try {
                FileOutputStream(output).use {
                    store.copyVerifiedTo(requireNotNull(artifactId), it)
                }
                val bytes = output.readBytes()
                assertTrue(bytes.size > 32)
                val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(0x46546C67, header.int)
                assertEquals(2, header.int)
                assertEquals(bytes.size, header.int)
                val jsonLength = header.int
                assertEquals(0x4E4F534A, header.int)
                assertTrue(jsonLength > 0 && 20 + jsonLength + 8 <= bytes.size)
                val jsonBytes = ByteArray(jsonLength)
                header.get(jsonBytes)
                val json = JSONObject(String(jsonBytes, Charsets.UTF_8).trim())
                assertEquals("2.0", json.getJSONObject("asset").getString("version"))
                assertEquals(4, json.getJSONArray("meshes").length())
                assertEquals(4, json.getJSONArray("nodes").length())
                assertEquals(4, json.getJSONArray("materials").length())
                assertEquals(1, json.getInt("scene"))
                assertTrue(json.getJSONArray("bufferViews").length() >= 12)

                val binLength = header.int
                assertEquals(0x004E4942, header.int)
                assertTrue(binLength > 0)
                assertEquals(20 + jsonLength + 8 + binLength, bytes.size)
                val declaredBufferLength = json.getJSONArray("buffers")
                    .getJSONObject(0).getInt("byteLength")
                assertTrue(declaredBufferLength in (binLength - 3)..binLength)
            } finally {
                output.delete()
            }
            assertEquals(1, store.listForTask("scene3d-test-1").size)
        } finally {
            module.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownPrimitivesInvalidTransformsAndExcessiveSegmentsWithoutArtifacts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "scene3d-studio-invalid-" + System.nanoTime())
        val store = KellvynArtifactStore(root)
        val module = KellvynScene3DStudio.definition(context, store).factory.create()
        try {
            val tool = module.tools.single { it.spec.name == KellvynScene3DStudio.BUILD_TOOL }
            val invalidPrimitive = tool.execute(
                mapOf(
                    "task_id" to "scene3d-invalid-1",
                    "scene_json" to """{"objects":[{"primitive":"script"}]}"""
                )
            )
            val invalidScale = tool.execute(
                mapOf(
                    "task_id" to "scene3d-invalid-2",
                    "scene_json" to """{"objects":[{"primitive":"cube","scale":[1,0,1]}]}"""
                )
            )
            val excessiveSegments = tool.execute(
                mapOf(
                    "task_id" to "scene3d-invalid-3",
                    "scene_json" to """{"objects":[{"primitive":"sphere","segments":128}]}"""
                )
            )
            val unknownField = tool.execute(
                mapOf(
                    "task_id" to "scene3d-invalid-4",
                    "scene_json" to """{"objects":[{"primitive":"cube","script":"not-accepted"}]}"""
                )
            )

            assertFalse(invalidPrimitive.success)
            assertFalse(invalidScale.success)
            assertFalse(excessiveSegments.success)
            assertFalse(unknownField.success)
            assertTrue(store.listForTask("scene3d-invalid-1").isEmpty())
            assertTrue(store.listForTask("scene3d-invalid-2").isEmpty())
            assertTrue(store.listForTask("scene3d-invalid-3").isEmpty())
            assertTrue(store.listForTask("scene3d-invalid-4").isEmpty())
        } finally {
            module.close()
            root.deleteRecursively()
        }
    }
}
