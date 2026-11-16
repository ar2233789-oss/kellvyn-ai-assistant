package com.kellvyn.assistant.neural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Primeiro protótipo neural autoral da Kellvyn.
 *
 * Preditor de próximo byte com embeddings treináveis, janela de contexto,
 * camada oculta não linear e saída softmax. Os parâmetros são inicializados
 * neste código; nenhum peso externo ou modelo pré-treinado é utilizado.
 *
 * Este protótipo valida o pipeline de aprendizado. Não é um assistente geral.
 */
internal class KellvynByteBrain private constructor(
    val config: Config,
    private val embeddings: FloatArray,
    private val hiddenWeights: FloatArray,
    private val hiddenBias: FloatArray,
    private val outputWeights: FloatArray,
    private val outputBias: FloatArray,
    var trainingSteps: Long
) {
    data class Config(
        val contextLength: Int = 8,
        val embeddingSize: Int = 12,
        val hiddenSize: Int = 32,
        val seed: Long = 20261116L
    ) {
        init {
            require(contextLength in 1..64) { "contextLength fora do limite." }
            require(embeddingSize in 2..128) { "embeddingSize fora do limite." }
            require(hiddenSize in 4..256) { "hiddenSize fora do limite." }
        }
    }

    data class TrainingReport(
        val initialLoss: Double,
        val finalLoss: Double,
        val examples: Int,
        val epochs: Int,
        val totalSteps: Long
    )

    data class GradientCheckReport(
        val maxAbsoluteError: Double,
        val maxRelativeError: Double,
        val checkedParameters: Int
    )

    private data class Gradients(
        val embeddings: FloatArray,
        val hiddenWeights: FloatArray,
        val hiddenBias: FloatArray,
        val outputWeights: FloatArray,
        val outputBias: FloatArray
    )

    private data class ParameterGroup(
        val values: FloatArray,
        val gradients: FloatArray
    )

    private data class ForwardPass(
        val hidden: FloatArray,
        val probabilities: FloatArray
    )

    private val embeddingRows = VOCABULARY_SIZE + 1
    private val hiddenInputSize = config.contextLength * config.embeddingSize

    init {
        require(embeddings.size == embeddingRows * config.embeddingSize)
        require(hiddenWeights.size == hiddenInputSize * config.hiddenSize)
        require(hiddenBias.size == config.hiddenSize)
        require(outputWeights.size == config.hiddenSize * VOCABULARY_SIZE)
        require(outputBias.size == VOCABULARY_SIZE)
        require(trainingSteps >= 0)
    }

    companion object {
        const val VOCABULARY_SIZE = 256
        private const val PAD_TOKEN = VOCABULARY_SIZE
        private const val CHECKPOINT_MAGIC = 0x4B4C564E
        private const val CHECKPOINT_VERSION = 1
        private const val CHECKSUM_BYTES = 32
        private const val GRADIENT_CLIP = 5.0

        /** Creates every trainable parameter from this project's seeded initializer. */
        fun create(config: Config = Config()): KellvynByteBrain {
            val random = Random(config.seed)
            val embeddingRows = VOCABULARY_SIZE + 1
            val hiddenInputSize = config.contextLength * config.embeddingSize
            val embeddings = FloatArray(embeddingRows * config.embeddingSize)
            val hiddenWeights = FloatArray(hiddenInputSize * config.hiddenSize)
            val hiddenBias = FloatArray(config.hiddenSize)
            val outputWeights = FloatArray(config.hiddenSize * VOCABULARY_SIZE)
            val outputBias = FloatArray(VOCABULARY_SIZE)

            val embeddingLimit = sqrt(3.0 / config.embeddingSize.toDouble()).toFloat()
            val hiddenLimit = sqrt(6.0 / (hiddenInputSize + config.hiddenSize).toDouble()).toFloat()
            val outputLimit = sqrt(
                6.0 / (config.hiddenSize + VOCABULARY_SIZE).toDouble()
            ).toFloat()

            fillUniform(embeddings, random, embeddingLimit)
            fillUniform(hiddenWeights, random, hiddenLimit)
            fillUniform(outputWeights, random, outputLimit)

            return KellvynByteBrain(
                config = config,
                embeddings = embeddings,
                hiddenWeights = hiddenWeights,
                hiddenBias = hiddenBias,
                outputWeights = outputWeights,
                outputBias = outputBias,
                trainingSteps = 0L
            )
        }

        /** Restores only this project's versioned KLVN checkpoint format. */
        fun readCheckpoint(input: InputStream): KellvynByteBrain {
            val checkpoint = input.readBytes()
            require(checkpoint.size > CHECKSUM_BYTES) { "Checkpoint Kellvyn incompleto." }

            val payloadSize = checkpoint.size - CHECKSUM_BYTES
            val payload = checkpoint.copyOfRange(0, payloadSize)
            val storedDigest = checkpoint.copyOfRange(payloadSize, checkpoint.size)
            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
            require(MessageDigest.isEqual(storedDigest, actualDigest)) {
                "A integridade do checkpoint Kellvyn não confere."
            }

            val data = DataInputStream(ByteArrayInputStream(payload))
            require(data.readInt() == CHECKPOINT_MAGIC) {
                "Checkpoint não pertence ao formato próprio da Kellvyn."
            }
            require(data.readInt() == CHECKPOINT_VERSION) {
                "Versão de checkpoint Kellvyn não suportada."
            }
            val config = Config(
                contextLength = data.readInt(),
                embeddingSize = data.readInt(),
                hiddenSize = data.readInt(),
                seed = data.readLong()
            )
            val steps = data.readLong()
            require(steps >= 0) { "Contador de treinamento inválido." }

            val embeddingCount = (VOCABULARY_SIZE + 1) * config.embeddingSize
            val hiddenWeightCount = config.contextLength * config.embeddingSize * config.hiddenSize
            val outputWeightCount = config.hiddenSize * VOCABULARY_SIZE

            val model = KellvynByteBrain(
                config,
                readFloatArray(data, embeddingCount),
                readFloatArray(data, hiddenWeightCount),
                readFloatArray(data, config.hiddenSize),
                readFloatArray(data, outputWeightCount),
                readFloatArray(data, VOCABULARY_SIZE),
                steps
            )
            require(data.available() == 0) { "Checkpoint contém dados inesperados." }
            return model
        }

        private fun fillUniform(target: FloatArray, random: Random, limit: Float) {
            for (i in target.indices) {
                target[i] = ((random.nextDouble() * 2.0 - 1.0) * limit).toFloat()
            }
        }

        private fun readFloatArray(data: DataInputStream, expectedSize: Int): FloatArray {
            val size = data.readInt()
            require(size == expectedSize) { "Dimensão inválida no checkpoint Kellvyn." }
            return FloatArray(size) {
                data.readFloat().also { value ->
                    require(value.isFinite()) { "Checkpoint contém peso não finito." }
                }
            }
        }
    }

    /**
     * Trains next-byte prediction from UTF-8 data supplied to this model.
     * The same text is revisited for each epoch; no hidden dataset is read.
     */
    fun trainText(
        text: String,
        epochs: Int,
        learningRate: Float = 0.025f
    ): TrainingReport {
        require(text.isNotEmpty()) { "O texto de treinamento está vazio." }
        require(epochs in 1..10_000) { "Número de épocas fora do limite." }
        require(learningRate.isFinite() && learningRate in 0.00001f..0.2f) {
            "Taxa de aprendizado fora do limite."
        }

        val tokens = KellvynByteTokenizer.encode(text)
        require(tokens.isNotEmpty())
        val initialLoss = evaluateLoss(tokens)
        repeat(epochs) {
            for (targetIndex in tokens.indices) {
                val context = contextFor(tokens, targetIndex)
                trainOne(context, tokens[targetIndex], learningRate)
                trainingSteps++
            }
        }
        val finalLoss = evaluateLoss(tokens)
        return TrainingReport(
            initialLoss = initialLoss,
            finalLoss = finalLoss,
            examples = tokens.size,
            epochs = epochs,
            totalSteps = trainingSteps
        )
    }

    fun evaluateLoss(text: String): Double {
        require(text.isNotEmpty()) { "O texto de avaliação está vazio." }
        return evaluateLoss(KellvynByteTokenizer.encode(text))
    }

    private fun evaluateLoss(tokens: IntArray): Double {
        require(tokens.isNotEmpty())
        var total = 0.0
        for (targetIndex in tokens.indices) {
            val pass = forward(contextFor(tokens, targetIndex))
            val probability = max(pass.probabilities[tokens[targetIndex]].toDouble(), 1.0e-12)
            total -= kotlin.math.ln(probability)
        }
        return total / tokens.size.toDouble()
    }


    /**
     * Compares analytic backpropagation with central finite differences.
     * This is a bounded diagnostic and does not change the model parameters.
     */
    internal fun checkGradients(
        contextTokens: IntArray,
        target: Int,
        epsilon: Double = 0.001,
        maxCoordinatesPerGroup: Int = 4
    ): GradientCheckReport {
        require(contextTokens.size <= config.contextLength)
        require(contextTokens.all { it in 0 until VOCABULARY_SIZE })
        require(target in 0 until VOCABULARY_SIZE)
        require(epsilon in 0.00001..0.01)
        require(maxCoordinatesPerGroup in 1..16)

        val context = IntArray(config.contextLength) { PAD_TOKEN }
        val offset = config.contextLength - contextTokens.size
        for (i in contextTokens.indices) context[offset + i] = contextTokens[i]

        val gradients = computeGradients(context, target)
        val groups = listOf(
            ParameterGroup(embeddings, gradients.embeddings),
            ParameterGroup(hiddenWeights, gradients.hiddenWeights),
            ParameterGroup(hiddenBias, gradients.hiddenBias),
            ParameterGroup(outputWeights, gradients.outputWeights),
            ParameterGroup(outputBias, gradients.outputBias)
        )

        var maxAbsoluteError = 0.0
        var maxRelativeError = 0.0
        var checked = 0

        for (group in groups) {
            val candidates = group.gradients.indices.filter {
                kotlin.math.abs(group.gradients[it]) > 1.0e-7f
            }
            val samples = minOf(maxCoordinatesPerGroup, candidates.size)
            for (sample in 0 until samples) {
                val candidatePosition = if (samples == 1) 0
                    else sample * (candidates.size - 1) / (samples - 1)
                val parameterIndex = candidates[candidatePosition]
                val original = group.values[parameterIndex]
                group.values[parameterIndex] = (original.toDouble() + epsilon).toFloat()
                val plusLoss = singleByteLoss(context, target)
                group.values[parameterIndex] = (original.toDouble() - epsilon).toFloat()
                val minusLoss = singleByteLoss(context, target)
                group.values[parameterIndex] = original

                val numericGradient = (plusLoss - minusLoss) / (2.0 * epsilon)
                val analyticGradient = group.gradients[parameterIndex].toDouble()
                val absoluteError = kotlin.math.abs(numericGradient - analyticGradient)
                val relativeError = absoluteError /
                    max(1.0e-7, kotlin.math.abs(numericGradient) + kotlin.math.abs(analyticGradient))

                if (!numericGradient.isFinite() || !analyticGradient.isFinite()) {
                    throw IllegalStateException("Gradiente não finito na verificação numérica.")
                }
                maxAbsoluteError = max(maxAbsoluteError, absoluteError)
                maxRelativeError = max(maxRelativeError, relativeError)
                checked++
            }
        }

        require(checked > 0) { "Não houve parâmetros ativos para verificar." }
        return GradientCheckReport(maxAbsoluteError, maxRelativeError, checked)
    }

    private fun singleByteLoss(context: IntArray, target: Int): Double {
        val probability = max(forward(context).probabilities[target].toDouble(), 1.0e-12)
        return -kotlin.math.ln(probability)
    }

    /** Predicts a byte ID from up to contextLength previous byte IDs. */
    fun predictNext(contextTokens: IntArray): Int {
        require(contextTokens.size <= config.contextLength)
        require(contextTokens.all { it in 0 until VOCABULARY_SIZE })
        val padded = IntArray(config.contextLength) { PAD_TOKEN }
        val offset = config.contextLength - contextTokens.size
        for (i in contextTokens.indices) padded[offset + i] = contextTokens[i]
        val probabilities = forward(padded).probabilities
        var bestIndex = 0
        for (i in 1 until probabilities.size) {
            if (probabilities[i] > probabilities[bestIndex]) bestIndex = i
        }
        return bestIndex
    }

    /** Greedy byte generation for diagnostics; not a chat-quality guarantee. */
    fun generate(prefix: String, maxNewBytes: Int): String {
        require(maxNewBytes in 0..4096)
        val all = KellvynByteTokenizer.encode(prefix).toMutableList()
        val originalSize = all.size
        repeat(maxNewBytes) {
            val recent = all.takeLast(config.contextLength).toIntArray()
            all += predictNext(recent)
        }
        val generated = IntArray(all.size - originalSize) { all[originalSize + it] }
        return KellvynByteTokenizer.decode(generated)
    }

    fun writeCheckpoint(output: OutputStream) {
        val payloadBuffer = ByteArrayOutputStream()
        val data = DataOutputStream(payloadBuffer)
        data.writeInt(CHECKPOINT_MAGIC)
        data.writeInt(CHECKPOINT_VERSION)
        data.writeInt(config.contextLength)
        data.writeInt(config.embeddingSize)
        data.writeInt(config.hiddenSize)
        data.writeLong(config.seed)
        data.writeLong(trainingSteps)
        writeFloatArray(data, embeddings)
        writeFloatArray(data, hiddenWeights)
        writeFloatArray(data, hiddenBias)
        writeFloatArray(data, outputWeights)
        writeFloatArray(data, outputBias)
        data.flush()

        val payload = payloadBuffer.toByteArray()
        val checksum = MessageDigest.getInstance("SHA-256").digest(payload)
        output.write(payload)
        output.write(checksum)
        output.flush()
    }

    /** Stable fingerprint for tests and checkpoint provenance. */
    fun parameterFingerprint(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { data ->
            writeFloatArray(data, embeddings)
            writeFloatArray(data, hiddenWeights)
            writeFloatArray(data, hiddenBias)
            writeFloatArray(data, outputWeights)
            writeFloatArray(data, outputBias)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun forward(context: IntArray): ForwardPass {
        require(context.size == config.contextLength)
        val hidden = FloatArray(config.hiddenSize)
        for (unit in 0 until config.hiddenSize) {
            var sum = hiddenBias[unit]
            for (position in 0 until config.contextLength) {
                val embeddingOffset = context[position] * config.embeddingSize
                val positionOffset = position * config.embeddingSize
                for (dimension in 0 until config.embeddingSize) {
                    val weightIndex = (positionOffset + dimension) * config.hiddenSize + unit
                    sum += embeddings[embeddingOffset + dimension] * hiddenWeights[weightIndex]
                }
            }
            hidden[unit] = tanh(sum.toDouble()).toFloat()
        }

        val logits = FloatArray(VOCABULARY_SIZE)
        var maximum = Float.NEGATIVE_INFINITY
        for (token in 0 until VOCABULARY_SIZE) {
            var value = outputBias[token]
            for (unit in 0 until config.hiddenSize) {
                value += hidden[unit] * outputWeights[unit * VOCABULARY_SIZE + token]
            }
            logits[token] = value
            if (value > maximum) maximum = value
        }

        val probabilities = FloatArray(VOCABULARY_SIZE)
        var denominator = 0.0
        for (token in logits.indices) {
            val value = exp((logits[token] - maximum).toDouble())
            probabilities[token] = value.toFloat()
            denominator += value
        }
        require(denominator.isFinite() && denominator > 0.0) {
            "A normalização neural produziu um valor inválido."
        }
        for (token in probabilities.indices) {
            probabilities[token] = (probabilities[token].toDouble() / denominator).toFloat()
        }
        return ForwardPass(hidden, probabilities)
    }


    private fun trainOne(context: IntArray, target: Int, learningRate: Float) {
        val gradients = computeGradients(context, target)
        var squaredNorm = 0.0
        squaredNorm += squaredNorm(gradients.embeddings)
        squaredNorm += squaredNorm(gradients.hiddenWeights)
        squaredNorm += squaredNorm(gradients.hiddenBias)
        squaredNorm += squaredNorm(gradients.outputWeights)
        squaredNorm += squaredNorm(gradients.outputBias)

        val norm = sqrt(squaredNorm)
        val scale = if (norm > GRADIENT_CLIP) (GRADIENT_CLIP / norm).toFloat() else 1.0f
        val step = learningRate * scale

        applyGradient(embeddings, gradients.embeddings, step)
        applyGradient(hiddenWeights, gradients.hiddenWeights, step)
        applyGradient(hiddenBias, gradients.hiddenBias, step)
        applyGradient(outputWeights, gradients.outputWeights, step)
        applyGradient(outputBias, gradients.outputBias, step)
    }

    /** Analytic derivatives of the single-example cross-entropy objective. */
    private fun computeGradients(context: IntArray, target: Int): Gradients {
        val pass = forward(context)
        val outputGradient = pass.probabilities.copyOf()
        outputGradient[target] -= 1.0f

        val gradEmbeddings = FloatArray(embeddings.size)
        val gradHiddenWeights = FloatArray(hiddenWeights.size)
        val gradHiddenBias = FloatArray(hiddenBias.size)
        val gradOutputWeights = FloatArray(outputWeights.size)
        val gradOutputBias = outputGradient

        for (unit in 0 until config.hiddenSize) {
            val hiddenValue = pass.hidden[unit]
            val outputWeightOffset = unit * VOCABULARY_SIZE
            var hiddenGradient = 0.0f
            for (token in 0 until VOCABULARY_SIZE) {
                val gradient = outputGradient[token]
                gradOutputWeights[outputWeightOffset + token] = hiddenValue * gradient
                hiddenGradient += outputWeights[outputWeightOffset + token] * gradient
            }
            hiddenGradient *= 1.0f - hiddenValue * hiddenValue
            gradHiddenBias[unit] = hiddenGradient

            for (position in 0 until config.contextLength) {
                val embeddingOffset = context[position] * config.embeddingSize
                val positionOffset = position * config.embeddingSize
                for (dimension in 0 until config.embeddingSize) {
                    val embeddingIndex = embeddingOffset + dimension
                    val weightIndex =
                        (positionOffset + dimension) * config.hiddenSize + unit
                    gradHiddenWeights[weightIndex] =
                        embeddings[embeddingIndex] * hiddenGradient
                    gradEmbeddings[embeddingIndex] +=
                        hiddenWeights[weightIndex] * hiddenGradient
                }
            }
        }

        return Gradients(
            gradEmbeddings,
            gradHiddenWeights,
            gradHiddenBias,
            gradOutputWeights,
            gradOutputBias
        )
    }

    private fun contextFor(tokens: IntArray, targetIndex: Int): IntArray {
        val context = IntArray(config.contextLength) { PAD_TOKEN }
        for (position in 0 until config.contextLength) {
            val sourceIndex = targetIndex - config.contextLength + position
            if (sourceIndex >= 0) context[position] = tokens[sourceIndex]
        }
        return context
    }

    private fun squaredNorm(values: FloatArray): Double {
        var total = 0.0
        for (value in values) total += value.toDouble() * value.toDouble()
        return total
    }

    private fun applyGradient(weights: FloatArray, gradient: FloatArray, step: Float) {
        for (i in weights.indices) weights[i] -= step * gradient[i]
    }

    private fun writeFloatArray(data: DataOutputStream, values: FloatArray) {
        data.writeInt(values.size)
        for (value in values) data.writeFloat(value)
    }
}

/** Reversible UTF-8 byte tokenizer written for this Kellvyn prototype. */
internal object KellvynByteTokenizer {
    fun encode(text: String): IntArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return IntArray(bytes.size) { index -> bytes[index].toInt() and 0xff }
    }

    fun decode(tokenIds: IntArray): String {
        require(tokenIds.all { it in 0..255 })
        val bytes = ByteArray(tokenIds.size) { index -> tokenIds[index].toByte() }
        return String(bytes, Charsets.UTF_8)
    }
}
