package com.kellvyn.assistant.neural

/**
 * Deterministic memory/parameter estimator for planning original dense KN Transformer sizes.
 *
 * The estimate covers trainable parameters, FP32 weights, two FP32 Adam moments and the
 * incremental key/value cache. It deliberately does not pretend to predict Android's exact
 * process RSS: allocator overhead, tokenizer tables, activations, JVM objects, runtime buffers,
 * and other application components must be measured separately on the target device.
 */
internal object KellvynTransformerCapacityPlanner {
    data class Estimate(
        val vocabularySize: Int,
        val contextLength: Int,
        val embeddingSize: Int,
        val headCount: Int,
        val feedForwardSize: Int,
        val layerCount: Int,
        val parameterCount: Long,
        val fp32WeightBytes: Long,
        val fp32AdamMomentBytes: Long,
        val fp32KvCacheBytes: Long,
        val inferenceCoreBytes: Long,
        val trainingCoreBytes: Long
    )

    /**
     * Parameter formula matches the current KN block layout:
     * token embedding + untied output projection/bias + per-layer Q/K/V/O,
     * two affine LayerNorms, and three SwiGLU matrices.
     *
     * No final LayerNorm or projection biases are counted because KN-003 does not contain them.
     */
    fun estimate(
        vocabularySize: Int,
        contextLength: Int,
        embeddingSize: Int,
        headCount: Int,
        feedForwardSize: Int,
        layerCount: Int,
        bytesPerWeight: Int = Float.SIZE_BYTES,
        bytesPerKvValue: Int = Float.SIZE_BYTES
    ): Estimate {
        require(vocabularySize in 256..65_536)
        require(contextLength in 1..32_768)
        require(embeddingSize in 8..8_192)
        require(headCount in 1..256)
        require(embeddingSize % headCount == 0)
        require(feedForwardSize in 4..32_768)
        require(layerCount in 1..256)
        require(bytesPerWeight in 1..8)
        require(bytesPerKvValue in 1..8)

        val d = embeddingSize.toLong()
        val v = vocabularySize.toLong()
        val f = feedForwardSize.toLong()
        val layers = layerCount.toLong()
        val context = contextLength.toLong()

        val embeddingAndOutput = multiplyExact(v, addExact(multiplyExact(2L, d), 1L))
        val attentionMatrices = multiplyExact(4L, multiplyExact(d, d))
        val twoAffineNorms = multiplyExact(4L, d)
        val swigluMatrices = multiplyExact(3L, multiplyExact(d, f))
        val parametersPerLayer = addExact(
            addExact(attentionMatrices, twoAffineNorms),
            swigluMatrices
        )
        val parameterCount = addExact(
            embeddingAndOutput,
            multiplyExact(layers, parametersPerLayer)
        )

        val fp32WeightBytes = multiplyExact(parameterCount, bytesPerWeight.toLong())
        val fp32AdamMomentBytes = multiplyExact(
            multiplyExact(parameterCount, ADAM_MOMENT_COPIES.toLong()),
            bytesPerWeight.toLong()
        )
        // K and V each contain [layers, context, embedding] values.
        val kvValues = multiplyExact(
            multiplyExact(2L, multiplyExact(layers, context)),
            d
        )
        val fp32KvCacheBytes = multiplyExact(kvValues, bytesPerKvValue.toLong())

        return Estimate(
            vocabularySize = vocabularySize,
            contextLength = contextLength,
            embeddingSize = embeddingSize,
            headCount = headCount,
            feedForwardSize = feedForwardSize,
            layerCount = layerCount,
            parameterCount = parameterCount,
            fp32WeightBytes = fp32WeightBytes,
            fp32AdamMomentBytes = fp32AdamMomentBytes,
            fp32KvCacheBytes = fp32KvCacheBytes,
            inferenceCoreBytes = addExact(fp32WeightBytes, fp32KvCacheBytes),
            trainingCoreBytes = addExact(
                addExact(fp32WeightBytes, fp32AdamMomentBytes),
                fp32KvCacheBytes
            )
        )
    }

    /**
     * Returns the greatest layer count whose estimated FP32 training core fits the explicit
     * budget, or null if even one layer does not fit. This is a lower-bound estimate, not a
     * device-safety guarantee; callers must leave headroom for activations and Android overhead.
     */
    fun maximumLayersWithinTrainingBudget(
        vocabularySize: Int,
        contextLength: Int,
        embeddingSize: Int,
        headCount: Int,
        feedForwardSize: Int,
        budgetBytes: Long,
        maximumLayers: Int = 128
    ): Int? {
        require(budgetBytes > 0L)
        require(maximumLayers in 1..256)

        var low = 1
        var high = maximumLayers
        var best: Int? = null
        while (low <= high) {
            val middle = low + (high - low) / 2
            val fits = try {
                estimate(
                    vocabularySize = vocabularySize,
                    contextLength = contextLength,
                    embeddingSize = embeddingSize,
                    headCount = headCount,
                    feedForwardSize = feedForwardSize,
                    layerCount = middle
                ).trainingCoreBytes <= budgetBytes
            } catch (_: ArithmeticException) {
                false
            }
            if (fits) {
                best = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return best
    }

    private fun addExact(left: Long, right: Long): Long =
        Math.addExact(left, right)

    private fun multiplyExact(left: Long, right: Long): Long =
        Math.multiplyExact(left, right)

    private const val ADAM_MOMENT_COPIES = 2
}
