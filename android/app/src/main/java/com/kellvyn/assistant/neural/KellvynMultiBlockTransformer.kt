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
 * KN-004: original multi-block decoder-only Transformer.
 *
 * Unlike KN-003, every position is propagated through every block. The loss on the
 * final prediction is backpropagated through all query positions, keys and values so
 * earlier token representations receive gradients through causal attention.
 *
 * This implementation intentionally remains separate from KN-003. No external model
 * weights, checkpoints, adapters or hosted inference are accepted.
 */
internal class KellvynMultiBlockTransformer private constructor(
    val config: Config,
    private val parameters: ParameterSet,
    private val firstMoment: ParameterSet,
    private val secondMoment: ParameterSet,
    var trainingSteps: Long,
    private var optimizerSteps: Long
) {
    data class Config(
        val vocabularySize: Int = 512,
        val contextLength: Int = 256,
        val embeddingSize: Int = 32,
        val headCount: Int = 4,
        val feedForwardSize: Int = 64,
        val depth: Int = 2,
        val seed: Long = 20261120L
    ) {
        init {
            require(vocabularySize in 256..8192)
            require(contextLength in 1..2048)
            require(embeddingSize in 8..256)
            require(headCount in 1..16)
            require(embeddingSize % headCount == 0)
            require((embeddingSize / headCount) % 2 == 0) {
                "A dimensão de cada cabeça precisa ser par para RoPE."
            }
            require(feedForwardSize in 4..1024)
            require(depth in 1..8)

            // Reject pathological allocations before any model tensors are created.
            val vocabulary = vocabularySize.toLong()
            val embedding = embeddingSize.toLong()
            val feedForward = feedForwardSize.toLong()
            val sharedParameters = 2L * vocabulary * embedding + vocabulary
            val perBlockParameters =
                4L * embedding * embedding + 4L * embedding + 3L * embedding * feedForward
            val parameterCount = sharedParameters + perBlockParameters * depth.toLong()
            val weightsAndAdamBytes = parameterCount * 12L
            require(weightsAndAdamBytes <= 128L * 1024L * 1024L) {
                "A configuração KN-004 excede o orçamento de pesos e estados AdamW."
            }

            val attentionCacheBytes =
                depth.toLong() * headCount.toLong() *
                    contextLength.toLong() * contextLength.toLong() * 4L
            require(attentionCacheBytes <= 32L * 1024L * 1024L) {
                "A matriz de atenção KN-004 excede o orçamento de memória de sequência."
            }
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
        val checkedParameters: Int,
        val checkedParametersByGroup: Map<String, Int>
    )

    private data class BlockParameters(
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
        val downWeights: FloatArray
    ) {
        fun arrays(): List<FloatArray> = listOf(
            queryWeights, keyWeights, valueWeights, attentionOutputWeights,
            normAttentionGamma, normAttentionBeta, normFeedForwardGamma,
            normFeedForwardBeta, gateWeights, upWeights, downWeights
        )

        fun namedArrays(prefix: String): List<Pair<String, FloatArray>> = listOf(
            "${prefix}.queryWeights" to queryWeights,
            "${prefix}.keyWeights" to keyWeights,
            "${prefix}.valueWeights" to valueWeights,
            "${prefix}.attentionOutputWeights" to attentionOutputWeights,
            "${prefix}.normAttentionGamma" to normAttentionGamma,
            "${prefix}.normAttentionBeta" to normAttentionBeta,
            "${prefix}.normFeedForwardGamma" to normFeedForwardGamma,
            "${prefix}.normFeedForwardBeta" to normFeedForwardBeta,
            "${prefix}.gateWeights" to gateWeights,
            "${prefix}.upWeights" to upWeights,
            "${prefix}.downWeights" to downWeights
        )
    }

    private data class ParameterSet(
        val tokenEmbeddings: FloatArray,
        val blocks: List<BlockParameters>,
        val outputWeights: FloatArray,
        val outputBias: FloatArray
    ) {
        fun arrays(): List<FloatArray> = buildList {
            add(tokenEmbeddings)
            blocks.forEach { addAll(it.arrays()) }
            add(outputWeights)
            add(outputBias)
        }

        fun namedArrays(): List<Pair<String, FloatArray>> = buildList {
            add("shared.tokenEmbeddings" to tokenEmbeddings)
            blocks.forEachIndexed { index, block ->
                addAll(block.namedArrays("block.$index"))
            }
            add("shared.outputWeights" to outputWeights)
            add("shared.outputBias" to outputBias)
        }
    }

    private data class Normalized(
        val values: FloatArray,
        val xhat: FloatArray,
        val inverseStandardDeviation: Float
    )

    private data class BlockForward(
        val input: Array<FloatArray>,
        val normalizedAttention: Array<FloatArray>,
        val normalizedAttentionXhat: Array<FloatArray>,
        val normalizedAttentionInverseStd: FloatArray,
        val queryRotated: Array<FloatArray>,
        val keysRotated: Array<FloatArray>,
        val values: Array<FloatArray>,
        /** [query position][head][key position] */
        val attentionWeights: Array<Array<FloatArray>>,
        val attentionConcat: Array<FloatArray>,
        val residualAfterAttention: Array<FloatArray>,
        val normalizedMlp: Array<FloatArray>,
        val normalizedMlpXhat: Array<FloatArray>,
        val normalizedMlpInverseStd: FloatArray,
        val gatePreActivation: Array<FloatArray>,
        val upProjection: Array<FloatArray>,
        val siluGate: Array<FloatArray>,
        val gatedIntermediate: Array<FloatArray>,
        val output: Array<FloatArray>
    )

    private data class ForwardPass(
        val embeddings: Array<FloatArray>,
        val blocks: List<BlockForward>,
        val probabilities: FloatArray
    )

    init {
        validateParameterShapes(parameters, config)
        validateParameterShapes(firstMoment, config)
        validateParameterShapes(secondMoment, config)
        require(trainingSteps >= 0L && optimizerSteps == trainingSteps)
    }

    companion object {
        private const val CHECKPOINT_MAGIC = 0x4B564E34 // "KVN4"
        private const val CHECKPOINT_VERSION = 1
        private const val CHECKSUM_BYTES = 32
        private const val MAX_CHECKPOINT_BYTES = 128 * 1024 * 1024
        private const val MAX_TRAINING_TOKENS = 100_000
        private const val MAX_TRAINING_STEPS_PER_CALL = 50_000L
        private const val GRADIENT_CLIP_NORM = 1.0
        private const val GRADIENT_CHECK_ZERO_THRESHOLD = 1.0e-8f
        private const val GRADIENT_CHECK_RELATIVE_ERROR_FLOOR = 1.0e-4
        private const val BETA1 = 0.9
        private const val BETA2 = 0.999
        private const val ADAM_EPSILON = 1.0e-8
        private const val DEFAULT_WEIGHT_DECAY = 0.01f
        private const val LAYER_NORM_EPSILON = 1.0e-5f
        private const val ROPE_BASE = 10_000.0

        fun create(config: Config = Config()): KellvynMultiBlockTransformer {
            val random = Random(config.seed)
            val d = config.embeddingSize
            val v = config.vocabularySize
            val f = config.feedForwardSize
            val embeddings = randomArray(v * d, random, sqrt(3.0 / d).toFloat())
            val blocks = List(config.depth) {
                BlockParameters(
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
                    downWeights = randomArray(f * d, random, xavierLimit(f, d))
                )
            }
            val outputWeights = randomArray(d * v, random, xavierLimit(d, v))
            val outputBias = FloatArray(v)
            val initial = ParameterSet(embeddings, blocks, outputWeights, outputBias)
            val zeros = zeroParameters(config)
            return KellvynMultiBlockTransformer(
                config, initial, copyParameters(zeros), copyParameters(zeros), 0L, 0L
            )
        }

        fun readCheckpoint(input: InputStream): KellvynMultiBlockTransformer {
            val bytes = readBounded(input)
            require(bytes.size in (CHECKSUM_BYTES + 64)..MAX_CHECKPOINT_BYTES) {
                "Checkpoint KN-004 vazio, truncado ou acima do limite."
            }
            val payloadSize = bytes.size - CHECKSUM_BYTES
            val payload = bytes.copyOfRange(0, payloadSize)
            val storedDigest = bytes.copyOfRange(payloadSize, bytes.size)
            val digest = MessageDigest.getInstance("SHA-256").digest(payload)
            require(MessageDigest.isEqual(storedDigest, digest)) {
                "A integridade do checkpoint KN-004 não confere."
            }

            val data = DataInputStream(ByteArrayInputStream(payload))
            require(data.readInt() == CHECKPOINT_MAGIC) {
                "Checkpoint não pertence ao Transformer KN-004."
            }
            require(data.readInt() == CHECKPOINT_VERSION) {
                "Versão do checkpoint KN-004 não suportada."
            }
            val config = Config(
                vocabularySize = data.readInt(),
                contextLength = data.readInt(),
                embeddingSize = data.readInt(),
                headCount = data.readInt(),
                feedForwardSize = data.readInt(),
                depth = data.readInt(),
                seed = data.readLong()
            )
            val trainingSteps = data.readLong()
            val optimizerSteps = data.readLong()
            require(trainingSteps >= 0L && optimizerSteps == trainingSteps) {
                "Contadores do checkpoint KN-004 inválidos."
            }
            val parameters = readParameters(data, config)
            val firstMoment = readParameters(data, config)
            val secondMoment = readParameters(data, config)
            require(data.available() == 0) {
                "Checkpoint KN-004 contém bytes extras."
            }
            return KellvynMultiBlockTransformer(
                config, parameters, firstMoment, secondMoment,
                trainingSteps, optimizerSteps
            )
        }

        private fun randomArray(size: Int, random: Random, limit: Float): FloatArray =
            FloatArray(size) { ((random.nextDouble() * 2.0 - 1.0) * limit).toFloat() }

        private fun xavierLimit(input: Int, output: Int): Float =
            sqrt(6.0 / (input + output)).toFloat()

        private fun zeroParameters(config: Config): ParameterSet {
            val d = config.embeddingSize
            val v = config.vocabularySize
            val f = config.feedForwardSize
            return ParameterSet(
                tokenEmbeddings = FloatArray(v * d),
                blocks = List(config.depth) {
                    BlockParameters(
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
                        downWeights = FloatArray(f * d)
                    )
                },
                outputWeights = FloatArray(d * v),
                outputBias = FloatArray(v)
            )
        }

        private fun copyParameters(source: ParameterSet): ParameterSet =
            ParameterSet(
                tokenEmbeddings = source.tokenEmbeddings.copyOf(),
                blocks = source.blocks.map { block ->
                    val a = block.arrays().map { it.copyOf() }
                    BlockParameters(
                        a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10]
                    )
                },
                outputWeights = source.outputWeights.copyOf(),
                outputBias = source.outputBias.copyOf()
            )

        private fun readParameters(data: DataInputStream, config: Config): ParameterSet {
            val template = zeroParameters(config)
            val arrays = template.arrays().map { readFloatArray(data, it.size) }
            var index = 1
            val blocks = List(config.depth) {
                BlockParameters(
                    arrays[index++], arrays[index++], arrays[index++], arrays[index++],
                    arrays[index++], arrays[index++], arrays[index++], arrays[index++],
                    arrays[index++], arrays[index++], arrays[index++]
                )
            }
            return ParameterSet(arrays[0], blocks, arrays[index++], arrays[index])
        }

        private fun readFloatArray(data: DataInputStream, expectedSize: Int): FloatArray {
            val count = data.readInt()
            require(count == expectedSize) {
                "Dimensão de parâmetro inválida no checkpoint KN-004."
            }
            return FloatArray(count) {
                data.readFloat().also { value ->
                    require(value.isFinite()) {
                        "Checkpoint KN-004 contém parâmetro não finito."
                    }
                }
            }
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
                        "Checkpoint KN-004 acima do limite."
                    }
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            return output.toByteArray()
        }
    }

    fun evaluateNextTokenProbabilities(contextTokenIds: IntArray): FloatArray {
        validateContext(contextTokenIds)
        return forward(contextTokenIds).probabilities.copyOf()
    }

    fun predictNext(contextTokenIds: IntArray): Int {
        val probabilities = evaluateNextTokenProbabilities(contextTokenIds)
        var best = 0
        for (index in 1 until probabilities.size) {
            if (probabilities[index] > probabilities[best]) best = index
        }
        return best
    }

    fun evaluateCompletionLoss(
        promptTokenIds: IntArray,
        completionTokenIds: IntArray
    ): Double {
        validateCompletion(promptTokenIds, completionTokenIds)
        var loss = 0.0
        for (index in completionTokenIds.indices) {
            val context = contextFor(promptTokenIds, completionTokenIds, index)
            loss += singleLoss(context, completionTokenIds[index])
        }
        return loss / completionTokenIds.size.toDouble()
    }

    fun trainCompletion(
        promptTokenIds: IntArray,
        completionTokenIds: IntArray,
        epochs: Int,
        learningRate: Float = 0.003f,
        weightDecay: Float = DEFAULT_WEIGHT_DECAY,
        cancellationCheck: (() -> Unit)? = null
    ): TrainingReport {
        validateCompletion(promptTokenIds, completionTokenIds)
        require(epochs in 1..10_000)
        require(completionTokenIds.size.toLong() * epochs.toLong() <= MAX_TRAINING_STEPS_PER_CALL)
        validateOptimizerArguments(learningRate, weightDecay)
        cancellationCheck?.invoke()
        val initialLoss = evaluateCompletionLoss(promptTokenIds, completionTokenIds)
        repeat(epochs) {
            for (index in completionTokenIds.indices) {
                if (index and 15 == 0) cancellationCheck?.invoke()
                trainOne(
                    contextFor(promptTokenIds, completionTokenIds, index),
                    completionTokenIds[index],
                    learningRate,
                    weightDecay
                )
            }
        }
        cancellationCheck?.invoke()
        val finalLoss = evaluateCompletionLoss(promptTokenIds, completionTokenIds)
        return TrainingReport(
            initialLoss, finalLoss, completionTokenIds.size, epochs, trainingSteps
        )
    }

    internal fun checkGradients(
        contextTokenIds: IntArray,
        targetTokenId: Int,
        epsilon: Double = 0.005,
        maxCoordinatesPerGroup: Int = 2
    ): GradientCheckReport {
        validateContext(contextTokenIds)
        require(targetTokenId in 0 until config.vocabularySize)
        require(epsilon in 0.00001..0.01)
        require(maxCoordinatesPerGroup in 1..8)

        val gradients = computeGradients(contextTokenIds, targetTokenId)
        val parameterGroups = parameters.namedArrays()
        val gradientGroups = gradients.namedArrays()
        require(parameterGroups.map { it.first } == gradientGroups.map { it.first })

        var maxAbsolute = 0.0
        var maxRelative = 0.0
        var checked = 0
        val checkedByGroup = linkedMapOf<String, Int>()
        for (groupIndex in parameterGroups.indices) {
            val (name, values) = parameterGroups[groupIndex]
            val gradient = gradientGroups[groupIndex].second
            val selected = selectCoordinates(gradient, maxCoordinatesPerGroup)
            require(selected.isNotEmpty()) { "Grupo sem coordenadas: $name" }
            for (parameterIndex in selected) {
                val original = values[parameterIndex]
                try {
                    values[parameterIndex] = (original.toDouble() + epsilon).toFloat()
                    val plus = singleLoss(contextTokenIds, targetTokenId)
                    values[parameterIndex] = (original.toDouble() - epsilon).toFloat()
                    val minus = singleLoss(contextTokenIds, targetTokenId)
                    val numeric = (plus - minus) / (2.0 * epsilon)
                    val analytic = gradient[parameterIndex].toDouble()
                    require(numeric.isFinite() && analytic.isFinite())
                    val absolute = abs(numeric - analytic)
                    val scale = max(
                        GRADIENT_CHECK_RELATIVE_ERROR_FLOOR,
                        abs(numeric) + abs(analytic)
                    )
                    maxAbsolute = max(maxAbsolute, absolute)
                    maxRelative = max(maxRelative, absolute / scale)
                    checked++
                } finally {
                    values[parameterIndex] = original
                }
            }
            checkedByGroup[name] = selected.size
        }
        require(checked > 0 && checkedByGroup.size == parameterGroups.size)
        return GradientCheckReport(maxAbsolute, maxRelative, checked, checkedByGroup)
    }

    internal fun parameterFingerprint(): String {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            for (array in parameters.arrays()) writeFloatArray(data, array)
        }
        return MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
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
            data.writeInt(config.depth)
            data.writeLong(config.seed)
            data.writeLong(trainingSteps)
            data.writeLong(optimizerSteps)
            listOf(parameters, firstMoment, secondMoment).forEach { set ->
                set.arrays().forEach { writeFloatArray(data, it) }
            }
        }
        val payload = buffer.toByteArray()
        require(payload.size + CHECKSUM_BYTES <= MAX_CHECKPOINT_BYTES)
        output.write(payload)
        output.write(MessageDigest.getInstance("SHA-256").digest(payload))
        output.flush()
    }

    fun generate(
        prefixTokenIds: IntArray,
        maxNewTokens: Int,
        endTokenId: Int? = null
    ): IntArray {
        require(prefixTokenIds.isNotEmpty())
        require(prefixTokenIds.all { it in 0 until config.vocabularySize })
        require(prefixTokenIds.size <= config.contextLength)
        require(maxNewTokens in 0..4096)
        require(endTokenId == null || endTokenId in 0 until config.vocabularySize)
        val session = newGenerationSession(prefixTokenIds)
        repeat(maxNewTokens) {
            val next = session.predictNext()
            if (next == endTokenId && endTokenId != null) return session.outputTokenIds()
            session.appendToken(next)
        }
        return session.outputTokenIds()
    }

    fun newGenerationSession(prefixTokenIds: IntArray): GenerationSession =
        GenerationSession(prefixTokenIds.copyOf())

    /**
     * Incremental generation with one independent key/value cache for every KN-004 block.
     * RoPE positions remain absolute as the oldest entry is evicted from the sliding window.
     * Caches are transient and are never serialized into model checkpoints.
     */
    internal inner class GenerationSession internal constructor(prefixTokenIds: IntArray) {
        private val outputTokens = ArrayList<Int>(prefixTokenIds.size + 64)
        private val contextTokens = ArrayList<Int>(config.contextLength)
        private val keyCaches = List(config.depth) {
            ArrayList<FloatArray>(config.contextLength)
        }
        private val valueCaches = List(config.depth) {
            ArrayList<FloatArray>(config.contextLength)
        }
        private var nextAbsolutePosition = 0
        private var latestProbabilities: FloatArray? = null

        init {
            require(prefixTokenIds.isNotEmpty()) { "O prefixo da sessão KV está vazio." }
            require(prefixTokenIds.size <= config.contextLength) {
                "O prefixo excede a janela de contexto KN-004."
            }
            require(prefixTokenIds.all { it in 0 until config.vocabularySize }) {
                "O prefixo contém um token fora do vocabulário."
            }
            prefixTokenIds.forEach(::appendInternal)
        }

        fun appendToken(tokenId: Int) {
            require(tokenId in 0 until config.vocabularySize)
            appendInternal(tokenId)
        }

        fun nextTokenProbabilities(): FloatArray =
            requireNotNull(latestProbabilities) {
                "A sessão KV ainda não processou nenhum token."
            }.copyOf()

        fun predictNext(): Int {
            val probabilities = nextTokenProbabilities()
            var best = 0
            for (index in 1 until probabilities.size) {
                if (probabilities[index] > probabilities[best]) best = index
            }
            return best
        }

        fun outputTokenIds(): IntArray = outputTokens.toIntArray()

        fun contextTokenIds(): IntArray = contextTokens.toIntArray()

        internal fun cachedTokenCount(layerIndex: Int): Int {
            require(layerIndex in 0 until config.depth)
            return keyCaches[layerIndex].size
        }

        internal fun processedTokenCount(): Int = nextAbsolutePosition

        private fun appendInternal(tokenId: Int) {
            if (contextTokens.size == config.contextLength) {
                contextTokens.removeAt(0)
                for (layerIndex in 0 until config.depth) {
                    keyCaches[layerIndex].removeAt(0)
                    valueCaches[layerIndex].removeAt(0)
                }
            }
            outputTokens += tokenId
            contextTokens += tokenId

            val d = config.embeddingSize
            val f = config.feedForwardSize
            val heads = config.headCount
            val headDim = d / heads
            val absolutePosition = nextAbsolutePosition
            val tokenOffset = tokenId * d
            var hidden = FloatArray(d) { dimension ->
                parameters.tokenEmbeddings[tokenOffset + dimension]
            }

            for (layerIndex in 0 until config.depth) {
                val block = parameters.blocks[layerIndex]
                val normalized = layerNorm(
                    hidden,
                    block.normAttentionGamma,
                    block.normAttentionBeta
                )
                val query = linear(normalized.values, block.queryWeights, d, d)
                rotateInPlace(query, absolutePosition, heads)
                val key = linear(normalized.values, block.keyWeights, d, d)
                rotateInPlace(key, absolutePosition, heads)
                val value = linear(normalized.values, block.valueWeights, d, d)

                val keyCache = keyCaches[layerIndex]
                val valueCache = valueCaches[layerIndex]
                keyCache += key
                valueCache += value
                require(keyCache.size == valueCache.size &&
                    keyCache.size == contextTokens.size) {
                    "O cache KV do bloco $layerIndex ficou inconsistente."
                }

                val attentionConcat = FloatArray(d)
                for (head in 0 until heads) {
                    val headOffset = head * headDim
                    val scores = FloatArray(keyCache.size)
                    var maximum = Float.NEGATIVE_INFINITY
                    for (position in keyCache.indices) {
                        var score = 0.0f
                        for (dimension in 0 until headDim) {
                            val feature = headOffset + dimension
                            score += query[feature] * keyCache[position][feature]
                        }
                        scores[position] = score / sqrt(headDim.toDouble()).toFloat()
                        if (scores[position] > maximum) maximum = scores[position]
                    }
                    var denominator = 0.0
                    val weights = FloatArray(scores.size)
                    for (position in scores.indices) {
                        val weight = exp((scores[position] - maximum).toDouble())
                        weights[position] = weight.toFloat()
                        denominator += weight
                    }
                    require(denominator.isFinite() && denominator > 0.0)
                    for (position in scores.indices) {
                        val weight = (weights[position].toDouble() / denominator).toFloat()
                        val cachedValue = valueCache[position]
                        for (dimension in 0 until headDim) {
                            val feature = headOffset + dimension
                            attentionConcat[feature] += weight * cachedValue[feature]
                        }
                    }
                }

                val attentionProjection =
                    linear(attentionConcat, block.attentionOutputWeights, d, d)
                val residualAfterAttention = FloatArray(d) { dimension ->
                    hidden[dimension] + attentionProjection[dimension]
                }
                val normalizedMlp = layerNorm(
                    residualAfterAttention,
                    block.normFeedForwardGamma,
                    block.normFeedForwardBeta
                )
                val gate = linear(normalizedMlp.values, block.gateWeights, d, f)
                val up = linear(normalizedMlp.values, block.upWeights, d, f)
                val gated = FloatArray(f) { unit -> silu(gate[unit]) * up[unit] }
                val ffnOutput = linear(gated, block.downWeights, f, d)
                hidden = FloatArray(d) { dimension ->
                    residualAfterAttention[dimension] + ffnOutput[dimension]
                }
            }

            val logits = linear(hidden, parameters.outputWeights, d, config.vocabularySize)
            for (token in logits.indices) logits[token] += parameters.outputBias[token]
            latestProbabilities = softmax(logits)
            nextAbsolutePosition++
            require(keyCaches.all { it.size == contextTokens.size } &&
                valueCaches.all { it.size == contextTokens.size })
        }
    }

    private fun validateCompletion(prompt: IntArray, completion: IntArray) {
        require(prompt.isNotEmpty() && completion.isNotEmpty())
        require(prompt.all { it in 0 until config.vocabularySize })
        require(completion.all { it in 0 until config.vocabularySize })
        require(prompt.size.toLong() + completion.size.toLong() <= MAX_TRAINING_TOKENS)
    }

    private fun validateContext(context: IntArray) {
        require(context.isNotEmpty() && context.size <= config.contextLength) {
            "Contexto vazio ou acima do limite KN-004."
        }
        require(context.all { it in 0 until config.vocabularySize })
    }

    private fun contextFor(prompt: IntArray, completion: IntArray, targetIndex: Int): IntArray {
        val totalSize = prompt.size + targetIndex
        val start = max(0, totalSize - config.contextLength)
        val result = IntArray(totalSize - start)
        for (index in result.indices) {
            val sourceIndex = start + index
            result[index] = if (sourceIndex < prompt.size) {
                prompt[sourceIndex]
            } else {
                completion[sourceIndex - prompt.size]
            }
        }
        require(result.isNotEmpty() && result.size <= config.contextLength)
        return result
    }

    private fun forward(contextTokenIds: IntArray): ForwardPass {
        validateContext(contextTokenIds)
        val d = config.embeddingSize
        val v = config.vocabularySize
        val f = config.feedForwardSize
        val h = config.headCount
        val headDim = d / h
        val length = contextTokenIds.size

        val embeddings = Array(length) { position ->
            val token = contextTokenIds[position]
            FloatArray(d) { dimension ->
                parameters.tokenEmbeddings[token * d + dimension]
            }
        }
        var current = Array(length) { embeddings[it].copyOf() }
        val blockPasses = ArrayList<BlockForward>(config.depth)

        for (layerIndex in 0 until config.depth) {
            val block = parameters.blocks[layerIndex]
            val normalizedAttention = Array(length) { FloatArray(d) }
            val normalizedAttentionXhat = Array(length) { FloatArray(d) }
            val normalizedAttentionInverseStd = FloatArray(length)
            for (position in 0 until length) {
                val norm = layerNorm(
                    current[position], block.normAttentionGamma, block.normAttentionBeta
                )
                normalizedAttention[position] = norm.values
                normalizedAttentionXhat[position] = norm.xhat
                normalizedAttentionInverseStd[position] = norm.inverseStandardDeviation
            }

            val queryRotated = Array(length) { position ->
                linear(normalizedAttention[position], block.queryWeights, d, d)
                    .also { rotateInPlace(it, position, h) }
            }
            val keysRotated = Array(length) { position ->
                linear(normalizedAttention[position], block.keyWeights, d, d)
                    .also { rotateInPlace(it, position, h) }
            }
            val values = Array(length) { position ->
                linear(normalizedAttention[position], block.valueWeights, d, d)
            }

            val attentionWeights = Array(length) { Array(h) { FloatArray(length) } }
            val attentionConcat = Array(length) { FloatArray(d) }
            val scale = (1.0 / sqrt(headDim.toDouble())).toFloat()
            for (queryPosition in 0 until length) {
                for (head in 0 until h) {
                    val offset = head * headDim
                    val scores = FloatArray(queryPosition + 1)
                    var maximum = Float.NEGATIVE_INFINITY
                    for (keyPosition in 0..queryPosition) {
                        var score = 0.0f
                        for (dimension in 0 until headDim) {
                            val feature = offset + dimension
                            score += queryRotated[queryPosition][feature] *
                                keysRotated[keyPosition][feature]
                        }
                        scores[keyPosition] = score * scale
                        if (scores[keyPosition] > maximum) maximum = scores[keyPosition]
                    }
                    var denominator = 0.0
                    for (keyPosition in 0..queryPosition) {
                        val probability = exp(
                            (scores[keyPosition] - maximum).toDouble()
                        )
                        attentionWeights[queryPosition][head][keyPosition] =
                            probability.toFloat()
                        denominator += probability
                    }
                    require(denominator.isFinite() && denominator > 0.0)
                    for (keyPosition in 0..queryPosition) {
                        val probability =
                            (attentionWeights[queryPosition][head][keyPosition].toDouble() /
                                denominator).toFloat()
                        attentionWeights[queryPosition][head][keyPosition] = probability
                        for (dimension in 0 until headDim) {
                            val feature = offset + dimension
                            attentionConcat[queryPosition][feature] +=
                                probability * values[keyPosition][feature]
                        }
                    }
                }
            }

            val residualAfterAttention = Array(length) { position ->
                val projection = linear(attentionConcat[position], block.attentionOutputWeights, d, d)
                FloatArray(d) { dimension ->
                    current[position][dimension] + projection[dimension]
                }
            }
            val normalizedMlp = Array(length) { FloatArray(d) }
            val normalizedMlpXhat = Array(length) { FloatArray(d) }
            val normalizedMlpInverseStd = FloatArray(length)
            val gatePreActivation = Array(length) { FloatArray(f) }
            val upProjection = Array(length) { FloatArray(f) }
            val siluGate = Array(length) { FloatArray(f) }
            val gatedIntermediate = Array(length) { FloatArray(f) }
            val output = Array(length) { FloatArray(d) }

            for (position in 0 until length) {
                val norm = layerNorm(
                    residualAfterAttention[position],
                    block.normFeedForwardGamma,
                    block.normFeedForwardBeta
                )
                normalizedMlp[position] = norm.values
                normalizedMlpXhat[position] = norm.xhat
                normalizedMlpInverseStd[position] = norm.inverseStandardDeviation
                gatePreActivation[position] = linear(norm.values, block.gateWeights, d, f)
                upProjection[position] = linear(norm.values, block.upWeights, d, f)
                siluGate[position] = FloatArray(f) { unit ->
                    silu(gatePreActivation[position][unit])
                }
                gatedIntermediate[position] = FloatArray(f) { unit ->
                    siluGate[position][unit] * upProjection[position][unit]
                }
                val ffnOutput = linear(gatedIntermediate[position], block.downWeights, f, d)
                for (dimension in 0 until d) {
                    output[position][dimension] =
                        residualAfterAttention[position][dimension] + ffnOutput[dimension]
                }
            }

            blockPasses += BlockForward(
                input = current,
                normalizedAttention = normalizedAttention,
                normalizedAttentionXhat = normalizedAttentionXhat,
                normalizedAttentionInverseStd = normalizedAttentionInverseStd,
                queryRotated = queryRotated,
                keysRotated = keysRotated,
                values = values,
                attentionWeights = attentionWeights,
                attentionConcat = attentionConcat,
                residualAfterAttention = residualAfterAttention,
                normalizedMlp = normalizedMlp,
                normalizedMlpXhat = normalizedMlpXhat,
                normalizedMlpInverseStd = normalizedMlpInverseStd,
                gatePreActivation = gatePreActivation,
                upProjection = upProjection,
                siluGate = siluGate,
                gatedIntermediate = gatedIntermediate,
                output = output
            )
            current = output
        }

        val finalHidden = current.last()
        val logits = linear(finalHidden, parameters.outputWeights, d, v)
        for (token in logits.indices) logits[token] += parameters.outputBias[token]
        return ForwardPass(embeddings, blockPasses, softmax(logits))
    }

    private fun computeGradients(contextTokenIds: IntArray, targetTokenId: Int): ParameterSet {
        val pass = forward(contextTokenIds)
        val d = config.embeddingSize
        val v = config.vocabularySize
        val f = config.feedForwardSize
        val h = config.headCount
        val headDim = d / h
        val length = contextTokenIds.size
        val gradients = zeroParameters(config)

        val dLogits = pass.probabilities.copyOf()
        dLogits[targetTokenId] -= 1.0f
        val finalHidden = pass.blocks.last().output.last()
        val dFinalHidden = FloatArray(d)
        for (dimension in 0 until d) {
            val offset = dimension * v
            for (token in 0 until v) {
                val gradient = dLogits[token]
                gradients.outputWeights[offset + token] =
                    finalHidden[dimension] * gradient
                dFinalHidden[dimension] += parameters.outputWeights[offset + token] * gradient
            }
        }
        gradients.outputBias.indices.forEach {
            gradients.outputBias[it] = dLogits[it]
        }

        var dLayerOutput = Array(length) { FloatArray(d) }
        dLayerOutput[length - 1] = dFinalHidden

        for (layerIndex in config.depth - 1 downTo 0) {
            val block = parameters.blocks[layerIndex]
            val blockPass = pass.blocks[layerIndex]
            val blockGradient = gradients.blocks[layerIndex]
            val dResidualAfterAttention = Array(length) { FloatArray(d) }
            val dNormalizedMlp = Array(length) { FloatArray(d) }

            // Reverse the SwiGLU feed-forward path for every position.
            for (position in 0 until length) {
                val dHidden = dLayerOutput[position]
                val dGated = FloatArray(f)
                for (dimension in 0 until d) {
                    val offset = dimension * f
                    for (unit in 0 until f) {
                        blockGradient.downWeights[unit * d + dimension] +=
                            blockPass.gatedIntermediate[position][unit] * dHidden[dimension]
                        dGated[unit] += block.downWeights[unit * d + dimension] *
                            dHidden[dimension]
                    }
                }

                val dGate = FloatArray(f)
                val dUp = FloatArray(f)
                for (unit in 0 until f) {
                    dGate[unit] = dGated[unit] *
                        blockPass.upProjection[position][unit] *
                        siluDerivative(blockPass.gatePreActivation[position][unit])
                    dUp[unit] = dGated[unit] * blockPass.siluGate[position][unit]
                }
                for (dimension in 0 until d) {
                    val offset = dimension * f
                    for (unit in 0 until f) {
                        blockGradient.gateWeights[offset + unit] +=
                            blockPass.normalizedMlp[position][dimension] * dGate[unit]
                        blockGradient.upWeights[offset + unit] +=
                            blockPass.normalizedMlp[position][dimension] * dUp[unit]
                        dNormalizedMlp[position][dimension] +=
                            block.gateWeights[offset + unit] * dGate[unit] +
                            block.upWeights[offset + unit] * dUp[unit]
                    }
                }

                val dFromNorm = layerNormBackward(
                    xhat = blockPass.normalizedMlpXhat[position],
                    inverseStd = blockPass.normalizedMlpInverseStd[position],
                    gamma = block.normFeedForwardGamma,
                    upstream = dNormalizedMlp[position],
                    dGamma = blockGradient.normFeedForwardGamma,
                    dBeta = blockGradient.normFeedForwardBeta
                )
                for (dimension in 0 until d) {
                    dResidualAfterAttention[position][dimension] =
                        dHidden[dimension] + dFromNorm[dimension]
                }
            }

            // Reverse the attention output projection for each query position.
            val dAttentionConcat = Array(length) { FloatArray(d) }
            for (position in 0 until length) {
                for (inputDimension in 0 until d) {
                    val offset = inputDimension * d
                    for (outputDimension in 0 until d) {
                        blockGradient.attentionOutputWeights[offset + outputDimension] +=
                            blockPass.attentionConcat[position][inputDimension] *
                            dResidualAfterAttention[position][outputDimension]
                        dAttentionConcat[position][inputDimension] +=
                            block.attentionOutputWeights[offset + outputDimension] *
                            dResidualAfterAttention[position][outputDimension]
                    }
                }
            }

            // Causal attention gradients accumulate K/V contributions from all later queries.
            val dQueryRotated = Array(length) { FloatArray(d) }
            val dKeysRotated = Array(length) { FloatArray(d) }
            val dValues = Array(length) { FloatArray(d) }
            val inverseScale = (1.0 / sqrt(headDim.toDouble())).toFloat()

            for (queryPosition in 0 until length) {
                for (head in 0 until h) {
                    val offset = head * headDim
                    val dProbabilities = FloatArray(queryPosition + 1)
                    for (keyPosition in 0..queryPosition) {
                        var derivative = 0.0f
                        val probability = blockPass.attentionWeights[queryPosition][head][keyPosition]
                        for (dimension in 0 until headDim) {
                            val feature = offset + dimension
                            derivative += dAttentionConcat[queryPosition][feature] *
                                blockPass.values[keyPosition][feature]
                            dValues[keyPosition][feature] += probability *
                                dAttentionConcat[queryPosition][feature]
                        }
                        dProbabilities[keyPosition] = derivative
                    }
                    var weightedDerivative = 0.0
                    for (keyPosition in 0..queryPosition) {
                        weightedDerivative +=
                            blockPass.attentionWeights[queryPosition][head][keyPosition].toDouble() *
                            dProbabilities[keyPosition].toDouble()
                    }
                    for (keyPosition in 0..queryPosition) {
                        val probability =
                            blockPass.attentionWeights[queryPosition][head][keyPosition]
                        val dScore = probability *
                            (dProbabilities[keyPosition] - weightedDerivative.toFloat())
                        for (dimension in 0 until headDim) {
                            val feature = offset + dimension
                            dQueryRotated[queryPosition][feature] += dScore *
                                blockPass.keysRotated[keyPosition][feature] * inverseScale
                            dKeysRotated[keyPosition][feature] += dScore *
                                blockPass.queryRotated[queryPosition][feature] * inverseScale
                        }
                    }
                }
            }

            val dNormalizedAttention = Array(length) { FloatArray(d) }
            for (position in 0 until length) {
                val dQueryRaw = dQueryRotated[position].copyOf()
                val dKeyRaw = dKeysRotated[position].copyOf()
                rotateInPlace(dQueryRaw, position, h, inverse = true)
                rotateInPlace(dKeyRaw, position, h, inverse = true)
                for (inputDimension in 0 until d) {
                    val offset = inputDimension * d
                    for (projectionDimension in 0 until d) {
                        val qGradient = dQueryRaw[projectionDimension]
                        val kGradient = dKeyRaw[projectionDimension]
                        val vGradient = dValues[position][projectionDimension]
                        blockGradient.queryWeights[offset + projectionDimension] +=
                            blockPass.normalizedAttention[position][inputDimension] * qGradient
                        blockGradient.keyWeights[offset + projectionDimension] +=
                            blockPass.normalizedAttention[position][inputDimension] * kGradient
                        blockGradient.valueWeights[offset + projectionDimension] +=
                            blockPass.normalizedAttention[position][inputDimension] * vGradient
                        dNormalizedAttention[position][inputDimension] +=
                            block.queryWeights[offset + projectionDimension] * qGradient +
                            block.keyWeights[offset + projectionDimension] * kGradient +
                            block.valueWeights[offset + projectionDimension] * vGradient
                    }
                }
            }

            // Add residual gradient and propagate through the first LayerNorm to embeddings.
            val dBlockInput = Array(length) { FloatArray(d) }
            for (position in 0 until length) {
                val dFromNorm = layerNormBackward(
                    xhat = blockPass.normalizedAttentionXhat[position],
                    inverseStd = blockPass.normalizedAttentionInverseStd[position],
                    gamma = block.normAttentionGamma,
                    upstream = dNormalizedAttention[position],
                    dGamma = blockGradient.normAttentionGamma,
                    dBeta = blockGradient.normAttentionBeta
                )
                val tokenOffset = contextTokenIds[position] * d
                for (dimension in 0 until d) {
                    dBlockInput[position][dimension] =
                        dResidualAfterAttention[position][dimension] + dFromNorm[dimension]
                    if (layerIndex == 0) {
                        gradients.tokenEmbeddings[tokenOffset + dimension] +=
                            dBlockInput[position][dimension]
                    }
                }
            }
            dLayerOutput = dBlockInput
        }

        return gradients
    }

    private fun trainOne(
        contextTokenIds: IntArray,
        targetTokenId: Int,
        learningRate: Float,
        weightDecay: Float
    ) {
        val gradients = computeGradients(contextTokenIds, targetTokenId)
        var squaredNorm = 0.0
        for (array in gradients.arrays()) {
            for (value in array) squaredNorm += value.toDouble() * value.toDouble()
        }
        val norm = sqrt(squaredNorm)
        if (norm > GRADIENT_CLIP_NORM) {
            val scale = GRADIENT_CLIP_NORM / norm
            gradients.arrays().forEach { array ->
                for (index in array.indices) array[index] = (array[index] * scale).toFloat()
            }
        }

        optimizerSteps++
        trainingSteps++
        val beta1Correction = 1.0 - BETA1.power(optimizerSteps)
        val beta2Correction = 1.0 - BETA2.power(optimizerSteps)
        val values = parameters.arrays()
        val gradientArrays = gradients.arrays()
        val first = firstMoment.arrays()
        val second = secondMoment.arrays()
        for (group in values.indices) {
            for (index in values[group].indices) {
                val gradient = gradientArrays[group][index].toDouble()
                first[group][index] =
                    (BETA1 * first[group][index] + (1.0 - BETA1) * gradient).toFloat()
                second[group][index] =
                    (BETA2 * second[group][index] + (1.0 - BETA2) *
                        gradient * gradient).toFloat()
                val firstHat = first[group][index].toDouble() / beta1Correction
                val secondHat = second[group][index].toDouble() / beta2Correction
                val update = firstHat / (sqrt(secondHat) + ADAM_EPSILON) +
                    weightDecay.toDouble() * values[group][index]
                values[group][index] =
                    (values[group][index] - learningRate * update).toFloat()
            }
        }
        require(parameters.arrays().all { values ->
            values.all(Float::isFinite)
        }) { "Treinamento KN-004 produziu pesos não finitos." }
    }

    private fun singleLoss(context: IntArray, target: Int): Double =
        -ln(max(forward(context).probabilities[target].toDouble(), 1.0e-12))

    private fun validateOptimizerArguments(learningRate: Float, weightDecay: Float) {
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f)
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f)
    }

    private fun validateParameterShapes(set: ParameterSet, config: Config) {
        val d = config.embeddingSize
        val v = config.vocabularySize
        val f = config.feedForwardSize
        require(set.blocks.size == config.depth)
        require(set.tokenEmbeddings.size == v * d)
        require(set.outputWeights.size == d * v)
        require(set.outputBias.size == v)
        for (block in set.blocks) {
            val expected = listOf(
                d * d, d * d, d * d, d * d, d, d, d, d,
                d * f, d * f, f * d
            )
            require(block.arrays().map { it.size } == expected) {
                "Dimensões de parâmetros de um bloco KN-004 inválidas."
            }
        }
        require(set.arrays().all { array -> array.all(Float::isFinite) }) {
            "Parâmetros KN-004 contêm valores não finitos."
        }
    }

    private fun selectCoordinates(gradient: FloatArray, maximum: Int): List<Int> {
        if (gradient.isEmpty()) return emptyList()
        val nonZero = gradient.indices.filter {
            abs(gradient[it]) > GRADIENT_CHECK_ZERO_THRESHOLD
        }
        val zero = gradient.indices.filter {
            abs(gradient[it]) <= GRADIENT_CHECK_ZERO_THRESHOLD
        }
        val selected = linkedSetOf<Int>()
        if (nonZero.isNotEmpty()) selected += nonZero[nonZero.size / 2]
        if (zero.isNotEmpty() && selected.size < maximum) selected += zero[zero.size / 2]
        if (selected.isEmpty()) selected += gradient.lastIndex
        var sample = 0
        while (selected.size < minOf(maximum, gradient.size)) {
            val candidate = if (maximum == 1 || gradient.size == 1) {
                gradient.size / 2
            } else {
                sample * (gradient.size - 1) / (maximum - 1)
            }
            selected += candidate
            sample++
        }
        return selected.sorted()
    }

    private fun layerNorm(
        input: FloatArray,
        gamma: FloatArray,
        beta: FloatArray
    ): Normalized {
        val size = input.size
        var mean = 0.0
        input.forEach { mean += it.toDouble() }
        mean /= size.toDouble()
        var variance = 0.0
        input.forEach {
            val centered = it.toDouble() - mean
            variance += centered * centered
        }
        variance /= size.toDouble()
        val inverseStd = (1.0 / sqrt(variance + LAYER_NORM_EPSILON)).toFloat()
        val xhat = FloatArray(size)
        val values = FloatArray(size)
        for (index in 0 until size) {
            xhat[index] = ((input[index].toDouble() - mean) * inverseStd).toFloat()
            values[index] = xhat[index] * gamma[index] + beta[index]
        }
        return Normalized(values, xhat, inverseStd)
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
        val dxhat = FloatArray(size)
        var sum = 0.0
        var sumXhat = 0.0
        for (index in 0 until size) {
            dGamma[index] += upstream[index] * xhat[index]
            dBeta[index] += upstream[index]
            dxhat[index] = upstream[index] * gamma[index]
            sum += dxhat[index].toDouble()
            sumXhat += dxhat[index].toDouble() * xhat[index].toDouble()
        }
        return FloatArray(size) { index ->
            (inverseStd.toDouble() / size.toDouble() *
                (size.toDouble() * dxhat[index].toDouble() - sum -
                    xhat[index].toDouble() * sumXhat)).toFloat()
        }
    }

    private fun rotateInPlace(
        values: FloatArray,
        position: Int,
        headCount: Int,
        inverse: Boolean = false
    ) {
        val headDim = config.embeddingSize / headCount
        for (head in 0 until headCount) {
            val offset = head * headDim
            for (pair in 0 until headDim / 2) {
                val firstIndex = offset + pair * 2
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
        logits.forEach { if (it > maximum) maximum = it }
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

    private fun sigmoid(value: Float): Float =
        if (value >= 0.0f) {
            (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()
        } else {
            val exponential = exp(value.toDouble())
            (exponential / (1.0 + exponential)).toFloat()
        }

    private fun writeFloatArray(data: DataOutputStream, values: FloatArray) {
        data.writeInt(values.size)
        values.forEach(data::writeFloat)
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
