package com.kellvyn.assistant.neural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * KBN-003 experimental decoder-only Transformer built from random Kellvyn-owned weights.
 *
 * This first trainable block has causal multi-head self-attention with RoPE, Pre-LayerNorm,
 * residual paths and a SwiGLU feed-forward sub-layer. It intentionally remains small and
 * separate from KBN-002 until gradient, learning, checkpoint and Android runtime gates pass.
 *
 * The implementation computes only the final position for next-token prediction. Keys and
 * values are recomputed for each step; a KV cache is a later optimization, not yet implemented.
 */
internal class KellvynTransformerLanguageModel private constructor(
    val config: Config,
    private val parameters: ParameterSet,
    private val firstMoment: ParameterSet,
    private val secondMoment: ParameterSet,
    var trainingSteps: Long,
    private var optimizerSteps: Long
) {
    data class Config(
        val vocabularySize: Int = 512,
        val contextLength: Int = 64,
        val embeddingSize: Int = 32,
        val headCount: Int = 4,
        val feedForwardSize: Int = 64,
        val seed: Long = 20261117L
    ) {
        init {
            require(vocabularySize in 256..8192)
            require(contextLength in 1..256)
            require(embeddingSize in 8..128)
            require(headCount in 1..8)
            require(embeddingSize % headCount == 0)
            require((embeddingSize / headCount) % 2 == 0) {
                "A dimensão de cada cabeça precisa ser par para RoPE."
            }
            require(feedForwardSize in 4..512)
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

    private data class ParameterSet(
        val tokenEmbeddings: FloatArray,
        val queryWeights: FloatArray,
        val keyWeights: FloatArray,
        val valueWeights: FloatArray,
        val attentionOutputWeights: FloatArray,
        val normAttentionGamma: FloatArray,
        val normAttentionBeta: FloatArray,
        val normFeedForwardGamma: FloatArray,
        val normFeedForwardBeta: FloatArray,
        val gateWeights: FloatArray,
        val upWeights: FloatArray,
        val downWeights: FloatArray,
        val outputWeights: FloatArray,
        val outputBias: FloatArray
    ) {
        fun arrays(): List<FloatArray> = listOf(
            tokenEmbeddings,
            queryWeights,
            keyWeights,
            valueWeights,
            attentionOutputWeights,
            normAttentionGamma,
            normAttentionBeta,
            normFeedForwardGamma,
            normFeedForwardBeta,
            gateWeights,
            upWeights,
            downWeights,
            outputWeights,
            outputBias
        )
    }

    private data class Normalized(
        val values: FloatArray,
        val centeredNormalized: FloatArray,
        val inverseStandardDeviation: Float
    )

    private data class ForwardPass(
        val normalizedInputs: Array<FloatArray>,
        val normalizedInputXhat: Array<FloatArray>,
        val normalizedInputInverseStd: FloatArray,
        val queryRotated: FloatArray,
        val keysRotated: Array<FloatArray>,
        val values: Array<FloatArray>,
        val attentionWeights: Array<FloatArray>,
        val attentionConcat: FloatArray,
        val residualAfterAttention: FloatArray,
        val normalizedMlp: FloatArray,
        val normalizedMlpXhat: FloatArray,
        val normalizedMlpInverseStd: Float,
        val gatePreActivation: FloatArray,
        val upProjection: FloatArray,
        val siluGate: FloatArray,
        val gatedIntermediate: FloatArray,
        val hidden: FloatArray,
        val probabilities: FloatArray
    )

    private data class Gradients(val parameters: ParameterSet)

    init {
        validateParameterShapes(parameters, config)
        validateParameterShapes(firstMoment, config)
        validateParameterShapes(secondMoment, config)
        require(trainingSteps >= 0L && optimizerSteps == trainingSteps)
    }

    companion object {
        private const val CHECKPOINT_MAGIC = 0x4B564E54 // "KVNT"
        private const val CHECKPOINT_VERSION = 1
        private const val CHECKSUM_BYTES = 32
        private const val MAX_CHECKPOINT_BYTES = 64 * 1024 * 1024
        private const val MAX_TRAINING_TOKENS = 100_000
        private const val MAX_TRAINING_STEPS_PER_CALL = 50_000L
        private const val GRADIENT_CLIP_NORM = 1.0
        private const val BETA1 = 0.9
        private const val BETA2 = 0.999
        private const val ADAM_EPSILON = 1.0e-8
        private const val DEFAULT_WEIGHT_DECAY = 0.01f
        private const val LAYER_NORM_EPSILON = 1.0e-5f
        private const val ROPE_BASE = 10_000.0

        fun createForTokenizer(
            tokenizer: KellvynBpeTokenizer,
            contextLength: Int = 64,
            embeddingSize: Int = 32,
            headCount: Int = 4,
            feedForwardSize: Int = 64,
            seed: Long = 20261117L
        ): KellvynTransformerLanguageModel = create(
            Config(
                vocabularySize = tokenizer.vocabularySize,
                contextLength = contextLength,
                embeddingSize = embeddingSize,
                headCount = headCount,
                feedForwardSize = feedForwardSize,
                seed = seed
            )
        )

        fun create(config: Config = Config()): KellvynTransformerLanguageModel {
            val random = Random(config.seed)
            val d = config.embeddingSize
            val v = config.vocabularySize
            val f = config.feedForwardSize

            val embeddingLimit = sqrt(3.0 / d).toFloat()
            val parameters = ParameterSet(
                tokenEmbeddings = randomArray(v * d, random, embeddingLimit),
                queryWeights = randomArray(d * d, random, xavierLimit(d, d)),
                keyWeights = randomArray(d * d, random, xavierLimit(d, d)),
                valueWeights = randomArray(d * d, random, xavierLimit(d, d)),
                attentionOutputWeights = randomArray(d * d, random, xavierLimit(d, d)),
                normAttentionGamma = FloatArray(d) { 1.0f },
                normAttentionBeta = FloatArray(d),
                normFeedForwardGamma = FloatArray(d) { 1.0f },
                normFeedForwardBeta = FloatArray(d),
                gateWeights = randomArray(d * f, random, xavierLimit(d, f)),
                upWeights = randomArray(d * f, random, xavierLimit(d, f)),
                downWeights = randomArray(f * d, random, xavierLimit(f, d)),
                outputWeights = randomArray(d * v, random, xavierLimit(d, v)),
                outputBias = FloatArray(v)
            )
            val zeros = zeroParameters(config)
            return KellvynTransformerLanguageModel(
                config = config,
                parameters = parameters,
                firstMoment = copyParameterSet(zeros),
                secondMoment = copyParameterSet(zeros),
                trainingSteps = 0L,
                optimizerSteps = 0L
            )
        }

        fun readCheckpoint(input: InputStream): KellvynTransformerLanguageModel {
            val bytes = readBounded(input)
            require(bytes.size in (CHECKSUM_BYTES + 48)..MAX_CHECKPOINT_BYTES) {
                "Checkpoint KBN-003 inválido ou acima do limite."
            }
            val payloadSize = bytes.size - CHECKSUM_BYTES
            val payload = bytes.copyOfRange(0, payloadSize)
            val storedDigest = bytes.copyOfRange(payloadSize, bytes.size)
            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
            require(MessageDigest.isEqual(storedDigest, actualDigest)) {
                "A integridade do checkpoint KBN-003 não confere."
            }

            val data = DataInputStream(ByteArrayInputStream(payload))
            require(data.readInt() == CHECKPOINT_MAGIC) {
                "Checkpoint não pertence ao Transformer Kellvyn."
            }
            require(data.readInt() == CHECKPOINT_VERSION) {
                "Versão do checkpoint KBN-003 não suportada."
            }
            val config = Config(
                vocabularySize = data.readInt(),
                contextLength = data.readInt(),
                embeddingSize = data.readInt(),
                headCount = data.readInt(),
                feedForwardSize = data.readInt(),
                seed = data.readLong()
            )
            val trainingSteps = data.readLong()
            val optimizerSteps = data.readLong()
            require(trainingSteps >= 0L && optimizerSteps == trainingSteps) {
                "Contadores do checkpoint KBN-003 inválidos."
            }

            val parameters = readParameterSet(data, config)
            val firstMoment = readParameterSet(data, config)
            val secondMoment = readParameterSet(data, config)
            require(data.available() == 0) {
                "Checkpoint KBN-003 contém dados extras."
            }
            return KellvynTransformerLanguageModel(
                config,
                parameters,
                firstMoment,
                secondMoment,
                trainingSteps,
                optimizerSteps
            )
        }

        private fun randomArray(size: Int, random: Random, limit: Float): FloatArray =
            FloatArray(size) {
                ((random.nextDouble() * 2.0 - 1.0) * limit).toFloat()
            }

        private fun xavierLimit(input: Int, output: Int): Float =
            sqrt(6.0 / (input + output)).toFloat()

        private fun zeroParameters(config: Config): ParameterSet {
            val d = config.embeddingSize
            val v = config.vocabularySize
            val f = config.feedForwardSize
            return ParameterSet(
                tokenEmbeddings = FloatArray(v * d),
                queryWeights = FloatArray(d * d),
                keyWeights = FloatArray(d * d),
                valueWeights = FloatArray(d * d),
                attentionOutputWeights = FloatArray(d * d),
                normAttentionGamma = FloatArray(d),
                normAttentionBeta = FloatArray(d),
                normFeedForwardGamma = FloatArray(d),
                normFeedForwardBeta = FloatArray(d),
                gateWeights = FloatArray(d * f),
                upWeights = FloatArray(d * f),
                downWeights = FloatArray(f * d),
                outputWeights = FloatArray(d * v),
                outputBias = FloatArray(v)
            )
        }

        private fun copyParameterSet(source: ParameterSet): ParameterSet {
            val a = source.arrays().map { it.copyOf() }
            return ParameterSet(
                a[0], a[1], a[2], a[3], a[4], a[5], a[6],
                a[7], a[8], a[9], a[10], a[11], a[12], a[13]
            )
        }

        private fun readBounded(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val value = input.read()
                    if (value < 0) break
                    require(total < MAX_CHECKPOINT_BYTES)
                    output.write(value)
                    total++
                } else {
                    require(total <= MAX_CHECKPOINT_BYTES - count) {
                        "Checkpoint KBN-003 acima do limite."
                    }
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            return output.toByteArray()
        }

        private fun readParameterSet(data: DataInputStream, config: Config): ParameterSet {
            val template = zeroParameters(config)
            val arrays = template.arrays().map { readFloatArray(data, it.size) }
            return ParameterSet(
                arrays[0], arrays[1], arrays[2], arrays[3], arrays[4],
                arrays[5], arrays[6], arrays[7], arrays[8], arrays[9],
                arrays[10], arrays[11], arrays[12], arrays[13]
            )
        }

        private fun readFloatArray(data: DataInputStream, expectedSize: Int): FloatArray {
            val count = data.readInt()
            require(count == expectedSize) {
                "Dimensão de parâmetro inválida no checkpoint KBN-003."
            }
            return FloatArray(count) {
                data.readFloat().also { value ->
                    require(value.isFinite()) {
                        "Checkpoint KBN-003 contém parâmetro não finito."
                    }
                }
            }
        }
    }

    fun trainTokens(
        tokenIds: IntArray,
        epochs: Int,
        learningRate: Float = 0.003f,
        weightDecay: Float = DEFAULT_WEIGHT_DECAY,
        cancellationCheck: (() -> Unit)? = null
    ): TrainingReport {
        require(tokenIds.size in 2..MAX_TRAINING_TOKENS)
        require(tokenIds.all { it in 0 until config.vocabularySize })
        require(epochs in 1..10_000)
        require((tokenIds.size - 1L) * epochs.toLong() <= MAX_TRAINING_STEPS_PER_CALL)
        validateOptimizerArguments(learningRate, weightDecay)
        cancellationCheck?.invoke()
        val initialLoss = evaluateLoss(tokenIds)

        repeat(epochs) {
            for (targetIndex in 1 until tokenIds.size) {
                if (targetIndex and 31 == 0) cancellationCheck?.invoke()
                val start = max(0, targetIndex - config.contextLength)
                trainOne(
                    tokenIds.copyOfRange(start, targetIndex),
                    tokenIds[targetIndex],
                    learningRate,
                    weightDecay
                )
            }
        }
        cancellationCheck?.invoke()
        val finalLoss = evaluateLoss(tokenIds)
        return TrainingReport(
            initialLoss,
            finalLoss,
            tokenIds.size - 1,
            epochs,
            trainingSteps
        )
    }

    fun trainCompletion(
        promptTokenIds: IntArray,
        completionTokenIds: IntArray,
        epochs: Int,
        learningRate: Float = 0.003f,
        weightDecay: Float = DEFAULT_WEIGHT_DECAY,
        cancellationCheck: (() -> Unit)? = null
    ): TrainingReport {
        require(promptTokenIds.isNotEmpty() && completionTokenIds.isNotEmpty())
        require(promptTokenIds.size.toLong() + completionTokenIds.size.toLong() <= MAX_TRAINING_TOKENS)
        val combined = promptTokenIds + completionTokenIds
        require(combined.all { it in 0 until config.vocabularySize })
        require(epochs in 1..10_000)
        require(completionTokenIds.size.toLong() * epochs.toLong() <= MAX_TRAINING_STEPS_PER_CALL)
        validateOptimizerArguments(learningRate, weightDecay)
        cancellationCheck?.invoke()
        val initialLoss = evaluateCompletionLoss(promptTokenIds, completionTokenIds)

        repeat(epochs) {
            for (targetIndex in promptTokenIds.size until combined.size) {
                if ((targetIndex - promptTokenIds.size) and 31 == 0) cancellationCheck?.invoke()
                val start = max(0, targetIndex - config.contextLength)
                trainOne(
                    combined.copyOfRange(start, targetIndex),
                    combined[targetIndex],
                    learningRate,
                    weightDecay
                )
            }
        }
        cancellationCheck?.invoke()
        val finalLoss = evaluateCompletionLoss(promptTokenIds, completionTokenIds)
        return TrainingReport(
            initialLoss,
            finalLoss,
            completionTokenIds.size,
            epochs,
            trainingSteps
        )
    }

    fun evaluateLoss(tokenIds: IntArray): Double {
        require(tokenIds.size in 2..MAX_TRAINING_TOKENS)
        require(tokenIds.all { it in 0 until config.vocabularySize })
        var total = 0.0
        for (targetIndex in 1 until tokenIds.size) {
            val start = max(0, targetIndex - config.contextLength)
            total -= ln(max(
                forward(tokenIds.copyOfRange(start, targetIndex)).probabilities[tokenIds[targetIndex]].toDouble(),
                1.0e-12
            ))
        }
        return total / (tokenIds.size - 1).toDouble()
    }

    fun evaluateCompletionLoss(promptTokenIds: IntArray, completionTokenIds: IntArray): Double {
        require(promptTokenIds.isNotEmpty() && completionTokenIds.isNotEmpty())
        require(promptTokenIds.size.toLong() + completionTokenIds.size.toLong() <= MAX_TRAINING_TOKENS)
        val combined = promptTokenIds + completionTokenIds
        require(combined.all { it in 0 until config.vocabularySize })
        var total = 0.0
        for (targetIndex in promptTokenIds.size until combined.size) {
            val start = max(0, targetIndex - config.contextLength)
            val context = combined.copyOfRange(start, targetIndex)
            val probability = max(forward(context).probabilities[combined[targetIndex]].toDouble(), 1.0e-12)
            total -= ln(probability)
        }
        return total / completionTokenIds.size.toDouble()
    }

    fun predictNext(contextTokenIds: IntArray): Int {
        require(contextTokenIds.isNotEmpty())
        require(contextTokenIds.size <= config.contextLength)
        require(contextTokenIds.all { it in 0 until config.vocabularySize })
        val probabilities = forward(contextTokenIds).probabilities
        var best = 0
        for (index in 1 until probabilities.size) {
            if (probabilities[index] > probabilities[best]) best = index
        }
        return best
    }

    fun generate(prefixTokenIds: IntArray, maxNewTokens: Int): IntArray {
        require(prefixTokenIds.isNotEmpty())
        require(prefixTokenIds.all { it in 0 until config.vocabularySize })
        require(maxNewTokens in 0..4096)
        val output = prefixTokenIds.toMutableList()
        repeat(maxNewTokens) {
            output += predictNext(output.takeLast(config.contextLength).toIntArray())
        }
        return output.toIntArray()
    }

    fun writeCheckpoint(output: OutputStream) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeInt(CHECKPOINT_MAGIC)
            data.writeInt(CHECKPOINT_VERSION)
            data.writeInt(config.vocabularySize)
            data.writeInt(config.contextLength)
            data.writeInt(config.embeddingSize)
            data.writeInt(config.headCount)
            data.writeInt(config.feedForwardSize)
            data.writeLong(config.seed)
            data.writeLong(trainingSteps)
            data.writeLong(optimizerSteps)
            for (set in listOf(parameters, firstMoment, secondMoment)) {
                for (array in set.arrays()) writeFloatArray(data, array)
            }
        }
        val payload = buffer.toByteArray()
        require(payload.size + CHECKSUM_BYTES <= MAX_CHECKPOINT_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        output.write(payload)
        output.write(digest)
        output.flush()
    }

    fun parameterFingerprint(): String {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            for (array in parameters.arrays()) writeFloatArray(data, array)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(buffer.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    internal fun checkGradients(
        contextTokenIds: IntArray,
        targetTokenId: Int,
        epsilon: Double = 0.001,
        maxCoordinatesPerGroup: Int = 3
    ): GradientCheckReport {
        require(contextTokenIds.isNotEmpty() && contextTokenIds.size <= config.contextLength)
        require(contextTokenIds.all { it in 0 until config.vocabularySize })
        require(targetTokenId in 0 until config.vocabularySize)
        require(epsilon in 0.00001..0.01)
        require(maxCoordinatesPerGroup in 1..8)

        val gradients = computeGradients(contextTokenIds, targetTokenId).parameters
        var maxAbsolute = 0.0
        var maxRelative = 0.0
        var checked = 0
        for ((values, gradient) in parameters.arrays().zip(gradients.arrays())) {
            val candidates = gradient.indices.filter { abs(gradient[it]) > 1.0e-8f }
            val samples = minOf(maxCoordinatesPerGroup, candidates.size)
            for (sample in 0 until samples) {
                val candidatePosition = if (samples == 1) 0
                    else sample * (candidates.size - 1) / (samples - 1)
                val parameterIndex = candidates[candidatePosition]
                val original = values[parameterIndex]
                values[parameterIndex] = (original.toDouble() + epsilon).toFloat()
                val plus = singleLoss(contextTokenIds, targetTokenId)
                values[parameterIndex] = (original.toDouble() - epsilon).toFloat()
                val minus = singleLoss(contextTokenIds, targetTokenId)
                values[parameterIndex] = original

                val numeric = (plus - minus) / (2.0 * epsilon)
                val analytic = gradient[parameterIndex].toDouble()
                require(numeric.isFinite() && analytic.isFinite())
                val absolute = abs(numeric - analytic)
                val relative = absolute / max(1.0e-7, abs(numeric) + abs(analytic))
                maxAbsolute = max(maxAbsolute, absolute)
                maxRelative = max(maxRelative, relative)
                checked++
            }
        }
        require(checked > 0)
        return GradientCheckReport(maxAbsolute, maxRelative, checked)
    }

    private fun validateOptimizerArguments(learningRate: Float, weightDecay: Float) {
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f)
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f)
    }

    private fun trainOne(
        contextTokenIds: IntArray,
        targetTokenId: Int,
        learningRate: Float,
        weightDecay: Float
    ) {
        val gradients = computeGradients(contextTokenIds, targetTokenId).parameters
        var squaredNorm = 0.0
        for (array in gradients.arrays()) {
            for (value in array) squaredNorm += value.toDouble() * value.toDouble()
        }
        val norm = sqrt(squaredNorm)
        if (norm > GRADIENT_CLIP_NORM) {
            val scale = GRADIENT_CLIP_NORM / norm
            for (array in gradients.arrays()) {
                for (index in array.indices) array[index] = (array[index] * scale).toFloat()
            }
        }

        optimizerSteps++
        trainingSteps++
        val beta1Correction = 1.0 - BETA1.power(optimizerSteps)
        val beta2Correction = 1.0 - BETA2.power(optimizerSteps)
        val parameterArrays = parameters.arrays()
        val gradientArrays = gradients.arrays()
        val firstArrays = firstMoment.arrays()
        val secondArrays = secondMoment.arrays()

        for (group in parameterArrays.indices) {
            val values = parameterArrays[group]
            val gradient = gradientArrays[group]
            val first = firstArrays[group]
            val second = secondArrays[group]
            for (index in values.indices) {
                val g = gradient[index].toDouble()
                first[index] = (BETA1 * first[index] + (1.0 - BETA1) * g).toFloat()
                second[index] = (BETA2 * second[index] + (1.0 - BETA2) * g * g).toFloat()
                val firstHat = first[index].toDouble() / beta1Correction
                val secondHat = second[index].toDouble() / beta2Correction
                val update = firstHat / (sqrt(secondHat) + ADAM_EPSILON) +
                    weightDecay.toDouble() * values[index]
                values[index] = (values[index] - learningRate * update).toFloat()
            }
        }
    }

    private fun computeGradients(contextTokenIds: IntArray, targetTokenId: Int): Gradients {
        val pass = forward(contextTokenIds)
        val d = config.embeddingSize
        val v = config.vocabularySize
        val f = config.feedForwardSize
        val h = config.headCount
        val headDim = d / h
        val length = contextTokenIds.size
        val last = length - 1
        val inverseAttentionScale = (1.0 / sqrt(headDim.toDouble())).toFloat()

        val dTokenEmbeddings = FloatArray(parameters.tokenEmbeddings.size)
        val dQueryWeights = FloatArray(parameters.queryWeights.size)
        val dKeyWeights = FloatArray(parameters.keyWeights.size)
        val dValueWeights = FloatArray(parameters.valueWeights.size)
        val dAttentionOutputWeights = FloatArray(parameters.attentionOutputWeights.size)
        val dNormAttentionGamma = FloatArray(d)
        val dNormAttentionBeta = FloatArray(d)
        val dNormFeedForwardGamma = FloatArray(d)
        val dNormFeedForwardBeta = FloatArray(d)
        val dGateWeights = FloatArray(parameters.gateWeights.size)
        val dUpWeights = FloatArray(parameters.upWeights.size)
        val dDownWeights = FloatArray(parameters.downWeights.size)
        val dOutputWeights = FloatArray(parameters.outputWeights.size)
        val dOutputBias = pass.probabilities.copyOf()
        dOutputBias[targetTokenId] -= 1.0f

        val dHidden = FloatArray(d)
        for (dimension in 0 until d) {
            val offset = dimension * v
            for (token in 0 until v) {
                val gradient = dOutputBias[token]
                dOutputWeights[offset + token] = pass.hidden[dimension] * gradient
                dHidden[dimension] += parameters.outputWeights[offset + token] * gradient
            }
        }

        val dResidualAfterAttention = dHidden.copyOf()
        val dGatedIntermediate = FloatArray(f)
        for (dimension in 0 until d) {
            val gradient = dHidden[dimension]
            val offset = dimension * f
            for (unit in 0 until f) {
                dDownWeights[unit * d + dimension] =
                    pass.gatedIntermediate[unit] * gradient
                dGatedIntermediate[unit] += parameters.downWeights[unit * d + dimension] * gradient
            }
        }

        val dGatePreActivation = FloatArray(f)
        val dUpProjection = FloatArray(f)
        for (unit in 0 until f) {
            dGatePreActivation[unit] = dGatedIntermediate[unit] *
                pass.upProjection[unit] * siluDerivative(pass.gatePreActivation[unit])
            dUpProjection[unit] = dGatedIntermediate[unit] * pass.siluGate[unit]
        }

        val dNormalizedMlp = FloatArray(d)
        for (dimension in 0 until d) {
            val offset = dimension * f
            for (unit in 0 until f) {
                dGateWeights[offset + unit] =
                    pass.normalizedMlp[dimension] * dGatePreActivation[unit]
                dUpWeights[offset + unit] =
                    pass.normalizedMlp[dimension] * dUpProjection[unit]
                dNormalizedMlp[dimension] +=
                    parameters.gateWeights[offset + unit] * dGatePreActivation[unit] +
                    parameters.upWeights[offset + unit] * dUpProjection[unit]
            }
        }

        val dResidualFromNorm2 = layerNormBackward(
            xhat = pass.normalizedMlpXhat,
            inverseStd = pass.normalizedMlpInverseStd,
            gamma = parameters.normFeedForwardGamma,
            upstream = dNormalizedMlp,
            dGamma = dNormFeedForwardGamma,
            dBeta = dNormFeedForwardBeta
        )
        for (dimension in 0 until d) {
            dResidualAfterAttention[dimension] += dResidualFromNorm2[dimension]
        }

        val dAttentionConcat = FloatArray(d)
        for (dimension in 0 until d) {
            val offset = dimension * d
            for (output in 0 until d) {
                dAttentionOutputWeights[offset + output] =
                    pass.attentionConcat[dimension] * dResidualAfterAttention[output]
                dAttentionConcat[dimension] +=
                    parameters.attentionOutputWeights[offset + output] *
                    dResidualAfterAttention[output]
            }
        }

        val dQueryRotated = FloatArray(d)
        val dKeysRotated = Array(length) { FloatArray(d) }
        val dValues = Array(length) { FloatArray(d) }

        for (head in 0 until h) {
            val headOffset = head * headDim
            val dAttentionProbability = FloatArray(length)
            for (position in 0 until length) {
                val attention = pass.attentionWeights[head][position]
                var derivative = 0.0f
                for (dimension in 0 until headDim) {
                    val feature = headOffset + dimension
                    derivative += dAttentionConcat[feature] * pass.values[position][feature]
                    dValues[position][feature] += attention * dAttentionConcat[feature]
                }
                dAttentionProbability[position] = derivative
            }

            var weightedDerivative = 0.0
            for (position in 0 until length) {
                weightedDerivative +=
                    pass.attentionWeights[head][position].toDouble() *
                    dAttentionProbability[position].toDouble()
            }
            for (position in 0 until length) {
                val attention = pass.attentionWeights[head][position]
                val dScore = attention *
                    (dAttentionProbability[position] - weightedDerivative.toFloat())
                for (dimension in 0 until headDim) {
                    val feature = headOffset + dimension
                    dQueryRotated[feature] +=
                        dScore * pass.keysRotated[position][feature] * inverseAttentionScale
                    dKeysRotated[position][feature] =
                        dScore * pass.queryRotated[feature] * inverseAttentionScale
                }
            }
        }

        val dQueryRaw = dQueryRotated.copyOf()
        rotateInPlace(dQueryRaw, last, inverse = true)
        val dKeysRaw = Array(length) { position ->
            dKeysRotated[position].copyOf().also { rotateInPlace(it, position, inverse = true) }
        }

        val dNormalizedInputs = Array(length) { FloatArray(d) }
        for (inputDimension in 0 until d) {
            val queryOffset = inputDimension * d
            for (projectionDimension in 0 until d) {
                dQueryWeights[queryOffset + projectionDimension] =
                    pass.normalizedInputs[last][inputDimension] *
                    dQueryRaw[projectionDimension]
                dNormalizedInputs[last][inputDimension] +=
                    parameters.queryWeights[queryOffset + projectionDimension] *
                    dQueryRaw[projectionDimension]
            }
        }

        for (position in 0 until length) {
            for (inputDimension in 0 until d) {
                val offset = inputDimension * d
                for (projectionDimension in 0 until d) {
                    val keyGradient = dKeysRaw[position][projectionDimension]
                    val valueGradient = dValues[position][projectionDimension]
                    dKeyWeights[offset + projectionDimension] +=
                        pass.normalizedInputs[position][inputDimension] * keyGradient
                    dValueWeights[offset + projectionDimension] +=
                        pass.normalizedInputs[position][inputDimension] * valueGradient
                    dNormalizedInputs[position][inputDimension] +=
                        parameters.keyWeights[offset + projectionDimension] * keyGradient +
                        parameters.valueWeights[offset + projectionDimension] * valueGradient
                }
            }
        }

        for (position in 0 until length) {
            val inputGradient = layerNormBackward(
                xhat = pass.normalizedInputXhat[position],
                inverseStd = pass.normalizedInputInverseStd[position],
                gamma = parameters.normAttentionGamma,
                upstream = dNormalizedInputs[position],
                dGamma = dNormAttentionGamma,
                dBeta = dNormAttentionBeta
            )
            val tokenOffset = contextTokenIds[position] * d
            for (dimension in 0 until d) {
                dTokenEmbeddings[tokenOffset + dimension] += inputGradient[dimension]
            }
        }

        val lastTokenOffset = contextTokenIds[last] * d
        for (dimension in 0 until d) {
            dTokenEmbeddings[lastTokenOffset + dimension] += dResidualAfterAttention[dimension]
        }

        return Gradients(
            ParameterSet(
                dTokenEmbeddings,
                dQueryWeights,
                dKeyWeights,
                dValueWeights,
                dAttentionOutputWeights,
                dNormAttentionGamma,
                dNormAttentionBeta,
                dNormFeedForwardGamma,
                dNormFeedForwardBeta,
                dGateWeights,
                dUpWeights,
                dDownWeights,
                dOutputWeights,
                dOutputBias
            )
        )
    }

    private fun forward(contextTokenIds: IntArray): ForwardPass {
        require(contextTokenIds.isNotEmpty() && contextTokenIds.size <= config.contextLength)
        val d = config.embeddingSize
        val v = config.vocabularySize
        val f = config.feedForwardSize
        val h = config.headCount
        val headDim = d / h
        val length = contextTokenIds.size
        val last = length - 1

        val inputEmbeddings = Array(length) { position ->
            val tokenId = contextTokenIds[position]
            require(tokenId in 0 until v)
            val offset = tokenId * d
            FloatArray(d) { dimension -> parameters.tokenEmbeddings[offset + dimension] }
        }
        val normalizedInputs = Array(length) { FloatArray(d) }
        val normalizedInputXhat = Array(length) { FloatArray(d) }
        val normalizedInputInverseStd = FloatArray(length)
        for (position in 0 until length) {
            val normalized = layerNorm(
                inputEmbeddings[position],
                parameters.normAttentionGamma,
                parameters.normAttentionBeta
            )
            normalizedInputs[position] = normalized.values
            normalizedInputXhat[position] = normalized.centeredNormalized
            normalizedInputInverseStd[position] = normalized.inverseStandardDeviation
        }

        val queryRotated = linear(
            normalizedInputs[last], parameters.queryWeights, d, d
        )
        rotateInPlace(queryRotated, last)
        val keysRotated = Array(length) { position ->
            linear(normalizedInputs[position], parameters.keyWeights, d, d)
                .also { rotateInPlace(it, position) }
        }
        val values = Array(length) { position ->
            linear(normalizedInputs[position], parameters.valueWeights, d, d)
        }

        val attentionWeights = Array(h) { FloatArray(length) }
        val attentionConcat = FloatArray(d)
        val scale = (1.0 / sqrt(headDim.toDouble())).toFloat()
        for (head in 0 until h) {
            val offset = head * headDim
            val scores = FloatArray(length)
            var maximum = Float.NEGATIVE_INFINITY
            for (position in 0 until length) {
                var score = 0.0f
                for (dimension in 0 until headDim) {
                    val feature = offset + dimension
                    score += queryRotated[feature] * keysRotated[position][feature]
                }
                scores[position] = score * scale
                if (scores[position] > maximum) maximum = scores[position]
            }
            var denominator = 0.0
            for (position in 0 until length) {
                val value = exp((scores[position] - maximum).toDouble())
                attentionWeights[head][position] = value.toFloat()
                denominator += value
            }
            require(denominator.isFinite() && denominator > 0.0)
            for (position in 0 until length) {
                attentionWeights[head][position] =
                    (attentionWeights[head][position].toDouble() / denominator).toFloat()
            }
            for (position in 0 until length) {
                val weight = attentionWeights[head][position]
                for (dimension in 0 until headDim) {
                    val feature = offset + dimension
                    attentionConcat[feature] += weight * values[position][feature]
                }
            }
        }

        val attentionProjection = linear(
            attentionConcat, parameters.attentionOutputWeights, d, d
        )
        val residualAfterAttention = FloatArray(d) { dimension ->
            inputEmbeddings[last][dimension] + attentionProjection[dimension]
        }
        val normalizedMlp = layerNorm(
            residualAfterAttention,
            parameters.normFeedForwardGamma,
            parameters.normFeedForwardBeta
        )
        val gatePreActivation = linear(
            normalizedMlp.values, parameters.gateWeights, d, f
        )
        val upProjection = linear(
            normalizedMlp.values, parameters.upWeights, d, f
        )
        val siluGate = FloatArray(f) { index -> silu(gatePreActivation[index]) }
        val gatedIntermediate = FloatArray(f) { index ->
            siluGate[index] * upProjection[index]
        }
        val ffnOutput = linear(
            gatedIntermediate, parameters.downWeights, f, d
        )
        val hidden = FloatArray(d) { dimension ->
            residualAfterAttention[dimension] + ffnOutput[dimension]
        }

        val logits = linear(hidden, parameters.outputWeights, d, v)
        for (token in 0 until v) logits[token] += parameters.outputBias[token]
        val probabilities = softmax(logits)

        return ForwardPass(
            normalizedInputs,
            normalizedInputXhat,
            normalizedInputInverseStd,
            queryRotated,
            keysRotated,
            values,
            attentionWeights,
            attentionConcat,
            residualAfterAttention,
            normalizedMlp.values,
            normalizedMlp.centeredNormalized,
            normalizedMlp.inverseStandardDeviation,
            gatePreActivation,
            upProjection,
            siluGate,
            gatedIntermediate,
            hidden,
            probabilities
        )
    }

    private fun layerNorm(
        input: FloatArray,
        gamma: FloatArray,
        beta: FloatArray
    ): Normalized {
        val size = input.size
        var mean = 0.0
        for (value in input) mean += value.toDouble()
        mean /= size.toDouble()
        var variance = 0.0
        for (value in input) {
            val centered = value.toDouble() - mean
            variance += centered * centered
        }
        variance /= size.toDouble()
        val inverseStd = (1.0 / sqrt(variance + LAYER_NORM_EPSILON)).toFloat()
        val xhat = FloatArray(size)
        val output = FloatArray(size)
        for (index in 0 until size) {
            xhat[index] = ((input[index].toDouble() - mean) * inverseStd).toFloat()
            output[index] = xhat[index] * gamma[index] + beta[index]
        }
        return Normalized(output, xhat, inverseStd)
    }

    private fun layerNormBackward(
        xhat: FloatArray,
        inverseStd: Float,
        gamma: FloatArray,
        upstream: FloatArray,
        dGamma: FloatArray,
        dBeta: FloatArray
    ): FloatArray {
        val size = xhat.size
        val dXhat = FloatArray(size)
        var sum = 0.0
        var sumXhat = 0.0
        for (index in 0 until size) {
            dGamma[index] += upstream[index] * xhat[index]
            dBeta[index] += upstream[index]
            dXhat[index] = upstream[index] * gamma[index]
            sum += dXhat[index].toDouble()
            sumXhat += dXhat[index].toDouble() * xhat[index].toDouble()
        }
        return FloatArray(size) { index ->
            (
                inverseStd.toDouble() / size.toDouble() *
                    (size.toDouble() * dXhat[index].toDouble() -
                        sum - xhat[index].toDouble() * sumXhat)
            ).toFloat()
        }
    }

    private fun rotateInPlace(values: FloatArray, position: Int, inverse: Boolean = false) {
        val headDim = config.embeddingSize / config.headCount
        for (head in 0 until config.headCount) {
            val headOffset = head * headDim
            for (pair in 0 until headDim / 2) {
                val firstIndex = headOffset + pair * 2
                val secondIndex = firstIndex + 1
                val exponent = (pair * 2).toDouble() / headDim.toDouble()
                val frequency = 1.0 / Math.pow(ROPE_BASE, exponent)
                val angle = position.toDouble() * frequency
                val cosine = kotlin.math.cos(angle).toFloat()
                val sine = kotlin.math.sin(angle).toFloat()
                val first = values[firstIndex]
                val second = values[secondIndex]
                if (inverse) {
                    values[firstIndex] = first * cosine + second * sine
                    values[secondIndex] = -first * sine + second * cosine
                } else {
                    values[firstIndex] = first * cosine - second * sine
                    values[secondIndex] = first * sine + second * cosine
                }
            }
        }
    }

    private fun linear(
        input: FloatArray,
        weights: FloatArray,
        inputSize: Int,
        outputSize: Int
    ): FloatArray {
        val output = FloatArray(outputSize)
        for (inputIndex in 0 until inputSize) {
            val value = input[inputIndex]
            val offset = inputIndex * outputSize
            for (outputIndex in 0 until outputSize) {
                output[outputIndex] += value * weights[offset + outputIndex]
            }
        }
        return output
    }

    private fun softmax(logits: FloatArray): FloatArray {
        var maximum = Float.NEGATIVE_INFINITY
        for (value in logits) if (value > maximum) maximum = value
        val probabilities = FloatArray(logits.size)
        var denominator = 0.0
        for (index in logits.indices) {
            val value = exp((logits[index] - maximum).toDouble())
            probabilities[index] = value.toFloat()
            denominator += value
        }
        require(denominator.isFinite() && denominator > 0.0)
        for (index in probabilities.indices) {
            probabilities[index] = (probabilities[index].toDouble() / denominator).toFloat()
        }
        return probabilities
    }

    private fun silu(value: Float): Float = value * sigmoid(value)

    private fun siluDerivative(value: Float): Float {
        val probability = sigmoid(value)
        return probability * (1.0f + value * (1.0f - probability))
    }

    private fun sigmoid(value: Float): Float {
        return if (value >= 0.0f) {
            (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()
        } else {
            val exponential = exp(value.toDouble())
            (exponential / (1.0 + exponential)).toFloat()
        }
    }

    private fun singleLoss(contextTokenIds: IntArray, targetTokenId: Int): Double =
        -ln(max(forward(contextTokenIds).probabilities[targetTokenId].toDouble(), 1.0e-12))

    private fun validateParameterShapes(set: ParameterSet, config: Config) {
        val d = config.embeddingSize
        val v = config.vocabularySize
        val f = config.feedForwardSize
        val expected = listOf(
            v * d, d * d, d * d, d * d, d * d,
            d, d, d, d, d * f, d * f, f * d, d * v, v
        )
        require(set.arrays().map { it.size } == expected) {
            "Dimensões do conjunto de parâmetros KBN-003 inválidas."
        }
        require(set.arrays().all { values -> values.all(Float::isFinite) }) {
            "Parâmetros KBN-003 contêm valores não finitos."
        }
    }

    private fun writeFloatArray(data: DataOutputStream, values: FloatArray) {
        data.writeInt(values.size)
        for (value in values) data.writeFloat(value)
    }
}

private fun Double.power(exponent: Long): Double {
    var base = this
    var remaining = exponent
    var result = 1.0
    while (remaining > 0L) {
        if ((remaining and 1L) == 1L) result *= base
        base *= base
        remaining = remaining ushr 1
    }
    return result
}
