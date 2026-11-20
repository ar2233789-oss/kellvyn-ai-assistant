package com.kellvyn.assistant.neural

/**
 * Deterministic sizing estimate for the current single-block KN-003 architecture.
 *
 * Values are deliberately reported as lower bounds: they exclude tokenizer storage,
 * corpus buffers, JVM object headers, temporary training activations, allocator overhead
 * and other application components. Never use this estimate as a substitute for device
 * profiling before enabling training or inference.
 */
internal object KellvynTransformerCapacityEstimator {
    private const val FLOAT_BYTES = 4L
    private const val CHECKSUM_BYTES = 32L
    private const val CHECKPOINT_HEADER_ESTIMATE_BYTES = 64L
    private const val PARAMETER_ARRAY_COUNT = 14L
    private const val ARRAY_HEADER_ESTIMATE_BYTES = 16L

    data class Report(
        val parameterCount: Long,
        val weightsBytes: Long,
        val optimizerStateBytes: Long,
        val persistedStateBytes: Long,
        val generationKvCacheBytes: Long,
        val residentLowerBoundBytes: Long,
        val checkpointLowerBoundBytes: Long,
        val memoryBudgetBytes: Long?,
        val fitsMemoryBudget: Boolean?
    )

    fun estimate(
        config: KellvynTransformerLanguageModel.Config,
        memoryBudgetBytes: Long? = null
    ): Report {
        require(memoryBudgetBytes == null || memoryBudgetBytes > 0L)

        val vocabulary = config.vocabularySize.toLong()
        val context = config.contextLength.toLong()
        val embedding = config.embeddingSize.toLong()
        val feedForward = config.feedForwardSize.toLong()

        // KN-003 tensors: token embeddings + Q/K/V/O + four LayerNorm vectors
        // + SwiGLU gate/up/down + vocabulary projection and bias.
        val parameterCount =
            (2L * vocabulary * embedding) +
                (4L * embedding * embedding) +
                (4L * embedding) +
                (3L * embedding * feedForward) +
                vocabulary

        val weightsBytes = parameterCount * FLOAT_BYTES
        val optimizerStateBytes = parameterCount * FLOAT_BYTES * 2L
        val persistedStateBytes = weightsBytes + optimizerStateBytes

        // Single-block incremental attention stores one key and one value per token.
        val generationKvCacheBytes = 2L * context * embedding * FLOAT_BYTES

        // Include a small lower-bound estimate for the three sets of FloatArray headers.
        val arrayHeaders = PARAMETER_ARRAY_COUNT * 3L * ARRAY_HEADER_ESTIMATE_BYTES
        val residentLowerBoundBytes =
            persistedStateBytes + generationKvCacheBytes + arrayHeaders
        val checkpointLowerBoundBytes =
            persistedStateBytes + CHECKSUM_BYTES + CHECKPOINT_HEADER_ESTIMATE_BYTES

        return Report(
            parameterCount = parameterCount,
            weightsBytes = weightsBytes,
            optimizerStateBytes = optimizerStateBytes,
            persistedStateBytes = persistedStateBytes,
            generationKvCacheBytes = generationKvCacheBytes,
            residentLowerBoundBytes = residentLowerBoundBytes,
            checkpointLowerBoundBytes = checkpointLowerBoundBytes,
            memoryBudgetBytes = memoryBudgetBytes,
            fitsMemoryBudget = memoryBudgetBytes?.let { residentLowerBoundBytes <= it }
        )
    }
}
