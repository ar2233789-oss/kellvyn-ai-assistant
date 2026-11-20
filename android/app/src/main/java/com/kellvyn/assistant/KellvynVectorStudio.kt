package com.kellvyn.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import java.util.Locale

/**
 * Offline structured vector artwork engine. It creates editable SVG and a raster PNG preview;
 * it is not a neural diffusion/image-generation model and never loads remote assets.
 */
internal class KellvynVectorStudio private constructor(
    context: Context,
    private val artifacts: KellvynArtifactStore
) : KellvynStudioModule {
    private val appContext = context.applicationContext

    override val manifest: KellvynStudioModuleManifest = MANIFEST
    override val tools: List<KellvynAgentTool> = listOf(renderTool())

    override fun trimTransientResources(level: KellvynCachePressure) = Unit
    override fun close() = Unit

    private fun renderTool() = object : KellvynAgentTool {
        override val spec = RENDER_SPEC

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            val validation = validateArguments(arguments)
            if (validation != null) return KellvynToolResult(false, validation)
            return try {
                withContext(Dispatchers.Default) {
                    render(
                        taskId = arguments.getValue("task_id"),
                        designJson = arguments.getValue("design_json")
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (memory: OutOfMemoryError) {
                KellvynToolResult(
                    false,
                    "A prévia vetorial excedeu a memória disponível. Reduza as dimensões ou a quantidade de formas."
                )
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível criar o desenho vetorial: " +
                        (error.message ?: error.javaClass.simpleName).take(400)
                )
            }
        }
    }

    private suspend fun render(taskId: String, designJson: String): KellvynToolResult {
        val design = parseDesign(designJson)
        val bitmap = Bitmap.createBitmap(design.width, design.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(design.background)
        try {
            design.shapes.forEach { shape ->
                currentCoroutineContext().ensureActive()
                drawShape(canvas, shape)
            }

            val directory = workingDirectory()
            val svgFile = File.createTempFile("kellvyn-vector-", ".svg", directory)
            val pngFile = File.createTempFile("kellvyn-vector-preview-", ".png", directory)
            try {
                FileOutputStream(svgFile).use { output ->
                    output.write(buildSvg(design).toByteArray(Charsets.UTF_8))
                    output.flush()
                    output.fd.sync()
                }
                val encoded = FileOutputStream(pngFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                require(encoded && pngFile.isFile && pngFile.length() > 0L) {
                    "O encoder não conseguiu produzir a prévia PNG."
                }

                val timestamp = System.currentTimeMillis()
                val svgRef = FileInputStream(svgFile).use { input ->
                    artifacts.put(
                        taskId = taskId,
                        fileName = "vector-art-$timestamp.svg",
                        mediaType = "image/svg+xml",
                        producerId = PRODUCER_ID,
                        input = input,
                        createdAtMillis = timestamp
                    )
                }
                val pngRef = FileInputStream(pngFile).use { input ->
                    artifacts.put(
                        taskId = taskId,
                        fileName = "vector-preview-$timestamp.png",
                        mediaType = "image/png",
                        producerId = PRODUCER_ID,
                        input = input,
                        createdAtMillis = timestamp + 1L
                    )
                }

                return KellvynToolResult(
                    true,
                    buildString {
                        appendLine("ARTE VETORIAL CRIADA")
                        appendLine("svg_artifact_id=${svgRef.id}")
                        appendLine("svg_sha256=${svgRef.sha256}")
                        appendLine("png_artifact_id=${pngRef.id}")
                        appendLine("png_sha256=${pngRef.sha256}")
                        appendLine("width=${design.width}")
                        appendLine("height=${design.height}")
                        appendLine("shapes=${design.shapes.size}")
                        appendLine("integrity=VERIFIED_ON_WRITE")
                        append("Entrega: SVG editável + prévia PNG. Nenhum arquivo remoto foi usado.")
                    }
                )
            } finally {
                svgFile.delete()
                pngFile.delete()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawShape(canvas: Canvas, shape: VectorShape) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shape.fill ?: Color.TRANSPARENT
            style = if (shape.fill == null) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = shape.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when (shape) {
            is VectorShape.Rect -> {
                shape.fill?.let {
                    paint.color = it
                    paint.style = Paint.Style.FILL
                    if (shape.radius > 0f) {
                        canvas.drawRoundRect(
                            RectF(shape.x, shape.y, shape.x + shape.width, shape.y + shape.height),
                            shape.radius,
                            shape.radius,
                            paint
                        )
                    } else {
                        canvas.drawRect(shape.x, shape.y, shape.x + shape.width, shape.y + shape.height, paint)
                    }
                }
                shape.stroke?.let {
                    paint.color = it
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = shape.strokeWidth
                    if (shape.radius > 0f) {
                        canvas.drawRoundRect(
                            RectF(shape.x, shape.y, shape.x + shape.width, shape.y + shape.height),
                            shape.radius,
                            shape.radius,
                            paint
                        )
                    } else {
                        canvas.drawRect(shape.x, shape.y, shape.x + shape.width, shape.y + shape.height, paint)
                    }
                }
            }
            is VectorShape.Circle -> {
                shape.fill?.let { paint.color = it; paint.style = Paint.Style.FILL; canvas.drawCircle(shape.cx, shape.cy, shape.radius, paint) }
                shape.stroke?.let { paint.color = it; paint.style = Paint.Style.STROKE; paint.strokeWidth = shape.strokeWidth; canvas.drawCircle(shape.cx, shape.cy, shape.radius, paint) }
            }
            is VectorShape.Line -> {
                paint.color = shape.stroke
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = shape.strokeWidth
                canvas.drawLine(shape.x1, shape.y1, shape.x2, shape.y2, paint)
            }
            is VectorShape.Polygon -> {
                val path = Path().apply {
                    moveTo(shape.points.first().first, shape.points.first().second)
                    shape.points.drop(1).forEach { lineTo(it.first, it.second) }
                    close()
                }
                shape.fill?.let { paint.color = it; paint.style = Paint.Style.FILL; canvas.drawPath(path, paint) }
                shape.stroke?.let { paint.color = it; paint.style = Paint.Style.STROKE; paint.strokeWidth = shape.strokeWidth; canvas.drawPath(path, paint) }
            }
            is VectorShape.Text -> {
                paint.color = shape.fill ?: Color.WHITE
                paint.style = Paint.Style.FILL
                paint.textSize = shape.fontSize
                canvas.drawText(shape.text, shape.x, shape.y, paint)
            }
        }
    }

    private fun buildSvg(design: VectorDesign): String = buildString {
        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${design.width}" height="${design.height}" viewBox="0 0 ${design.width} ${design.height}">""")
        appendLine("""  <rect width="100%" height="100%" fill="${colorHex(design.background)}"/>""")
        design.shapes.forEach { shape ->
            when (shape) {
                is VectorShape.Rect -> {
                    append("""  <rect x="${num(shape.x)}" y="${num(shape.y)}" width="${num(shape.width)}" height="${num(shape.height)}" rx="${num(shape.radius)}"""")
                    appendPaint(shape.fill, shape.stroke, shape.strokeWidth)
                    appendLine(" />")
                }
                is VectorShape.Circle -> {
                    append("""  <circle cx="${num(shape.cx)}" cy="${num(shape.cy)}" r="${num(shape.radius)}"""")
                    appendPaint(shape.fill, shape.stroke, shape.strokeWidth)
                    appendLine(" />")
                }
                is VectorShape.Line -> {
                    append("""  <line x1="${num(shape.x1)}" y1="${num(shape.y1)}" x2="${num(shape.x2)}" y2="${num(shape.y2)}" stroke="${colorHex(shape.stroke)}" stroke-width="${num(shape.strokeWidth)}" stroke-linecap="round" />""")
                    appendLine()
                }
                is VectorShape.Polygon -> {
                    append("""  <polygon points="${shape.points.joinToString(" ") { num(it.first) + "," + num(it.second) }}"""")
                    appendPaint(shape.fill, shape.stroke, shape.strokeWidth)
                    appendLine(" />")
                }
                is VectorShape.Text -> {
                    append("""  <text x="${num(shape.x)}" y="${num(shape.y)}" font-size="${num(shape.fontSize)}" font-family="sans-serif" fill="${colorHex(shape.fill ?: Color.WHITE)}">""")
                    append(escapeXml(shape.text))
                    appendLine("</text>")
                }
            }
        }
        appendLine("</svg>")
    }

    private fun StringBuilder.appendPaint(fill: Int?, stroke: Int?, strokeWidth: Float) {
        if (fill == null) append(""" fill="none"""") else append(""" fill="${colorHex(fill)}"""")
        if (stroke != null) {
            append(""" stroke="${colorHex(stroke)}" stroke-width="${num(strokeWidth)}"""")
        }
    }

    private fun parseDesign(source: String): VectorDesign {
        require(source.length in 2..MAX_DESIGN_CHARS) { "O desenho excede o tamanho máximo permitido." }
        val root = JSONObject(source)
        require(root.keys().asSequence().all {
            it in setOf("width", "height", "background", "shapes")
        }) { "A descrição vetorial contém campos não reconhecidos." }
        val width = root.getInt("width")
        val height = root.getInt("height")
        require(width in 16..MAX_DIMENSION && height in 16..MAX_DIMENSION) {
            "Cada dimensão precisa ficar entre 16 e $MAX_DIMENSION pixels."
        }
        require(width.toLong() * height.toLong() <= MAX_PIXELS) {
            "A prévia excede o limite de pixels do estúdio vetorial."
        }
        val background = parseColor(root.optString("background", "#101820"))
        val array = root.getJSONArray("shapes")
        require(array.length() in 1..MAX_SHAPES) {
            "O desenho precisa conter de 1 a $MAX_SHAPES formas."
        }
        val shapes = (0 until array.length()).map { parseShape(array.getJSONObject(it), width, height) }
        return VectorDesign(width, height, background, shapes)
    }

    private fun parseShape(json: JSONObject, frameWidth: Int, frameHeight: Int): VectorShape {
        val type = json.getString("type")
        val common = setOf("type")
        return when (type) {
            "rect" -> {
                requireKeys(json, common + setOf("x", "y", "width", "height", "fill", "stroke", "stroke_width", "radius"))
                val x = finiteFloat(json, "x")
                val y = finiteFloat(json, "y")
                val width = finiteFloat(json, "width")
                val height = finiteFloat(json, "height")
                val radius = finiteFloat(json, "radius", 0f)
                validateBounds(x, y, frameWidth, frameHeight)
                require(width in 1f..frameWidth.toFloat() * 2f && height in 1f..frameHeight.toFloat() * 2f)
                require(radius in 0f..512f)
                VectorShape.Rect(x, y, width, height, radius, optionalColor(json, "fill"), optionalColor(json, "stroke"), finiteFloat(json, "stroke_width", 1f).also { require(it in 0f..128f) })
            }
            "circle" -> {
                requireKeys(json, common + setOf("cx", "cy", "radius", "fill", "stroke", "stroke_width"))
                val cx = finiteFloat(json, "cx")
                val cy = finiteFloat(json, "cy")
                val radius = finiteFloat(json, "radius")
                validateBounds(cx, cy, frameWidth, frameHeight)
                require(radius in 1f..1_024f)
                VectorShape.Circle(cx, cy, radius, optionalColor(json, "fill"), optionalColor(json, "stroke"), finiteFloat(json, "stroke_width", 1f).also { require(it in 0f..128f) })
            }
            "line" -> {
                requireKeys(json, common + setOf("x1", "y1", "x2", "y2", "stroke", "stroke_width"))
                val x1 = finiteFloat(json, "x1")
                val y1 = finiteFloat(json, "y1")
                val x2 = finiteFloat(json, "x2")
                val y2 = finiteFloat(json, "y2")
                validateBounds(x1, y1, frameWidth, frameHeight)
                validateBounds(x2, y2, frameWidth, frameHeight)
                VectorShape.Line(x1, y1, x2, y2, parseColor(json.getString("stroke")), finiteFloat(json, "stroke_width", 2f).also { require(it in 0.5f..128f) })
            }
            "polygon" -> {
                requireKeys(json, common + setOf("points", "fill", "stroke", "stroke_width"))
                val pointsJson = json.getJSONArray("points")
                require(pointsJson.length() in 3..MAX_POLYGON_POINTS)
                val points = (0 until pointsJson.length()).map { index ->
                    val pair = pointsJson.getJSONArray(index)
                    require(pair.length() == 2)
                    val x = pair.getDouble(0).toFloat()
                    val y = pair.getDouble(1).toFloat()
                    require(x.isFinite() && y.isFinite())
                    validateBounds(x, y, frameWidth, frameHeight)
                    x to y
                }
                VectorShape.Polygon(points, optionalColor(json, "fill"), optionalColor(json, "stroke"), finiteFloat(json, "stroke_width", 1f).also { require(it in 0f..128f) })
            }
            "text" -> {
                requireKeys(json, common + setOf("x", "y", "text", "font_size", "fill"))
                val x = finiteFloat(json, "x")
                val y = finiteFloat(json, "y")
                val text = json.getString("text")
                val size = finiteFloat(json, "font_size", 24f)
                validateBounds(x, y, frameWidth, frameHeight)
                require(text.isNotBlank() && text.length <= MAX_TEXT_CHARS)
                require(size in 8f..256f)
                VectorShape.Text(x, y, text, size, optionalColor(json, "fill") ?: Color.WHITE)
            }
            else -> throw IllegalArgumentException("Tipo vetorial não suportado: $type.")
        }
    }

    private fun validateBounds(x: Float, y: Float, width: Int, height: Int) {
        require(x in -width.toFloat()..(width * 2f) && y in -height.toFloat()..(height * 2f)) {
            "Coordenadas vetoriais fora dos limites permitidos."
        }
    }

    private fun requireKeys(json: JSONObject, allowed: Set<String>) {
        require(json.keys().asSequence().all { it in allowed }) {
            "Uma forma vetorial contém campos não reconhecidos."
        }
    }

    private fun finiteFloat(json: JSONObject, key: String, default: Float? = null): Float {
        val value = if (json.has(key)) json.getDouble(key).toFloat()
        else default ?: throw IllegalArgumentException("Campo obrigatório ausente: $key.")
        require(value.isFinite()) { "Valor numérico inválido em $key." }
        return value
    }

    private fun optionalColor(json: JSONObject, key: String): Int? =
        if (json.isNull(key) || !json.has(key)) null else parseColor(json.getString(key))

    private fun parseColor(value: String): Int {
        require(value.matches(Regex("#(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})"))) {
            "Cor vetorial precisa usar #RRGGBB ou #AARRGGBB."
        }
        return Color.parseColor(value)
    }

    private fun colorHex(color: Int): String =
        String.format(Locale.ROOT, "#%06X", 0xFFFFFF and color)

    private fun num(value: Float): String =
        String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun validateArguments(arguments: Map<String, String>): String? {
        val required = setOf("task_id", "design_json")
        val allowed = required
        val missing = required - arguments.keys
        if (missing.isNotEmpty()) return "Faltam argumentos: ${missing.sorted().joinToString()}."
        val unknown = arguments.keys - allowed
        if (unknown.isNotEmpty()) return "Argumentos não reconhecidos: ${unknown.sorted().joinToString()}."
        if (!arguments.getValue("task_id").matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}"))) {
            return "task_id inválido."
        }
        if (arguments.getValue("design_json").length !in 2..MAX_DESIGN_CHARS) {
            return "design_json precisa ter entre 2 e $MAX_DESIGN_CHARS caracteres."
        }
        return null
    }

    private fun workingDirectory(): File {
        val directory = File(appContext.cacheDir, CACHE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Não foi possível preparar a área temporária vetorial.")
        }
        require(directory.isDirectory)
        require(directory.canonicalFile.path.startsWith(appContext.cacheDir.canonicalPath + File.separator))
        return directory
    }

    private sealed class VectorShape {
        data class Rect(
            val x: Float, val y: Float, val width: Float, val height: Float,
            val radius: Float, val fill: Int?, val stroke: Int?, val strokeWidth: Float
        ) : VectorShape()
        data class Circle(
            val cx: Float, val cy: Float, val radius: Float,
            val fill: Int?, val stroke: Int?, val strokeWidth: Float
        ) : VectorShape()
        data class Line(
            val x1: Float, val y1: Float, val x2: Float, val y2: Float,
            val stroke: Int, val strokeWidth: Float
        ) : VectorShape()
        data class Polygon(
            val points: List<Pair<Float, Float>>, val fill: Int?, val stroke: Int?, val strokeWidth: Float
        ) : VectorShape()
        data class Text(
            val x: Float, val y: Float, val text: String, val fontSize: Float, val fill: Int?
        ) : VectorShape()
    }

    private data class VectorDesign(
        val width: Int,
        val height: Int,
        val background: Int,
        val shapes: List<VectorShape>
    )

    companion object {
        const val MODULE_ID = "vector-graphics-studio"
        const val RENDER_TOOL = "vector_render_artwork"
        private const val PRODUCER_ID = "vector.graphics"
        private const val CACHE_DIRECTORY = "kellvyn-vector-studio"
        private const val MAX_DIMENSION = 2_048
        private const val MAX_PIXELS = 4L * 1_024L * 1_024L
        private const val MAX_SHAPES = 64
        private const val MAX_POLYGON_POINTS = 64
        private const val MAX_TEXT_CHARS = 200
        private const val MAX_DESIGN_CHARS = 4_000
        private const val MEBIBYTE = 1024L * 1024L

        private val RENDER_SPEC = KellvynToolSpec(
            name = RENDER_TOOL,
            description = "Cria arte vetorial estruturada com formas, texto, cores e polígonos e exporta SVG editável + prévia PNG offline.",
            requiredArguments = setOf("task_id", "design_json"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            timeoutMs = 90_000L,
            workloadClass = KellvynWorkloadClass.HEAVY,
            estimatedWorkingSetBytes = 96L * MEBIBYTE,
            capabilities = setOf(
                KellvynStudioCapability.VECTOR_GRAPHICS,
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
                    name = "design_json",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = MAX_DESIGN_CHARS
                )
            )
        )

        private val MANIFEST = KellvynStudioModuleManifest(
            moduleId = MODULE_ID,
            version = 1,
            capabilities = setOf(
                KellvynStudioCapability.VECTOR_GRAPHICS,
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.ARTIFACT_STORAGE
            ),
            supportedExtensions = setOf("svg", "png", "json"),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedLoadWorkingSetBytes = 4L * MEBIBYTE,
            estimatedResidentBytes = 2L * MEBIBYTE,
            permissions = setOf(KellvynStudioPermission.PRIVATE_ARTIFACT_WRITE),
            toolSpecs = listOf(RENDER_SPEC)
        )

        fun definition(
            context: Context,
            artifacts: KellvynArtifactStore
        ) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynVectorStudio(context.applicationContext, artifacts)
        }
    }
}
