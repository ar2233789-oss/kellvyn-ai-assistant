package com.kellvyn.assistant

import android.content.Context
import android.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Offline parametric mesh builder that exports a standards-based binary glTF 2.0 (GLB).
 *
 * This is a deterministic geometry engine, not a neural 3D generator or renderer. It creates
 * bounded primitive meshes locally and stores one verified GLB artifact in the private store.
 */
internal class KellvynScene3DStudio private constructor(
    context: Context,
    private val artifacts: KellvynArtifactStore
) : KellvynStudioModule {
    private val appContext = context.applicationContext

    override val manifest: KellvynStudioModuleManifest = MANIFEST
    override val tools: List<KellvynAgentTool> = listOf(buildSceneTool())

    override fun trimTransientResources(level: KellvynCachePressure) = Unit
    override fun close() = Unit

    private fun buildSceneTool() = object : KellvynAgentTool {
        override val spec = BUILD_SPEC

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            val validation = validateArguments(arguments)
            if (validation != null) return KellvynToolResult(false, validation)
            return try {
                withContext(Dispatchers.Default) {
                    buildScene(
                        taskId = arguments.getValue("task_id"),
                        sceneJson = arguments.getValue("scene_json")
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (memory: OutOfMemoryError) {
                KellvynToolResult(
                    false,
                    "A geometria excedeu a memória disponível. Reduza a quantidade de objetos ou os segmentos."
                )
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível construir a cena 3D: " +
                        (error.message ?: error.javaClass.simpleName).take(400)
                )
            }
        }
    }

    private suspend fun buildScene(taskId: String, sceneJson: String): KellvynToolResult {
        val scene = parseScene(sceneJson)
        val binary = ByteArrayOutputStream()
        val bufferViews = JSONArray()
        val accessors = JSONArray()
        val materials = JSONArray()
        val meshes = JSONArray()
        val nodes = JSONArray()
        val sceneNodes = JSONArray()
        var totalVertices = 0
        var totalIndices = 0

        for ((objectIndex, item) in scene.objects.withIndex()) {
            currentCoroutineContext().ensureActive()
            val geometry = createGeometry(item)
            totalVertices += geometry.vertexCount
            totalIndices += geometry.indices.size
            require(totalVertices <= MAX_TOTAL_VERTICES) {
                "A cena excedeu o limite de vértices."
            }
            require(totalIndices <= MAX_TOTAL_INDICES) {
                "A cena excedeu o limite de índices."
            }

            val positionBytes = floatsToBytes(geometry.positions)
            val normalBytes = floatsToBytes(geometry.normals)
            val indexBytes = intsToBytes(geometry.indices)

            val positionOffset = appendAligned(binary, positionBytes)
            val positionView = bufferViews.length()
            bufferViews.put(
                JSONObject()
                    .put("buffer", 0)
                    .put("byteOffset", positionOffset)
                    .put("byteLength", positionBytes.size)
                    .put("target", ARRAY_BUFFER)
            )

            val positionAccessor = accessors.length()
            accessors.put(
                JSONObject()
                    .put("bufferView", positionView)
                    .put("componentType", FLOAT_COMPONENT)
                    .put("count", geometry.vertexCount)
                    .put("type", "VEC3")
                    .put("min", floatArrayJson(geometry.minimum))
                    .put("max", floatArrayJson(geometry.maximum))
            )

            val normalOffset = appendAligned(binary, normalBytes)
            val normalView = bufferViews.length()
            bufferViews.put(
                JSONObject()
                    .put("buffer", 0)
                    .put("byteOffset", normalOffset)
                    .put("byteLength", normalBytes.size)
                    .put("target", ARRAY_BUFFER)
            )
            val normalAccessor = accessors.length()
            accessors.put(
                JSONObject()
                    .put("bufferView", normalView)
                    .put("componentType", FLOAT_COMPONENT)
                    .put("count", geometry.vertexCount)
                    .put("type", "VEC3")
            )

            val indexOffset = appendAligned(binary, indexBytes)
            val indexView = bufferViews.length()
            bufferViews.put(
                JSONObject()
                    .put("buffer", 0)
                    .put("byteOffset", indexOffset)
                    .put("byteLength", indexBytes.size)
                    .put("target", ELEMENT_ARRAY_BUFFER)
            )
            val indexAccessor = accessors.length()
            accessors.put(
                JSONObject()
                    .put("bufferView", indexView)
                    .put("componentType", UNSIGNED_INT_COMPONENT)
                    .put("count", geometry.indices.size)
                    .put("type", "SCALAR")
                    .put("min", JSONArray().put(0))
                    .put("max", JSONArray().put(geometry.vertexCount - 1))
            )

            val materialIndex = materials.length()
            materials.put(
                JSONObject()
                    .put(
                        "pbrMetallicRoughness",
                        JSONObject()
                            .put("baseColorFactor", colorFactor(item.color))
                            .put("metallicFactor", 0.0)
                            .put("roughnessFactor", 0.72)
                    )
                    .put("doubleSided", false)
            )

            meshes.put(
                JSONObject()
                    .put("name", item.name)
                    .put(
                        "primitives",
                        JSONArray().put(
                            JSONObject()
                                .put(
                                    "attributes",
                                    JSONObject()
                                        .put("POSITION", positionAccessor)
                                        .put("NORMAL", normalAccessor)
                                )
                                .put("indices", indexAccessor)
                                .put("material", materialIndex)
                                .put("mode", TRIANGLES_MODE)
                        )
                    )
            )

            nodes.put(
                JSONObject()
                    .put("name", item.name)
                    .put("mesh", objectIndex)
                    .put("translation", floatArrayJson(item.position))
                    .put("rotation", floatArrayJson(eulerToQuaternion(item.rotation)))
                    .put("scale", floatArrayJson(item.scale))
            )
            sceneNodes.put(objectIndex)
        }

        currentCoroutineContext().ensureActive()
        val binaryData = binary.toByteArray()
        val glb = encodeGlb(
            sceneName = scene.name,
            binaryData = binaryData,
            bufferViews = bufferViews,
            accessors = accessors,
            materials = materials,
            meshes = meshes,
            nodes = nodes,
            sceneNodes = sceneNodes
        )
        require(glb.size <= MAX_GLB_BYTES) {
            "O arquivo GLB excedeu o limite de tamanho."
        }

        val timestamp = System.currentTimeMillis()
        val artifact = artifacts.put(
            taskId = taskId,
            fileName = safeFileName(scene.name) + "-" + timestamp + ".glb",
            mediaType = GLB_MEDIA_TYPE,
            producerId = PRODUCER_ID,
            input = ByteArrayInputStream(glb),
            createdAtMillis = timestamp
        )
        return KellvynToolResult(
            true,
            buildString {
                appendLine("CENA 3D PARAMÉTRICA CRIADA")
                appendLine("glb_artifact_id=" + artifact.id)
                appendLine("glb_sha256=" + artifact.sha256)
                appendLine("scene_name=" + scene.name)
                appendLine("objects=" + scene.objects.size)
                appendLine("vertices=" + totalVertices)
                appendLine("triangles=" + (totalIndices / 3))
                appendLine("bytes=" + artifact.sizeBytes)
                appendLine("format=glTF 2.0 binary (GLB)")
                appendLine("integrity=VERIFIED_ON_WRITE")
                append("Entrega: um artefato GLB local; nenhuma rede, serviço externo ou modelo de terceiros foi usado.")
            }
        )
    }

    private fun parseScene(source: String): Scene {
        require(source.length in 2..MAX_SCENE_CHARS) {
            "scene_json precisa ter entre 2 e " + MAX_SCENE_CHARS + " caracteres."
        }
        val root = JSONObject(source)
        require(onlyKeys(root, setOf("name", "objects"))) {
            "scene_json contém campos não reconhecidos."
        }
        val name = root.optString("name", "Kellvyn Scene").trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME_CHARS && !containsControl(name)) {
            "O nome da cena é inválido."
        }
        val objectsJson = root.optJSONArray("objects")
            ?: throw IllegalArgumentException("A cena precisa conter um array objects.")
        require(objectsJson.length() in 1..MAX_OBJECTS) {
            "A cena precisa conter entre 1 e " + MAX_OBJECTS + " objetos."
        }
        val objects = ArrayList<SceneObject>(objectsJson.length())
        for (index in 0 until objectsJson.length()) {
            val json = objectsJson.optJSONObject(index)
                ?: throw IllegalArgumentException("O objeto " + index + " não é um JSON object.")
            require(
                onlyKeys(
                    json,
                    setOf("name", "primitive", "position", "rotation", "scale", "color", "segments")
                )
            ) { "O objeto " + index + " contém campos não reconhecidos." }

            val objectName = json.optString("name", "Object " + (index + 1)).trim()
            require(objectName.isNotEmpty() && objectName.length <= MAX_NAME_CHARS &&
                !containsControl(objectName)
            ) { "O nome do objeto " + index + " é inválido." }

            val primitive = json.optString("primitive", "").lowercase(Locale.ROOT)
            require(primitive in SUPPORTED_PRIMITIVES) {
                "Primitiva inválida no objeto " + objectName +
                    ". Use cube, sphere, cylinder ou plane."
            }

            val position = readVector(json, "position", floatArrayOf(0f, 0f, 0f), -1_000f, 1_000f)
            val rotation = readVector(json, "rotation", floatArrayOf(0f, 0f, 0f), -360f, 360f)
            val scale = readVector(json, "scale", floatArrayOf(1f, 1f, 1f), 0.001f, 1_000f)
            val colorText = json.optString("color", "#B8C7D9")
            val color = parseColor(colorText)
            val segments = json.optInt("segments", DEFAULT_SEGMENTS)
            require(segments in MIN_SEGMENTS..MAX_SEGMENTS) {
                "segments deve ficar entre " + MIN_SEGMENTS + " e " + MAX_SEGMENTS + "."
            }
            objects += SceneObject(
                name = objectName,
                primitive = primitive,
                position = position,
                rotation = rotation,
                scale = scale,
                color = color,
                segments = segments
            )
        }
        return Scene(name, objects)
    }

    private fun createGeometry(item: SceneObject): Geometry = when (item.primitive) {
        "cube" -> createCube()
        "plane" -> createPlane()
        "sphere" -> createSphere(item.segments)
        "cylinder" -> createCylinder(item.segments)
        else -> throw IllegalArgumentException("Primitiva 3D não suportada.")
    }

    private fun createCube(): Geometry {
        val positions = ArrayList<Float>(72)
        val normals = ArrayList<Float>(72)
        val indices = ArrayList<Int>(36)
        val faces = listOf(
            Face(floatArrayOf(1f, 0f, 0f), arrayOf(
                floatArrayOf(0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, 0.5f, -0.5f),
                floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, -0.5f, 0.5f)
            )),
            Face(floatArrayOf(-1f, 0f, 0f), arrayOf(
                floatArrayOf(-0.5f, -0.5f, 0.5f), floatArrayOf(-0.5f, 0.5f, 0.5f),
                floatArrayOf(-0.5f, 0.5f, -0.5f), floatArrayOf(-0.5f, -0.5f, -0.5f)
            )),
            Face(floatArrayOf(0f, 1f, 0f), arrayOf(
                floatArrayOf(-0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f),
                floatArrayOf(0.5f, 0.5f, -0.5f), floatArrayOf(-0.5f, 0.5f, -0.5f)
            )),
            Face(floatArrayOf(0f, -1f, 0f), arrayOf(
                floatArrayOf(-0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, -0.5f, -0.5f),
                floatArrayOf(0.5f, -0.5f, 0.5f), floatArrayOf(-0.5f, -0.5f, 0.5f)
            )),
            Face(floatArrayOf(0f, 0f, 1f), arrayOf(
                floatArrayOf(-0.5f, -0.5f, 0.5f), floatArrayOf(0.5f, -0.5f, 0.5f),
                floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(-0.5f, 0.5f, 0.5f)
            )),
            Face(floatArrayOf(0f, 0f, -1f), arrayOf(
                floatArrayOf(-0.5f, -0.5f, -0.5f), floatArrayOf(-0.5f, 0.5f, -0.5f),
                floatArrayOf(0.5f, 0.5f, -0.5f), floatArrayOf(0.5f, -0.5f, -0.5f)
            ))
        )
        for (face in faces) {
            val first = positions.size / 3
            for (vertex in face.vertices) {
                positions.add(vertex[0]); positions.add(vertex[1]); positions.add(vertex[2])
                normals.add(face.normal[0]); normals.add(face.normal[1]); normals.add(face.normal[2])
            }
            indices.addAll(listOf(first, first + 1, first + 2, first, first + 2, first + 3))
        }
        return geometry(positions, normals, indices)
    }

    private fun createPlane(): Geometry {
        val positions = arrayListOf(
            -0.5f, 0f, -0.5f,
            -0.5f, 0f, 0.5f,
            0.5f, 0f, 0.5f,
            0.5f, 0f, -0.5f
        )
        val normals = arrayListOf(
            0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f
        )
        return geometry(positions, normals, arrayListOf(0, 1, 2, 0, 2, 3))
    }

    private fun createSphere(segments: Int): Geometry {
        val rings = maxOf(4, segments / 2)
        val positions = ArrayList<Float>((rings + 1) * (segments + 1) * 3)
        val normals = ArrayList<Float>((rings + 1) * (segments + 1) * 3)
        val indices = ArrayList<Int>(rings * segments * 6)
        for (ring in 0..rings) {
            val latitude = PI * ring.toDouble() / rings.toDouble()
            val y = cos(latitude).toFloat()
            val radius = sin(latitude).toFloat()
            for (segment in 0..segments) {
                val longitude = 2.0 * PI * segment.toDouble() / segments.toDouble()
                val x = radius * cos(longitude).toFloat()
                val z = radius * sin(longitude).toFloat()
                positions.add(0.5f * x); positions.add(0.5f * y); positions.add(0.5f * z)
                normals.add(x); normals.add(y); normals.add(z)
            }
        }
        val stride = segments + 1
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val a = ring * stride + segment
                val b = a + stride
                indices.add(a); indices.add(b); indices.add(a + 1)
                indices.add(a + 1); indices.add(b); indices.add(b + 1)
            }
        }
        return geometry(positions, normals, indices)
    }

    private fun createCylinder(segments: Int): Geometry {
        val positions = ArrayList<Float>((segments + 1) * 12 + segments * 6)
        val normals = ArrayList<Float>((segments + 1) * 12 + segments * 6)
        val indices = ArrayList<Int>(segments * 12)
        val sideBase = 0
        for (segment in 0..segments) {
            val angle = 2.0 * PI * segment.toDouble() / segments.toDouble()
            val x = cos(angle).toFloat()
            val z = sin(angle).toFloat()
            positions.add(0.5f * x); positions.add(-0.5f); positions.add(0.5f * z)
            normals.add(x); normals.add(0f); normals.add(z)
            positions.add(0.5f * x); positions.add(0.5f); positions.add(0.5f * z)
            normals.add(x); normals.add(0f); normals.add(z)
        }
        for (segment in 0 until segments) {
            val lower = sideBase + segment * 2
            val upper = lower + 1
            val nextLower = lower + 2
            val nextUpper = lower + 3
            indices.add(lower); indices.add(upper); indices.add(nextLower)
            indices.add(nextLower); indices.add(upper); indices.add(nextUpper)
        }

        val bottomCenter = positions.size / 3
        positions.add(0f); positions.add(-0.5f); positions.add(0f)
        normals.add(0f); normals.add(-1f); normals.add(0f)
        val bottomRing = positions.size / 3
        for (segment in 0..segments) {
            val angle = 2.0 * PI * segment.toDouble() / segments.toDouble()
            positions.add(0.5f * cos(angle).toFloat()); positions.add(-0.5f)
            positions.add(0.5f * sin(angle).toFloat())
            normals.add(0f); normals.add(-1f); normals.add(0f)
        }
        for (segment in 0 until segments) {
            indices.add(bottomCenter); indices.add(bottomRing + segment); indices.add(bottomRing + segment + 1)
        }

        val topCenter = positions.size / 3
        positions.add(0f); positions.add(0.5f); positions.add(0f)
        normals.add(0f); normals.add(1f); normals.add(0f)
        val topRing = positions.size / 3
        for (segment in 0..segments) {
            val angle = 2.0 * PI * segment.toDouble() / segments.toDouble()
            positions.add(0.5f * cos(angle).toFloat()); positions.add(0.5f)
            positions.add(0.5f * sin(angle).toFloat())
            normals.add(0f); normals.add(1f); normals.add(0f)
        }
        for (segment in 0 until segments) {
            indices.add(topCenter); indices.add(topRing + segment + 1); indices.add(topRing + segment)
        }
        return geometry(positions, normals, indices)
    }

    private fun geometry(
        positions: List<Float>,
        normals: List<Float>,
        indices: List<Int>
    ): Geometry {
        require(positions.isNotEmpty() && positions.size % 3 == 0)
        require(normals.size == positions.size)
        require(indices.isNotEmpty() && indices.size % 3 == 0)
        val minimum = floatArrayOf(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val maximum = floatArrayOf(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (index in positions.indices) {
            val value = positions[index]
            require(value.isFinite())
            val axis = index % 3
            minimum[axis] = minOf(minimum[axis], value)
            maximum[axis] = maxOf(maximum[axis], value)
        }
        val vertexCount = positions.size / 3
        require(indices.all { it in 0 until vertexCount })
        return Geometry(
            positions = positions.toFloatArray(),
            normals = normals.toFloatArray(),
            indices = indices.toIntArray(),
            minimum = minimum,
            maximum = maximum
        )
    }

    private fun encodeGlb(
        sceneName: String,
        binaryData: ByteArray,
        bufferViews: JSONArray,
        accessors: JSONArray,
        materials: JSONArray,
        meshes: JSONArray,
        nodes: JSONArray,
        sceneNodes: JSONArray
    ): ByteArray {
        val root = JSONObject()
            .put(
                "asset",
                JSONObject()
                    .put("version", "2.0")
                    .put("generator", "Kellvyn Scene3D Studio")
            )
            .put("scene", 0)
            .put(
                "scenes",
                JSONArray().put(
                    JSONObject()
                        .put("name", sceneName)
                        .put("nodes", sceneNodes)
                )
            )
            .put("nodes", nodes)
            .put("meshes", meshes)
            .put("materials", materials)
            .put("accessors", accessors)
            .put("bufferViews", bufferViews)
            .put(
                "buffers",
                JSONArray().put(JSONObject().put("byteLength", binaryData.size))
            )

        val jsonBytes = padToFour(root.toString().toByteArray(Charsets.UTF_8), 0x20)
        val binBytes = padToFour(binaryData, 0)
        val totalLength = 12 + 8 + jsonBytes.size + 8 + binBytes.size
        require(totalLength <= MAX_GLB_BYTES)

        val output = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(GLB_MAGIC)
        output.putInt(GLB_VERSION)
        output.putInt(totalLength)
        output.putInt(jsonBytes.size)
        output.putInt(JSON_CHUNK_TYPE)
        output.put(jsonBytes)
        output.putInt(binBytes.size)
        output.putInt(BIN_CHUNK_TYPE)
        output.put(binBytes)
        return output.array()
    }

    private fun appendAligned(output: ByteArrayOutputStream, bytes: ByteArray): Int {
        while (output.size() % 4 != 0) output.write(0)
        val offset = output.size()
        output.write(bytes)
        while (output.size() % 4 != 0) output.write(0)
        return offset
    }

    private fun floatsToBytes(values: FloatArray): ByteArray {
        val output = ByteBuffer.allocate(values.size * java.lang.Float.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(output::putFloat)
        return output.array()
    }

    private fun intsToBytes(values: IntArray): ByteArray {
        val output = ByteBuffer.allocate(values.size * Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(output::putInt)
        return output.array()
    }

    private fun padToFour(bytes: ByteArray, fill: Int): ByteArray {
        val padding = (4 - bytes.size % 4) % 4
        if (padding == 0) return bytes
        return bytes + ByteArray(padding) { fill.toByte() }
    }

    private fun floatArrayJson(values: FloatArray): JSONArray {
        val result = JSONArray()
        values.forEach { result.put(it.toDouble()) }
        return result
    }

    private fun colorFactor(color: Int): JSONArray = JSONArray()
        .put(Color.red(color) / 255.0)
        .put(Color.green(color) / 255.0)
        .put(Color.blue(color) / 255.0)
        .put(Color.alpha(color) / 255.0)

    private fun eulerToQuaternion(rotation: FloatArray): FloatArray {
        val x = Math.toRadians(rotation[0].toDouble()) / 2.0
        val y = Math.toRadians(rotation[1].toDouble()) / 2.0
        val z = Math.toRadians(rotation[2].toDouble()) / 2.0
        val sx = sin(x); val cx = cos(x)
        val sy = sin(y); val cy = cos(y)
        val sz = sin(z); val cz = cos(z)
        return floatArrayOf(
            (sx * cy * cz - cx * sy * sz).toFloat(),
            (cx * sy * cz + sx * cy * sz).toFloat(),
            (cx * cy * sz - sx * sy * cz).toFloat(),
            (cx * cy * cz + sx * sy * sz).toFloat()
        )
    }

    private fun readVector(
        json: JSONObject,
        name: String,
        default: FloatArray,
        minimum: Float,
        maximum: Float
    ): FloatArray {
        val array = json.optJSONArray(name) ?: return default.copyOf()
        require(array.length() == 3) {
            "O campo " + name + " precisa conter exatamente três números."
        }
        return FloatArray(3) { index ->
            val value = array.optDouble(index, Double.NaN)
            require(value.isFinite() && value >= minimum.toDouble() && value <= maximum.toDouble()) {
                "O campo " + name + " contém um valor fora do limite."
            }
            value.toFloat()
        }
    }

    private fun parseColor(value: String): Int {
        require(value.matches(Regex("#[A-Fa-f0-9]{6}"))) {
            "A cor precisa usar o formato #RRGGBB."
        }
        return Color.parseColor(value)
    }

    private fun validateArguments(arguments: Map<String, String>): String? {
        val required = setOf("task_id", "scene_json")
        val missing = required - arguments.keys
        if (missing.isNotEmpty()) {
            return "Faltam argumentos obrigatórios: " + missing.sorted().joinToString() + "."
        }
        val unknown = arguments.keys - required
        if (unknown.isNotEmpty()) {
            return "Argumentos não reconhecidos: " + unknown.sorted().joinToString() + "."
        }
        if (!arguments.getValue("task_id").matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}"))) {
            return "task_id inválido."
        }
        val json = arguments.getValue("scene_json")
        if (json.length !in 2..MAX_SCENE_CHARS) {
            return "scene_json precisa ter entre 2 e " + MAX_SCENE_CHARS + " caracteres."
        }
        return null
    }

    private fun onlyKeys(json: JSONObject, allowed: Set<String>): Boolean {
        val iterator = json.keys()
        while (iterator.hasNext()) {
            if (iterator.next() !in allowed) return false
        }
        return true
    }

    private fun containsControl(value: String): Boolean =
        value.any { it.code < 0x20 || it.code == 0x7F }

    private fun safeFileName(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-', '_')
            .take(48)
            .ifBlank { "scene" }

    private data class Scene(
        val name: String,
        val objects: List<SceneObject>
    )

    private data class SceneObject(
        val name: String,
        val primitive: String,
        val position: FloatArray,
        val rotation: FloatArray,
        val scale: FloatArray,
        val color: Int,
        val segments: Int
    )

    private data class Geometry(
        val positions: FloatArray,
        val normals: FloatArray,
        val indices: IntArray,
        val minimum: FloatArray,
        val maximum: FloatArray
    ) {
        val vertexCount: Int get() = positions.size / 3
    }

    private data class Face(
        val normal: FloatArray,
        val vertices: Array<FloatArray>
    )

    companion object {
        const val MODULE_ID = "scene-3d-studio"
        const val BUILD_TOOL = "scene3d_build_glb"
        private const val PRODUCER_ID = "scene3d.parametric"
        private const val GLB_MEDIA_TYPE = "model/gltf-binary"
        private const val MAX_SCENE_CHARS = 12_000
        private const val MAX_NAME_CHARS = 64
        private const val MAX_OBJECTS = 24
        private const val MAX_SEGMENTS = 32
        private const val MIN_SEGMENTS = 8
        private const val DEFAULT_SEGMENTS = 16
        private const val MAX_TOTAL_VERTICES = 20_000
        private const val MAX_TOTAL_INDICES = 120_000
        private const val MAX_GLB_BYTES = 8 * 1024 * 1024
        private const val MEBIBYTE = 1024L * 1024L
        private const val GLB_MAGIC = 0x46546C67
        private const val GLB_VERSION = 2
        private const val JSON_CHUNK_TYPE = 0x4E4F534A
        private const val BIN_CHUNK_TYPE = 0x004E4942
        private const val ARRAY_BUFFER = 34962
        private const val ELEMENT_ARRAY_BUFFER = 34963
        private const val FLOAT_COMPONENT = 5126
        private const val UNSIGNED_INT_COMPONENT = 5125
        private const val TRIANGLES_MODE = 4
        private val SUPPORTED_PRIMITIVES = setOf("cube", "sphere", "cylinder", "plane")

        private val BUILD_SPEC = KellvynToolSpec(
            name = BUILD_TOOL,
            description = "Cria uma cena 3D paramétrica offline com cubos, esferas, cilindros e planos, transformações e materiais, exportando um GLB 2.0 validado.",
            requiredArguments = setOf("task_id", "scene_json"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            timeoutMs = 120_000L,
            workloadClass = KellvynWorkloadClass.HEAVY,
            estimatedWorkingSetBytes = 96L * MEBIBYTE,
            capabilities = setOf(
                KellvynStudioCapability.SCENE_3D,
                KellvynStudioCapability.CAD_PARAMETRIC,
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.ARTIFACT_STORAGE
            ),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "task_id",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = 96,
                    pattern = "^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$"
                ),
                KellvynToolArgumentSchema(
                    name = "scene_json",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = MAX_SCENE_CHARS
                )
            )
        )

        private val MANIFEST = KellvynStudioModuleManifest(
            moduleId = MODULE_ID,
            version = 2,
            capabilities = setOf(
                KellvynStudioCapability.SCENE_3D,
                KellvynStudioCapability.CAD_PARAMETRIC,
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.ARTIFACT_STORAGE
            ),
            supportedExtensions = setOf("glb", "json"),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedLoadWorkingSetBytes = 4L * MEBIBYTE,
            estimatedResidentBytes = 2L * MEBIBYTE,
            permissions = setOf(KellvynStudioPermission.PRIVATE_ARTIFACT_WRITE),
            toolSpecs = listOf(BUILD_SPEC)
        )

        fun definition(
            context: Context,
            artifacts: KellvynArtifactStore
        ) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynScene3DStudio(context.applicationContext, artifacts)
        }
    }
}
