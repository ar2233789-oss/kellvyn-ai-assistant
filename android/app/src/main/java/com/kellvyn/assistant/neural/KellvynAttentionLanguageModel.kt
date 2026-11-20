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
import kotlin.math.tanh

/**
 * Original, randomly initialized, single-head autoregressive attention language model.
 *
 * KN-002 alpha is intentionally small and CPU-only. It learns next-token prediction
 * from token IDs provided by the caller. It imports no pretrained parameters.
 *
 * This is an attention-model prototype, not yet a full multi-layer Transformer or a
 * general conversational assistant. The implementation favors inspectable math and
 * gradient tests before adding multi-head blocks, normalization and a gated MLP.
 */
internal class KellvynAttentionLanguageModel private constructor(
    val config: Config,
    private val parameters: ParameterSet,
    private val firstMoment: ParameterSet,
    private val secondMoment: ParameterSet,
    var trainingSteps: Long,
    private var optimizerSteps: Long
) {
    data class Config(
        val vocabularySize: Int = 512,
        val contextLength: Int = 32,
        val embeddingSize: Int = 32,
        val seed: Long = 20261116L
    ) {
        init {
            require(vocabularySize in 256..8192) { "vocabularySize fora do limite." }
            require(contextLength in 1..256) { "contextLength fora do limite." }
            require(embeddingSize in 4..128) { "embeddingSize fora do limite." }
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
        val positionEmbeddings: FloatArray,
        val queryWeights: FloatArray,
        val keyWeights: FloatArray,
        val valueWeights: FloatArray,
        val outputWeights: FloatArray,
        val outputBias: FloatArray
    ) {
        fun arrays(): List<FloatArray> = listOf(
            tokenEmbeddings,
            positionEmbeddings,
            queryWeights,
            keyWeights,
            valueWeights,
            outputWeights,
            outputBias
        )
    }

    private data class ForwardPass(
        val probabilities: FloatArray,
        val hidden: FloatArray,
        val inputs: Array<FloatArray>,
        val query: FloatArray,
        val keys: Array<FloatArray>,
        val values: Array<FloatArray>,
        val attention: FloatArray
    )

    private data class Gradients(
        val parameters: ParameterSet
    )

    init {
        validateParameterShapes(parameters, config)
        validateParameterShapes(firstMoment, config)
        validateParameterShapes(secondMoment, config)
        require(trainingSteps >= 0L && optimizerSteps >= 0L)
        require(optimizerSteps == trainingSteps) {
            "Contadores do otimizador e do treinamento divergiram."
        }
    }

    companion object {
        private const val CHECKPOINT_MAGIC = 0x4B564E4C // "KVNL"
        private const val CHECKPOINT_VERSION = 1
        private const val CHECKSUM_BYTES = 32
        private const val MAX_CHECKPOINT_BYTES = 64 * 1024 * 1024
        private const val MAX_TRAINING_TOKENS = 100_000
        private const val MAX_GENERATION_INPUT_TOKENS = 100_000
        private const val MAX_TRAINING_STEPS_PER_CALL = 50_000L
        private const val GRADIENT_CLIP_NORM = 1.0
        private const val BETA1 = 0.9
        private const val BETA2 = 0.999
        private const val ADAM_EPSILON = 1.0e-8
        private const val DEFAULT_WEIGHT_DECAY = 0.01f

        fun createForTokenizer(
            tokenizer: KellvynBpeTokenizer,
            contextLength: Int = 32,
            embeddingSize: Int = 32,
            seed: Long = 20261116L
        ): KellvynAttentionLanguageModel = create(
            Config(
                vocabularySize = tokenizer.vocabularySize,
                contextLength = contextLength,
                embeddingSize = embeddingSize,
                seed = seed
            )
        )

        fun create(config: Config = Config()): KellvynAttentionLanguageModel {
            val random = Random(config.seed)
            val d = config.embeddingSize
            val v = config.vocabularySize
            val c = config.contextLength

            val embeddingLimit = sqrt(3.0 / d).toFloat()
            val projectionLimit = sqrt(6.0 / (2.0 * d)).toFloat()
            val outputLimit = sqrt(6.0 / (d + v)).toFloat()

            val parameters = ParameterSet(
                tokenEmbeddings = randomArray((v * d), random, embeddingLimit),
                positionEmbeddings = randomArray((c * d), random, 0.02f),
                queryWeights = randomArray(d * d, random, projectionLimit),
                keyWeights = randomArray(d * d, random, projectionLimit),
                valueWeights = randomArray(d * d, random, projectionLimit),
                outputWeights = randomArray(d * v, random, outputLimit),
                outputBias = FloatArray(v)
            )
            return KellvynAttentionLanguageModel(
                config = config,
                parameters = parameters,
                firstMoment = zeroParameters(config),
                secondMoment = zeroParameters(config),
                trainingSteps = 0L,
                optimizerSteps = 0L
            )
        }

        /** Restores the model, including optimizer moments, from its own bounded format. */
        fun readCheckpoint(input: InputStream): KellvynAttentionLanguageModel {
            val bytes = readBounded(input)
            require(bytes.size in (CHECKSUM_BYTES + 32)..MAX_CHECKPOINT_BYTES) {
                "Checkpoint neural inválido ou acima do limite."
            }

            val payloadSize = bytes.size - CHECKSUM_BYTES
            val payload = bytes.copyOfRange(0, payloadSize)
            val storedDigest = bytes.copyOfRange(payloadSize, bytes.size)
            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
            require(MessageDigest.isEqual(storedDigest, actualDigest)) {
                "A integridade do checkpoint neural não confere."
            }

            val data = DataInputStream(ByteArrayInputStream(payload))
            require(data.readInt() == CHECKPOINT_MAGIC) {
                "Checkpoint não pertence ao modelo neural Kellvyn."
            }
            require(data.readInt() == CHECKPOINT_VERSION) {
                "Versão do checkpoint neural não suportada."
            }
            val config = Config(
                vocabularySize = data.readInt(),
                contextLength = data.readInt(),
                embeddingSize = data.readInt(),
                seed = data.readLong()
            )
            val trainingSteps = data.readLong()
            val optimizerSteps = data.readLong()
            require(trainingSteps >= 0L && optimizerSteps == trainingSteps) {
                "Contadores do checkpoint neural inválidos."
            }

            val parameters = readParameterSet(data, config)
            val firstMoment = readParameterSet(data, config)
            val secondMoment = readParameterSet(data, config)
            require(data.available() == 0) { "Checkpoint neural contém dados extras." }

            return KellvynAttentionLanguageModel(
                config,
                parameters,
                firstMoment,
                secondMoment,
                trainingSteps,
                optimizerSteps
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
                    val single = input.read()
                    if (single < 0) break
                    require(total < MAX_CHECKPOINT_BYTES) { "Checkpoint neural acima do limite." }
                    output.write(single)
                    total++
                } else {
                    require(total <= MAX_CHECKPOINT_BYTES - count) {
                        "Checkpoint neural acima do limite."
                    }
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            return output.toByteArray()
        }

        private fun randomArray(size: Int, random: Random, limit: Float): FloatArray =
            FloatArray(size) {
                ((random.nextDouble() * 2.0 - 1.0) * limit).toFloat()
            }

        private fun zeroParameters(config: Config): ParameterSet {
            val d = config.embeddingSize
            val v = config.vocabularySize
            val c = config.contextLength
            return ParameterSet(
                FloatArray(v * d),
                FloatArray(c * d),
                FloatArray(d * d),
                FloatArray(d * d),
                FloatArray(d * d),
                FloatArray(d * v),
                FloatArray(v)
            )
        }

        private fun validateParameterShapes(parameters: ParameterSet, config: Config) {
            val d = config.embeddingSize
            val v = config.vocabularySize
            val c = config.contextLength
            require(parameters.tokenEmbeddings.size == v * d)
            require(parameters.positionEmbeddings.size == c * d)
            require(parameters.queryWeights.size == d * d)
            require(parameters.keyWeights.size == d * d)
            require(parameters.valueWeights.size == d * d)
            require(parameters.outputWeights.size == d * v)
            require(parameters.outputBias.size == v)
            require(parameters.arrays().all { values -> values.all(Float::isFinite) }) {
                "O conjunto de parâmetros contém valores não finitos."
            }
        }

        private fun readParameterSet(data: DataInputStream, config: Config): ParameterSet {
            val zero = zeroParameters(config)
            val expected = zero.arrays()
            val restored = expected.map { readFloatArray(data, it.size) }
            return ParameterSet(
                restored[0],
                restored[1],
                restored[2],
                restored[3],
                restored[4],
                restored[5],
                restored[6]
            )
        }

        private fun readFloatArray(data: DataInputStream, expectedSize: Int): FloatArray {
            val count = data.readInt()
            require(count == expectedSize) { "Dimensão de parâmetro inválida no checkpoint." }
            return FloatArray(count) {
                data.readFloat().also { value ->
                    require(value.isFinite()) { "Checkpoint contém parâmetro não finito." }
                }
            }
        }
    }

    /**
     * Trains next-token prediction on the explicit token stream.
     * Work is bounded per call; long training runs must be checkpointed in batches.
     */
    fun trainTokens(
        tokenIds: IntArray,
        epochs: Int,
        learningRate: Float = 0.003f,
        weightDecay: Float = DEFAULT_WEIGHT_DECAY,
        cancellationCheck: (() -> Unit)? = null
    ): TrainingReport {
        require(tokenIds.size in 2..MAX_TRAINING_TOKENS) { "Corpus tokenizado fora do limite." }
        require(tokenIds.all { it in 0 until config.vocabularySize }) {
            "O corpus contém ID de token fora do vocabulário."
        }
        require(epochs in 1..10_000) { "Número de épocas fora do limite." }
        require((tokenIds.size - 1L) * epochs.toLong() <= MAX_TRAINING_STEPS_PER_CALL) {
            "A chamada excederia o orçamento de passos de treinamento."
        }
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f) {
            "Taxa de aprendizado fora do limite."
        }
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f) {
            "Weight decay fora do limite."
        }

        cancellationCheck?.invoke()
        val initialLoss = evaluateLoss(tokenIds)
        repeat(epochs) {
            for (targetIndex in 1 until tokenIds.size) {
                if (targetIndex and 31 == 0) cancellationCheck?.invoke()
                val start = max(0, targetIndex - config.contextLength)
                val context = tokenIds.copyOfRange(start, targetIndex)
                trainOne(context, tokenIds[targetIndex], learningRate, weightDecay)
            }
        }
        cancellationCheck?.invoke()
        val finalLoss = evaluateLoss(tokenIds)
        return TrainingReport(
            initialLoss = initialLoss,
            finalLoss = finalLoss,
            examples = tokenIds.size - 1,
            epochs = epochs,
            totalSteps = trainingSteps
        )
    }

    /**
     * Supervised completion training. The prompt is used as context but only completion
     * tokens contribute to the loss and parameter updates.
     */
    fun trainCompletion(
        promptTokenIds: IntArray,
        completionTokenIds: IntArray,
        epochs: Int,
        learningRate: Float = 0.003f,
        weightDecay: Float = DEFAULT_WEIGHT_DECAY,
        cancellationCheck: (() -> Unit)? = null
    ): TrainingReport {
        require(promptTokenIds.isNotEmpty()) { "O prompt supervisionado está vazio." }
        require(completionTokenIds.isNotEmpty()) { "A resposta supervisionada está vazia." }
        require(promptTokenIds.size.toLong() + completionTokenIds.size.toLong() <= MAX_TRAINING_TOKENS) {
            "Prompt e resposta excedem o limite de tokens por exemplo."
        }
        require((promptTokenIds + completionTokenIds).all { it in 0 until config.vocabularySize }) {
            "O exemplo supervisionado contém ID de token inválido."
        }
        require(epochs in 1..10_000) { "Número de épocas fora do limite." }
        require(completionTokenIds.size.toLong() * epochs.toLong() <= MAX_TRAINING_STEPS_PER_CALL) {
            "A chamada excederia o orçamento de passos de treinamento supervisionado."
        }
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f) {
            "Taxa de aprendizado fora do limite."
        }
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f) {
            "Weight decay fora do limite."
        }

        val combined = promptTokenIds + completionTokenIds
        cancellationCheck?.invoke()
        val initialLoss = evaluateCompletionLoss(promptTokenIds, completionTokenIds)
        repeat(epochs) {
            for (targetIndex in promptTokenIds.size until combined.size) {
                if ((targetIndex - promptTokenIds.size) and 31 == 0) cancellationCheck?.invoke()
                val start = max(0, targetIndex - config.contextLength)
                trainOne(
                    contextTokenIds = combined.copyOfRange(start, targetIndex),
                    targetTokenId = combined[targetIndex],
                    learningRate = learningRate,
                    weightDecay = weightDecay
                )
            }
        }
        cancellationCheck?.invoke()
        val finalLoss = evaluateCompletionLoss(promptTokenIds, completionTokenIds)
        return TrainingReport(
            initialLoss = initialLoss,
            finalLoss = finalLoss,
            examples = completionTokenIds.size,
            epochs = epochs,
            totalSteps = trainingSteps
        )
    }

    /** Evaluates only the completion, conditioning every target on the preceding prompt. */
    fun evaluateCompletionLoss(
        promptTokenIds: IntArray,
        completionTokenIds: IntArray
    ): Double {
        require(promptTokenIds.isNotEmpty() && completionTokenIds.isNotEmpty())
        require(promptTokenIds.size.toLong() + completionTokenIds.size.toLong() <= MAX_TRAINING_TOKENS)
        val combined = promptTokenIds + completionTokenIds
        require(combined.all { it in 0 until config.vocabularySize }) {
            "O exemplo supervisionado contém ID de token inválido."
        }
        var totalLoss = 0.0
        for (targetIndex in promptTokenIds.size until combined.size) {
            val start = max(0, targetIndex - config.contextLength)
            val context = combined.copyOfRange(start, targetIndex)
            val probability = max(
                forward(context).probabilities[combined[targetIndex]].toDouble(),
                1.0e-12
            )
            totalLoss -= ln(probability)
        }
        return totalLoss / completionTokenIds.size.toDouble()
    }

    fun trainText(
        text: String,
        tokenizer: KellvynBpeTokenizer,
        epochs: Int,
        learningRate: Float = 0.003f,
        weightDecay: Float = DEFAULT_WEIGHT_DECAY
    ): TrainingReport {
        require(tokenizer.vocabularySize == config.vocabularySize) {
            "O vocabulário do tokenizer não corresponde ao modelo."
        }
        val textTokens = tokenizer.encode(text)
        val endTokenId = requireNotNull(tokenizer.endOfSequenceTokenId) {
            "O tokenizer não reservou o token de encerramento."
        }
        return trainTokens(
            textTokens + intArrayOf(endTokenId),
            epochs,
            learningRate,
            weightDecay
        )
    }

    fun evaluateLoss(tokenIds: IntArray): Double {
        require(tokenIds.size in 2..MAX_TRAINING_TOKENS) { "Corpus de avaliação fora do limite." }
        require(tokenIds.all { it in 0 until config.vocabularySize }) {
            "A avaliação contém ID de token fora do vocabulário."
        }
        var totalLoss = 0.0
        for (targetIndex in 1 until tokenIds.size) {
            val start = max(0, targetIndex - config.contextLength)
            val pass = forward(tokenIds.copyOfRange(start, targetIndex))
            val probability = max(pass.probabilities[tokenIds[targetIndex]].toDouble(), 1.0e-12)
            totalLoss -= ln(probability)
        }
        return totalLoss / (tokenIds.size - 1).toDouble()
    }

    /** Predicts one token from the available preceding context. */
    fun predictNext(contextTokenIds: IntArray): Int {
        require(contextTokenIds.isNotEmpty()) { "O contexto precisa conter ao menos um token." }
        require(contextTokenIds.size <= config.contextLength)
        require(contextTokenIds.all { it in 0 until config.vocabularySize })
        val probabilities = forward(contextTokenIds).probabilities
        var bestToken = 0
        for (token in 1 until probabilities.size) {
            if (probabilities[token] > probabilities[bestToken]) bestToken = token
        }
        return bestToken
    }

    /** Greedy autoregressive generation; a supplied EOS control token is not returned. */
    fun generate(
        prefixTokenIds: IntArray,
        maxNewTokens: Int,
        endTokenId: Int? = null
    ): IntArray {
        require(prefixTokenIds.isNotEmpty())
        require(prefixTokenIds.size <= MAX_GENERATION_INPUT_TOKENS) {
            "Prefixo de geração acima do limite."
        }
        require(prefixTokenIds.all { it in 0 until config.vocabularySize })
        require(maxNewTokens in 0..4096)
        require(endTokenId == null || endTokenId in 0 until config.vocabularySize)
        val output = prefixTokenIds.toMutableList()
        repeat(maxNewTokens) {
            val context = output.takeLast(config.contextLength).toIntArray()
            val nextToken = predictNext(context)
            if (endTokenId != null && nextToken == endTokenId) {
                return output.toIntArray()
            }
            output += nextToken
        }
        return output.toIntArray()
    }

    fun generateText(
        prefix: String,
        tokenizer: KellvynBpeTokenizer,
        maxNewTokens: Int
    ): String {
        require(tokenizer.vocabularySize == config.vocabularySize)
        return tokenizer.decode(
            generate(
                tokenizer.encode(prefix),
                maxNewTokens,
                endTokenId = tokenizer.endOfSequenceTokenId
            )
        )
    }

    /** Compares analytic gradients to central finite differences without changing weights. */
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
        val groups = parameters.arrays().zip(gradients.arrays())
        var maxAbsoluteError = 0.0
        var maxRelativeError = 0.0
        var checked = 0

        for ((values, gradient) in groups) {
            val candidates = gradient.indices.filter { abs(gradient[it]) > 1.0e-8f }
            val samples = minOf(maxCoordinatesPerGroup, candidates.size)
            for (sample in 0 until samples) {
                val position = if (samples == 1) 0
                    else sample * (candidates.size - 1) / (samples - 1)
                val parameterIndex = candidates[position]
                val original = values[parameterIndex]
                values[parameterIndex] = (original.toDouble() + epsilon).toFloat()
                val plusLoss = singleLoss(contextTokenIds, targetTokenId)
                values[parameterIndex] = (original.toDouble() - epsilon).toFloat()
                val minusLoss = singleLoss(contextTokenIds, targetTokenId)
                values[parameterIndex] = original

                val numeric = (plusLoss - minusLoss) / (2.0 * epsilon)
                val analytic = gradient[parameterIndex].toDouble()
                val absolute = abs(numeric - analytic)
                val relative = absolute /
                    max(1.0e-7, abs(numeric) + abs(analytic))
                require(numeric.isFinite() && analytic.isFinite()) {
                    "Gradiente não finito na verificação numérica."
                }
                maxAbsoluteError = max(maxAbsoluteError, absolute)
                maxRelativeError = max(maxRelativeError, relative)
                checked++
            }
        }
        require(checked > 0) { "Não houve parâmetros ativos para verificar." }
        return GradientCheckReport(maxAbsoluteError, maxRelativeError, checked)
    }

    fun writeCheckpoint(output: OutputStream) {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeInt(CHECKPOINT_MAGIC)
            data.writeInt(CHECKPOINT_VERSION)
            data.writeInt(config.vocabularySize)
            data.writeInt(config.contextLength)
            data.writeInt(config.embeddingSize)
            data.writeLong(config.seed)
            data.writeLong(trainingSteps)
            data.writeLong(optimizerSteps)
            for (set in listOf(parameters, firstMoment, secondMoment)) {
                for (array in set.arrays()) writeFloatArray(data, array)
            }
        }
        val payload = buffer.toByteArray()
        require(payload.size + CHECKSUM_BYTES <= MAX_CHECKPOINT_BYTES) {
            "Checkpoint neural excedeu o limite."
        }
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
        val scale = if (norm > GRADIENT_CLIP_NORM) GRADIENT_CLIP_NORM / norm else 1.0
        if (scale < 1.0) {
            for (array in gradients.arrays()) {
                for (index in array.indices) array[index] = (array[index] * scale).toFloat()
            }
        }

        optimizerSteps++
        trainingSteps++
        val beta1Correction = 1.0 - BETA1.pow(optimizerSteps)
        val beta2Correction = 1.0 - BETA2.pow(optimizerSteps)

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
        val length = contextTokenIds.size
        val last = length - 1
        val inverseScale = (1.0 / sqrt(d.toDouble())).toFloat()

        val dTokenEmbeddings = FloatArray(parameters.tokenEmbeddings.size)
        val dPositionEmbeddings = FloatArray(parameters.positionEmbeddings.size)
        val dQueryWeights = FloatArray(parameters.queryWeights.size)
        val dKeyWeights = FloatArray(parameters.keyWeights.size)
        val dValueWeights = FloatArray(parameters.valueWeights.size)
        val dOutputWeights = FloatArray(parameters.outputWeights.size)
        val dOutputBias = pass.probabilities.copyOf()
        dOutputBias[targetTokenId] -= 1.0f

        val dHidden = FloatArray(d)
        for (dimension in 0 until d) {
            val weightOffset = dimension * v
            for (token in 0 until v) {
                val gradient = dOutputBias[token]
                dOutputWeights[weightOffset + token] = pass.hidden[dimension] * gradient
                dHidden[dimension] += parameters.outputWeights[weightOffset + token] * gradient
            }
        }

        val dAttentionOutput = FloatArray(d) { dimension ->
            dHidden[dimension] * (1.0f - pass.hidden[dimension] * pass.hidden[dimension])
        }

        val dValues = Array(length) { FloatArray(d) }
        val dAttention = FloatArray(length)
        for (position in 0 until length) {
            for (dimension in 0 until d) {
                dValues[position][dimension] = pass.attention[position] * dAttentionOutput[dimension]
                dAttention[position] += dAttentionOutput[dimension] * pass.values[position][dimension]
            }
        }

        var softmaxWeightedGradient = 0.0f
        for (position in 0 until length) {
            softmaxWeightedGradient += pass.attention[position] * dAttention[position]
        }

        val dScores = FloatArray(length)
        val dQuery = FloatArray(d)
        val dKeys = Array(length) { FloatArray(d) }
        for (position in 0 until length) {
            dScores[position] = pass.attention[position] *
                (dAttention[position] - softmaxWeightedGradient)
            for (dimension in 0 until d) {
                dQuery[dimension] += dScores[position] * pass.keys[position][dimension] * inverseScale
                dKeys[position][dimension] =
                    dScores[position] * pass.query[dimension] * inverseScale
            }
        }

        val dInputs = Array(length) { FloatArray(d) }
        for (inputPosition in 0 until length) {
            for (inputDimension in 0 until d) {
                var gradient = 0.0f
                if (inputPosition == last) {
                    for (projectionDimension in 0 until d) {
                        val index = inputDimension * d + projectionDimension
                        dQueryWeights[index] = pass.inputs[last][inputDimension] * dQuery[projectionDimension]
                        gradient += parameters.queryWeights[index] * dQuery[projectionDimension]
                    }
                }
                for (projectionDimension in 0 until d) {
                    val index = inputDimension * d + projectionDimension
                    dKeyWeights[index] += pass.inputs[inputPosition][inputDimension] *
                        dKeys[inputPosition][projectionDimension]
                    dValueWeights[index] += pass.inputs[inputPosition][inputDimension] *
                        dValues[inputPosition][projectionDimension]
                    gradient += parameters.keyWeights[index] * dKeys[inputPosition][projectionDimension]
                    gradient += parameters.valueWeights[index] * dValues[inputPosition][projectionDimension]
                }
                dInputs[inputPosition][inputDimension] = gradient
                val tokenOffset = contextTokenIds[inputPosition] * d + inputDimension
                val positionOffset = inputPosition * d + inputDimension
                dTokenEmbeddings[tokenOffset] += gradient
                dPositionEmbeddings[positionOffset] += gradient
            }
        }

        return Gradients(
            ParameterSet(
                dTokenEmbeddings,
                dPositionEmbeddings,
                dQueryWeights,
                dKeyWeights,
                dValueWeights,
                dOutputWeights,
                dOutputBias
            )
        )
    }

    private fun forward(contextTokenIds: IntArray): ForwardPass {
        require(contextTokenIds.isNotEmpty() && contextTokenIds.size <= config.contextLength)
        val d = config.embeddingSize
        val v = config.vocabularySize
        val length = contextTokenIds.size
        val last = length - 1
        val inputs = Array(length) { position ->
            val tokenId = contextTokenIds[position]
            require(tokenId in 0 until v) { "ID de token fora do vocabulário." }
            FloatArray(d) { dimension ->
                parameters.tokenEmbeddings[tokenId * d + dimension] +
                    parameters.positionEmbeddings[position * d + dimension]
            }
        }

        val query = FloatArray(d)
        for (inputDimension in 0 until d) {
            val value = inputs[last][inputDimension]
            for (projectionDimension in 0 until d) {
                query[projectionDimension] +=
                    value * parameters.queryWeights[inputDimension * d + projectionDimension]
            }
        }

        val keys = Array(length) { FloatArray(d) }
        val values = Array(length) { FloatArray(d) }
        for (position in 0 until length) {
            for (inputDimension in 0 until d) {
                val value = inputs[position][inputDimension]
                for (projectionDimension in 0 until d) {
                    val index = inputDimension * d + projectionDimension
                    keys[position][projectionDimension] +=
                        value * parameters.keyWeights[index]
                    values[position][projectionDimension] +=
                        value * parameters.valueWeights[index]
                }
            }
        }

        val scale = (1.0 / sqrt(d.toDouble())).toFloat()
        val scores = FloatArray(length)
        var maximum = Float.NEGATIVE_INFINITY
        for (position in 0 until length) {
            var score = 0.0f
            for (dimension in 0 until d) {
                score += query[dimension] * keys[position][dimension]
            }
            scores[position] = score * scale
            if (scores[position] > maximum) maximum = scores[position]
        }

        val attention = FloatArray(length)
        var denominator = 0.0
        for (position in 0 until length) {
            val value = exp((scores[position] - maximum).toDouble())
            attention[position] = value.toFloat()
            denominator += value
        }
        require(denominator.isFinite() && denominator > 0.0) {
            "A atenção produziu uma normalização inválida."
        }
        for (position in 0 until length) {
            attention[position] = (attention[position].toDouble() / denominator).toFloat()
        }

        val attentionOutput = FloatArray(d)
        for (position in 0 until length) {
            for (dimension in 0 until d) {
                attentionOutput[dimension] += attention[position] * values[position][dimension]
            }
        }
        val hidden = FloatArray(d) { dimension ->
            tanh(attentionOutput[dimension].toDouble()).toFloat()
        }

        val logits = FloatArray(v)
        var logitMaximum = Float.NEGATIVE_INFINITY
        for (token in 0 until v) {
            var value = parameters.outputBias[token]
            for (dimension in 0 until d) {
                value += hidden[dimension] * parameters.outputWeights[dimension * v + token]
            }
            logits[token] = value
            if (value > logitMaximum) logitMaximum = value
        }

        val probabilities = FloatArray(v)
        var logitDenominator = 0.0
        for (token in 0 until v) {
            val value = exp((logits[token] - logitMaximum).toDouble())
            probabilities[token] = value.toFloat()
            logitDenominator += value
        }
        require(logitDenominator.isFinite() && logitDenominator > 0.0) {
            "A saída neural produziu uma normalização inválida."
        }
        for (token in 0 until v) {
            probabilities[token] = (probabilities[token].toDouble() / logitDenominator).toFloat()
        }

        return ForwardPass(probabilities, hidden, inputs, query, keys, values, attention)
    }

    private fun singleLoss(contextTokenIds: IntArray, targetTokenId: Int): Double {
        val probability = max(forward(contextTokenIds).probabilities[targetTokenId].toDouble(), 1.0e-12)
        return -ln(probability)
    }

    private fun writeFloatArray(data: DataOutputStream, values: FloatArray) {
        data.writeInt(values.size)
        for (value in values) data.writeFloat(value)
    }
}

private fun Double.pow(exponent: Long): Double {
    var base = this
    var power = exponent
    var result = 1.0
    while (power > 0L) {
        if ((power and 1L) == 1L) result *= base
        base *= base
        power = power ushr 1
    }
    return result
}
