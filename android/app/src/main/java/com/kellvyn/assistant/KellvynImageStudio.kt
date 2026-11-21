package com.kellvyn.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Headless raster image motor. It is an editor/inspector, not a neural image generator.
 * Inputs and outputs are private ArtifactStore objects; raw pixels never enter KN context.
 */
internal class KellvynImageStudio private constructor(
    context: Context,
    private val artifacts: KellvynArtifactStore
) : KellvynStudioModule {
    private val appContext = context.applicationContext

    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        inspectTool(),
        resizeTool()
    )

    override fun trimTransientResources(level: KellvynCachePressure) = Unit

    override fun close() = Unit

    private fun inspectTool() = object : KellvynAgentTool {
        override val spec = INSPECT_SPEC

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            if (arguments.keys != setOf("artifact_id")) {
                return KellvynToolResult(false, "A inspeção exige somente artifact_id.")
            }
            return try {
                withContext(Dispatchers.IO) {
                    val ref = artifacts.get(arguments.getValue("artifact_id"))
                    require(ref.mediaType.startsWith("image/")) {
                        "O artefato informado não declara conteúdo de imagem."
                    }
                    val staged = stageInput(ref.id)
                    try {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(staged.absolutePath, options)
                        validateSourceDimensions(options.outWidth, options.outHeight)
                        KellvynToolResult(
                            true,
                            buildString {
                                appendLine("INSPEÇÃO DE IMAGEM")
                                appendLine("artifact_id=${ref.id}")
                                appendLine("file_name=${ref.fileName}")
                                appendLine("media_type=${options.outMimeType ?: ref.mediaType}")
                                appendLine("width=${options.outWidth}")
                                appendLine("height=${options.outHeight}")
                                appendLine("pixels=${options.outWidth.toLong() * options.outHeight.toLong()}")
                                appendLine("size_bytes=${ref.sizeBytes}")
                                appendLine("sha256=${ref.sha256}")
                                append("integrity=VERIFIED")
                            }
                        )
                    } finally {
                        staged.delete()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível inspecionar a imagem: " +
                        (error.message ?: error.javaClass.simpleName).take(300)
                )
            }
        }
    }

    private fun resizeTool() = object : KellvynAgentTool {
        override val spec = RESIZE_SPEC

        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            val validation = validateArguments(spec, arguments)
            if (validation != null) return KellvynToolResult(false, validation)

            return try {
                withContext(Dispatchers.Default) {
                    resizeImage(
                        sourceArtifactId = arguments.getValue("artifact_id"),
                        taskId = arguments.getValue("task_id"),
                        width = arguments.getValue("width").toInt(),
                        height = arguments.getValue("height").toInt(),
                        format = arguments.getValue("format"),
                        quality = arguments["quality"]?.toIntOrNull() ?: DEFAULT_QUALITY
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (memory: OutOfMemoryError) {
                KellvynToolResult(
                    false,
                    "Redimensionamento interrompido por falta de memória. A imagem original foi preservada; reduza as dimensões solicitadas."
                )
            } catch (error: Exception) {
                KellvynToolResult(
                    false,
                    "Não foi possível redimensionar a imagem: " +
                        (error.message ?: error.javaClass.simpleName).take(400)
                )
            }
        }
    }

    private fun resizeImage(
        sourceArtifactId: String,
        taskId: String,
        width: Int,
        height: Int,
        format: String,
        quality: Int
    ): KellvynToolResult {
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            "Cada dimensão precisa ficar entre 1 e $MAX_DIMENSION pixels."
        }
        require(width.toLong() * height.toLong() <= MAX_OUTPUT_PIXELS) {
            "A imagem de saída ultrapassa o limite de $MAX_OUTPUT_PIXELS pixels."
        }
        require(quality in 1..100) { "A qualidade precisa ficar entre 1 e 100." }

        val source = artifacts.get(sourceArtifactId)
        require(source.mediaType.startsWith("image/")) {
            "O artefato de origem não é uma imagem."
        }
        val inputFile = stageInput(sourceArtifactId)
        val outputFile = File.createTempFile("kellvyn-image-output-", ".part", workingDirectory())

        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputFile.absolutePath, bounds)
            validateSourceDimensions(bounds.outWidth, bounds.outHeight)

            val sampleSize = calculateSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetWidth = width,
                targetHeight = height
            )
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inDither = false
            }
            val decoded = BitmapFactory.decodeFile(inputFile.absolutePath, options)
                ?: throw IllegalArgumentException("O decoder Android não reconheceu a imagem.")
            try {
                val outputBitmap = if (decoded.width == width && decoded.height == height) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, width, height, true)
                }
                try {
                    val compressFormat = when (format) {
                        "png" -> Bitmap.CompressFormat.PNG
                        "jpeg" -> Bitmap.CompressFormat.JPEG
                        "webp" -> Bitmap.CompressFormat.WEBP
                        else -> throw IllegalArgumentException("Formato de saída não suportado.")
                    }
                    val compressed = FileOutputStream(outputFile).use { stream ->
                        outputBitmap.compress(compressFormat, quality, stream)
                    }
                    require(compressed && outputFile.isFile && outputFile.length() > 0L) {
                        "O encoder não conseguiu produzir um arquivo válido."
                    }

                    val extension = if (format == "jpeg") "jpg" else format
                    val mimeType = when (format) {
                        "png" -> "image/png"
                        "jpeg" -> "image/jpeg"
                        else -> "image/webp"
                    }
                    val fileName = "image-${System.currentTimeMillis()}.$extension"
                    val resultRef = FileInputStream(outputFile).use { stream ->
                        artifacts.put(
                            taskId = taskId,
                            fileName = fileName,
                            mediaType = mimeType,
                            producerId = PRODUCER_ID,
                            input = stream
                        )
                    }
                    return KellvynToolResult(
                        true,
                        buildString {
                            appendLine("IMAGEM REDIMENSIONADA")
                            appendLine("source_artifact_id=$sourceArtifactId")
                            appendLine("artifact_id=${resultRef.id}")
                            appendLine("file_name=${resultRef.fileName}")
                            appendLine("media_type=${resultRef.mediaType}")
                            appendLine("width=$width")
                            appendLine("height=$height")
                            appendLine("size_bytes=${resultRef.sizeBytes}")
                            appendLine("sha256=${resultRef.sha256}")
                            appendLine("integrity=VERIFIED_ON_WRITE")
                            append("A imagem original não foi alterada.")
                        }
                    )
                } finally {
                    if (outputBitmap !== decoded) outputBitmap.recycle()
                }
            } finally {
                decoded.recycle()
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun stageInput(artifactId: String): File {
        val ref = artifacts.get(artifactId)
        val file = File.createTempFile("kellvyn-image-input-", ".img", workingDirectory())
        try {
            FileOutputStream(file).use { output ->
                val copied = artifacts.copyVerifiedTo(artifactId, output)
                require(copied.sha256 == ref.sha256)
            }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun workingDirectory(): File {
        val directory = File(appContext.cacheDir, CACHE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Não foi possível preparar a área temporária de imagens.")
        }
        require(directory.isDirectory)
        require(directory.canonicalFile.path.startsWith(appContext.cacheDir.canonicalPath + File.separator))
        return directory
    }

    private fun validateSourceDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0) { "O arquivo não contém dimensões de imagem válidas." }
        require(width <= MAX_DIMENSION * 4 && height <= MAX_DIMENSION * 4) {
            "A imagem de origem excede o limite de dimensão suportado."
        }
        require(width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS) {
            "A imagem de origem ultrapassa o limite de pixels do motor raster."
        }
    }

    private fun calculateSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth &&
            sourceHeight / (sample * 2) >= targetHeight
        ) {
            sample *= 2
        }
        return sample.coerceAtMost(64)
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

    companion object {
        const val MODULE_ID = "raster-image-studio"
        const val INSPECT_TOOL = "image_inspect"
        const val RESIZE_TOOL = "image_resize"
        private const val PRODUCER_ID = "image.raster"
        private const val CACHE_DIRECTORY = "kellvyn-image-studio"
        private const val MAX_DIMENSION = 8_192
        private const val MAX_SOURCE_PIXELS = 64L * 1024L * 1024L
        private const val MAX_OUTPUT_PIXELS = 16L * 1024L * 1024L
        private const val DEFAULT_QUALITY = 92
        private const val MEBIBYTE = 1024L * 1024L

        private val INSPECT_SPEC = KellvynToolSpec(
            name = INSPECT_TOOL,
            description = "Inspeciona dimensões, formato, tamanho e integridade de um artefato de imagem privado sem carregar pixels para o contexto.",
            requiredArguments = setOf("artifact_id"),
            risk = KellvynToolRisk.READ_ONLY,
            timeoutMs = 10_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedWorkingSetBytes = 8L * MEBIBYTE,
            capabilities = setOf(
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.RASTER_IMAGE_EDIT,
                KellvynStudioCapability.ARTIFACT_STORAGE
            ),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "artifact_id",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = 64,
                    pattern = "^[a-f0-9]{64}$"
                )
            )
        )

        private val RESIZE_SPEC = KellvynToolSpec(
            name = RESIZE_TOOL,
            description = "Redimensiona uma imagem privada e salva uma nova versão imutável no armazenamento de artefatos; nunca sobrescreve a origem.",
            requiredArguments = setOf("artifact_id", "task_id", "width", "height", "format"),
            optionalArguments = setOf("quality"),
            risk = KellvynToolRisk.ARTIFACT_CREATE,
            timeoutMs = 90_000L,
            workloadClass = KellvynWorkloadClass.HEAVY,
            estimatedWorkingSetBytes = 384L * MEBIBYTE,
            capabilities = setOf(
                KellvynStudioCapability.RASTER_IMAGE_EDIT,
                KellvynStudioCapability.ARTIFACT_STORAGE
            ),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "artifact_id",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = 64,
                    pattern = "^[a-f0-9]{64}$"
                ),
                KellvynToolArgumentSchema(
                    name = "task_id",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = 96,
                    pattern = "^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$"
                ),
                KellvynToolArgumentSchema(
                    name = "width",
                    type = KellvynToolArgumentType.INTEGER,
                    maxLength = 5,
                    minInteger = 1L,
                    maxInteger = MAX_DIMENSION.toLong()
                ),
                KellvynToolArgumentSchema(
                    name = "height",
                    type = KellvynToolArgumentType.INTEGER,
                    maxLength = 5,
                    minInteger = 1L,
                    maxInteger = MAX_DIMENSION.toLong()
                ),
                KellvynToolArgumentSchema(
                    name = "format",
                    type = KellvynToolArgumentType.ENUM,
                    maxLength = 4,
                    allowedValues = setOf("png", "jpeg", "webp")
                ),
                KellvynToolArgumentSchema(
                    name = "quality",
                    type = KellvynToolArgumentType.INTEGER,
                    required = false,
                    maxLength = 3,
                    minInteger = 1L,
                    maxInteger = 100L
                )
            )
        )

        private val MANIFEST = KellvynStudioModuleManifest(
            moduleId = MODULE_ID,
            version = 1,
            capabilities = setOf(
                KellvynStudioCapability.VISUAL_ANALYSIS,
                KellvynStudioCapability.RASTER_IMAGE_EDIT,
                KellvynStudioCapability.ARTIFACT_STORAGE
            ),
            supportedExtensions = setOf("png", "jpg", "jpeg", "webp"),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedLoadWorkingSetBytes = 8L * MEBIBYTE,
            estimatedResidentBytes = 2L * MEBIBYTE,
            permissions = setOf(KellvynStudioPermission.PRIVATE_ARTIFACT_WRITE),
            toolSpecs = listOf(INSPECT_SPEC, RESIZE_SPEC)
        )

        fun definition(
            context: Context,
            artifacts: KellvynArtifactStore
        ) = KellvynStudioModuleDefinition(MANIFEST) {
            KellvynImageStudio(context.applicationContext, artifacts)
        }
    }
}
