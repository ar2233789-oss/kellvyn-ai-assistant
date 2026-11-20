package com.kellvyn.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Deterministic 2D timeline renderer. The first release exports a PNG spritesheet plus a
 * JSON timeline manifest; it does not claim to encode animated GIF or MP4.
 */
internal class KellvynAnimationStudio private constructor(
    context: Context,
    private val artifacts: KellvynArtifactStore
) : KellvynStudioModule {
    private val appContext = context.applicationContext

    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(renderSpritesheetTool())

    override fun trimTransientResources(level: KellvynCachePressure) = Unit

    override fun close() = Unit

    private fun renderSpritesheetTool() = object : KellvynAgentTool {
        override val spec = RENDER_SPEC

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            val validation = validateArguments(spec, arguments)
            if (validation != null) return KellvynToolResult(false, validation)
            return try {
                withContext(Dispatchers.Default) {
                    render(
                        taskId = arguments.getValue("task_id"),
                        timelineJson = arguments.getValue("timeline_json")
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (memory: OutOfMemoryError) {
                KellvynToolResult(
                    false,
                    "A renderização foi interrompida por falta de memória. Reduza as dimensões ou a quantidade de quadros."
                )
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível renderizar a animação: " +
                        (error.message ?: error.javaClass.simpleName).take(400)
                )
            }
        }
    }

    private fun render(taskId: String, timelineJson: String): KellvynToolResult {
        val timeline = parseTimeline(timelineJson)
        val rows = ceil(timeline.frameCount.toDouble() / timeline.columns.toDouble()).toInt()
        val sheetWidth = timeline.width * timeline.columns
        val sheetHeight = timeline.height * rows
        require(sheetWidth.toLong() * sheetHeight.toLong() <= MAX_SHEET_PIXELS) {
            "A spritesheet excede o limite interno de pixels."
        }

        val sheet = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val backgroundPaint = Paint().apply { color = timeline.background }
        try {
            for (frameIndex in 0 until timeline.frameCount) {
                currentCoroutineContext().ensureActive()
                val frameTime = if (timeline.frameCount <= 1) 0 else {
                    minOf(
                        timeline.durationMillis,
                        (frameIndex * 1_000.0 / timeline.fps).roundToInt()
                    )
                }
                val column = frameIndex % timeline.columns
                val row = frameIndex / timeline.columns
                canvas.save()
                canvas.translate(
                    (column * timeline.width).toFloat(),
                    (row * timeline.height).toFloat()
                )
                canvas.clipRect(0f, 0f, timeline.width.toFloat(), timeline.height.toFloat())
                canvas.drawRect(
                    0f,
                    0f,
                    timeline.width.toFloat(),
                    timeline.height.toFloat(),
                    backgroundPaint
                )
                timeline.objects.forEach { item -> drawObject(canvas, item, frameTime) }
                canvas.restore()
            }

            val directory = workingDirectory()
            val sheetFile = File.createTempFile("kellvyn-animation-sheet-", ".png", directory)
            val manifestFile = File.createTempFile("kellvyn-animation-manifest-", ".json", directory)
            try {
                val compressed = FileOutputStream(sheetFile).use { output ->
                    sheet.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                require(compressed && sheetFile.isFile && sheetFile.length() > 0L) {
                    "O encoder não conseguiu produzir a spritesheet PNG."
                }

                val timestamp = System.currentTimeMillis()
                val sheetRef = FileInputStream(sheetFile).use { input ->
                    artifacts.put(
                        taskId = taskId,
                        fileName = "animation-sheet-$timestamp.png",
                        mediaType = "image/png",
                        producerId = PRODUCER_ID,
                        input = input,
                        createdAtMillis = timestamp
                    )
                }
                val manifest = JSONObject()
                    .put("schema", "kellvyn-animation-timeline/v1")
                    .put("frame_width", timeline.width)
                    .put("frame_height", timeline.height)
                    .put("fps", timeline.fps)
                    .put("duration_ms", timeline.durationMillis)
                    .put("frame_count", timeline.frameCount)
                    .put("columns", timeline.columns)
                    .put("rows", rows)
                    .put("spritesheet_width", sheetWidth)
                    .put("spritesheet_height", sheetHeight)
                    .put("object_count", timeline.objects.size)
                    .put("spritesheet_artifact_id", sheetRef.id)
                    .put("spritesheet_sha256", sheetRef.sha256)
                FileOutputStream(manifestFile).use { output ->
                    output.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    output.flush()
                    output.fd.sync()
                }
                val manifestRef = FileInputStream(manifestFile).use { input ->
                    artifacts.put(
                        taskId = taskId,
                        fileName = "animation-manifest-$timestamp.json",
                        mediaType = "application/json",
                        producerId = PRODUCER_ID,
                        input = input,
                        createdAtMillis = timestamp + 1L
                    )
                }

                return KellvynToolResult(
                    true,
                    buildString {
                        appendLine("ANIMAÇÃO 2D RENDERIZADA COMO SPRITESHEET")
                        appendLine("spritesheet_artifact_id=${sheetRef.id}")
                        appendLine("spritesheet_sha256=${sheetRef.sha256}")
                        appendLine("manifest_artifact_id=${manifestRef.id}")
                        appendLine("manifest_sha256=${manifestRef.sha256}")
                        appendLine("frame_width=${timeline.width}")
                        appendLine("frame_height=${timeline.height}")
                        appendLine("frames=${timeline.frameCount}")
                        appendLine("fps=${timeline.fps}")
                        appendLine("duration_ms=${timeline.durationMillis}")
                        appendLine("columns=${timeline.columns}")
                        appendLine("rows=$rows")
                        appendLine("objects=${timeline.objects.size}")
                        appendLine("integrity=VERIFIED_ON_WRITE")
                        append("Formato entregue: PNG spritesheet + JSON manifesto; MP4/GIF não foi gerado.")
                    }
                )
            } finally {
                sheetFile.delete()
                manifestFile.delete()
            }
        } finally {
            sheet.recycle()
        }
    }

    private fun drawObject(canvas: Canvas, item: AnimatedObject, timeMillis: Int) {
        val state = interpolate(item.keyframes, timeMillis)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.color
            alpha = (state.opacity * 255f).roundToInt().coerceIn(0, 255)
            style = Paint.Style.FILL
            textSize = item.textSize
        }
        canvas.save()
        canvas.translate(state.x, state.y)
        canvas.rotate(state.rotation)
        canvas.scale(state.scale, state.scale)
        when (item.type) {
            "circle" -> canvas.drawCircle(0f, 0f, item.radius, paint)
            "rect" -> canvas.drawRect(
                RectF(
                    -item.width / 2f,
                    -item.height / 2f,
                    item.width / 2f,
                    item.height / 2f
                ),
                paint
            )
            "text" -> {
                val baseline = -(paint.ascent() + paint.descent()) / 2f
                canvas.drawText(item.text, -paint.measureText(item.text) / 2f, baseline, paint)
            }
        }
        canvas.restore()
    }

    private fun interpolate(frames: List<AnimationKeyframe>, timeMillis: Int): AnimationKeyframe {
        val previous = frames.lastOrNull { it.timeMillis <= timeMillis } ?: frames.first()
        val next = frames.firstOrNull { it.timeMillis >= timeMillis } ?: frames.last()
        if (previous === next || previous.timeMillis == next.timeMillis) return previous
        val progress = (
            (timeMillis - previous.timeMillis).toFloat() /
                (next.timeMillis - previous.timeMillis).toFloat()
            ).coerceIn(0f, 1f)
        return AnimationKeyframe(
            timeMillis = timeMillis,
            x = lerp(previous.x, next.x, progress),
            y = lerp(previous.y, next.y, progress),
            scale = lerp(previous.scale, next.scale, progress),
            rotation = lerp(previous.rotation, next.rotation, progress),
            opacity = lerp(previous.opacity, next.opacity, progress)
        )
    }

    private fun parseTimeline(source: String): AnimationTimeline {
        require(source.length in 2..MAX_TIMELINE_CHARS) {
            "A timeline precisa ter entre 2 e $MAX_TIMELINE_CHARS caracteres."
        }
        val root = JSONObject(source)
        require(root.keys().asSequence().all {
            it in setOf("width", "height", "fps", "duration_ms", "columns", "background", "objects")
        }) { "A timeline contém campos não reconhecidos." }

        val width = root.getInt("width")
        val height = root.getInt("height")
        val fps = root.getInt("fps")
        val duration = root.getInt("duration_ms")
        val frameCount = ceil(duration.toDouble() * fps.toDouble() / 1_000.0).toInt() + 1
        require(width in 16..MAX_FRAME_DIMENSION && height in 16..MAX_FRAME_DIMENSION) {
            "Cada quadro precisa medir entre 16 e $MAX_FRAME_DIMENSION pixels por dimensão."
        }
        require(fps in 1..MAX_FPS) { "FPS precisa ficar entre 1 e $MAX_FPS." }
        require(duration in 250..MAX_DURATION_MILLIS) { "A duração precisa ficar entre 250 e $MAX_DURATION_MILLIS ms." }
        require(frameCount in 2..MAX_FRAMES) {
            "A timeline excede o limite de $MAX_FRAMES quadros."
        }
        val columns = if (root.has("columns")) root.getInt("columns") else minOf(6, frameCount)
        require(columns in 1..6) { "A spritesheet aceita entre 1 e 6 colunas." }
        val background = parseColor(root.optString("background", "#000000"))

        val array = root.getJSONArray("objects")
        require(array.length() in 1..MAX_OBJECTS) {
            "A animação precisa conter entre 1 e $MAX_OBJECTS objetos."
        }
        val objects = (0 until array.length()).map { index ->
            parseObject(array.getJSONObject(index), width, height, duration)
        }
        return AnimationTimeline(
            width = width,
            height = height,
            fps = fps,
            durationMillis = duration,
            frameCount = frameCount,
            columns = columns,
            background = background,
            objects = objects
        )
    }

    private fun parseObject(
        json: JSONObject,
        frameWidth: Int,
        frameHeight: Int,
        durationMillis: Int
    ): AnimatedObject {
        require(json.keys().asSequence().all {
            it in setOf("type", "color", "radius", "width", "height", "text", "text_size", "keyframes")
        }) { "Um objeto da animação contém campos não reconhecidos." }
        val type = json.getString("type")
        require(type in setOf("circle", "rect", "text")) { "Tipo de objeto não suportado: $type." }
        val color = parseColor(json.getString("color"))
        val radius = json.optDouble("radius", 12.0).toFloat()
        val width = json.optDouble("width", 24.0).toFloat()
        val height = json.optDouble("height", 24.0).toFloat()
        val text = json.optString("text", "")
        val textSize = json.optDouble("text_size", 24.0).toFloat()
        when (type) {
            "circle" -> require(radius in 1f..512f) { "Raio de círculo inválido." }
            "rect" -> require(width in 1f..1_024f && height in 1f..1_024f) { "Dimensões do retângulo inválidas." }
            "text" -> require(text.isNotBlank() && text.length <= 80 && textSize in 8f..128f) {
                "Texto ou tamanho de fonte inválido."
            }
        }

        val array = json.getJSONArray("keyframes")
        require(array.length() in 1..MAX_KEYFRAMES_PER_OBJECT) {
            "Cada objeto precisa ter entre 1 e $MAX_KEYFRAMES_PER_OBJECT keyframes."
        }
        val keyframes = (0 until array.length()).map { index ->
            val frame = array.getJSONObject(index)
            require(frame.keys().asSequence().all {
                it in setOf("time_ms", "x", "y", "scale", "rotation", "opacity")
            }) { "Um keyframe contém campos não reconhecidos." }
            val time = frame.getInt("time_ms")
            val x = frame.getDouble("x").toFloat()
            val y = frame.getDouble("y").toFloat()
            val scale = frame.optDouble("scale", 1.0).toFloat()
            val rotation = frame.optDouble("rotation", 0.0).toFloat()
            val opacity = frame.optDouble("opacity", 1.0).toFloat()
            require(time in 0..durationMillis) { "Tempo de keyframe fora da duração da timeline." }
            require(x in -frameWidth.toFloat()..(frameWidth * 2f) &&
                y in -frameHeight.toFloat()..(frameHeight * 2f)
            ) { "Posição do keyframe fora dos limites aceitos." }
            require(scale in 0.05f..5f) { "Escala de keyframe precisa ficar entre 0,05 e 5." }
            require(rotation in -3_600f..3_600f) { "Rotação de keyframe fora dos limites." }
            require(opacity in 0f..1f) { "Opacidade de keyframe precisa ficar entre 0 e 1." }
            AnimationKeyframe(time, x, y, scale, rotation, opacity)
        }.sortedBy { it.timeMillis }
        require(keyframes.map { it.timeMillis }.distinct().size == keyframes.size) {
            "Um objeto não pode ter dois keyframes no mesmo instante."
        }

        return AnimatedObject(
            type = type,
            color = color,
            radius = radius,
            width = width,
            height = height,
            text = text,
            textSize = textSize,
            keyframes = keyframes
        )
    }

    private fun parseColor(value: String): Int {
        require(value.matches(Regex("#(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})"))) {
            "A cor precisa usar #RRGGBB ou #AARRGGBB."
        }
        return Color.parseColor(value)
    }

    private fun validateArguments(
        spec: KellvynToolSpec,
        arguments: Map<String, String>
    ): String? {
        val missing = spec.requiredArguments - arguments.keys
        if (missing.isNotEmpty()) return "Faltam argumentos obrigatórios: ${missing.sorted().joinToString()}."
        val unknown = arguments.keys - spec.allArguments
        if (unknown.isNotEmpty()) return "Argumentos não reconhecidos: ${unknown.sorted().joinToString()}."
        for (schema in spec.argumentSchemas) {
            val value = arguments[schema.name] ?: continue
            val error = schema.validate(value)
            if (error != null) return "Argumento '${schema.name}' inválido: $error"
        }
        return null
    }

    private fun workingDirectory(): File {
        val directory = File(appContext.cacheDir, CACHE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Não foi possível preparar a área temporária de animação.")
        }
        require(directory.isDirectory)
        require(directory.canonicalFile.path.startsWith(appContext.cacheDir.canonicalPath + File.separator))
        return directory
    }

    private data class AnimationKeyframe(
        val timeMillis: Int,
        val x: Float,
        val y: Float,
        val scale: Float,
        val rotation: Float,
        val opacity: Float
    )

    private data class AnimatedObject(
        val type: String,
        val color: Int,
        val radius: Float,
        val width: Float,
        val height: Float,
        val text: String,
        val textSize: Float,
        val keyframes: List<AnimationKeyframe>
    )

    private data class AnimationTimeline(
        val width: Int,
        val height: Int,
        val fps: Int,
        val durationMillis: Int,
        val frameCount: Int,
        val columns: Int,
        val background: Int,
        val objects: List<AnimatedObject>
    )

    companion object {
        const val MODULE_ID = "animation-2d-studio"
        const val RENDER_TOOL = "animation_render_spritesheet"
        private const val PRODUCER_ID = "animation.2d"
        private const val CACHE_DIRECTORY = "kellvyn-animation-studio"
        private const val MAX_FRAME_DIMENSION = 512
        private const val MAX_SHEET_PIXELS = 12L * 1024L * 1024L
        private const val MAX_FPS = 24
        private const val MAX_DURATION_MILLIS = 5_000
        private const val MAX_FRAMES = 36
        private const val MAX_OBJECTS = 16
        private const val MAX_KEYFRAMES_PER_OBJECT = 16
        private const val MAX_TIMELINE_CHARS = 20_000
        private const val MEBIBYTE = 1024L * 1024L

        private val RENDER_SPEC = KellvynToolSpec(
            name = RENDER_TOOL,
            description = "Renderiza uma timeline 2D com keyframes para uma spritesheet PNG e um manifesto JSON privados. Não gera MP4/GIF.",
            requiredArguments = setOf("task_id", "timeline_json"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            timeoutMs = 90_000L,
            workloadClass = KellvynWorkloadClass.HEAVY,
            estimatedWorkingSetBytes = 192L * MEBIBYTE,
            capabilities = setOf(
                KellvynStudioCapability.ANIMATION,
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
                    name = "timeline_json",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = MAX_TIMELINE_CHARS
                )
            )
        )

        private val MANIFEST = KellvynStudioModuleManifest(
            moduleId = MODULE_ID,
            version = 1,
            capabilities = setOf(
                KellvynStudioCapability.ANIMATION,
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.ARTIFACT_STORAGE
            ),
            supportedExtensions = setOf("json", "png"),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedLoadWorkingSetBytes = 8L * MEBIBYTE,
            estimatedResidentBytes = 2L * MEBIBYTE,
            permissions = setOf(KellvynStudioPermission.PRIVATE_ARTIFACT_WRITE),
            toolSpecs = listOf(RENDER_SPEC)
        )

        fun definition(
            context: Context,
            artifacts: KellvynArtifactStore
        ) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynAnimationStudio(context.applicationContext, artifacts)
        }
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress
}
