package com.kellvyn.assistant.neural

/**
 * Deterministic sizing estimate for KN-003 and planned multi-block KN-004 configurations.
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
    private const val SHARED_PARAMETER_ARRAY_COUNT = 3L
    private const val PER_BLOCK_PARAMETER_ARRAY_COUNT = 11L
    private const val MAX_ESTIMATED_DEPTH = 8
    private const val ARRAY_HEADER_ESTIMATE_BYTES = 16L

    data class Report(
        val depth: Int,
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

    /** Estimates the actual KN-004 tensor layout, including one KV cache per block. */
    fun estimate(
        config: KellvynMultiBlockTransformer.Config,
        memoryBudgetBytes: Long? = null
    ): Report = estimate(
        config = KellvynTransformerLanguageModel.Config(
            vocabularySize = config.vocabularySize,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            headCount = config.headCount,
            feedForwardSize = config.feedForwardSize,
            seed = config.seed
        ),
        memoryBudgetBytes = memoryBudgetBytes,
        depth = config.depth
    )

    fun estimate(
        config: KellvynTransformerLanguageModel.Config,
        memoryBudgetBytes: Long? = null,
        depth: Int = 1
    ): Report {
        require(memoryBudgetBytes == null || memoryBudgetBytes > 0L)
        require(depth in 1..MAX_ESTIMATED_DEPTH)

        val vocabulary = config.vocabularySize.toLong()
        val context = config.contextLength.toLong()
        val embedding = config.embeddingSize.toLong()
        val feedForward = config.feedForwardSize.toLong()

        // Shared tensors: token embeddings + vocabulary projection and bias.
        // Each block adds Q/K/V/O, four LayerNorm vectors and SwiGLU gate/up/down.
        val sharedParameterCount = (2L * vocabulary * embedding) + vocabulary
        val perBlockParameterCount =
            (4L * embedding * embedding) +
                (4L * embedding) +
                (3L * embedding * feedForward)
        val parameterCount = sharedParameterCount + perBlockParameterCount * depth.toLong()

        val weightsBytes = parameterCount * FLOAT_BYTES
        val optimizerStateBytes = parameterCount * FLOAT_BYTES * 2L
        val persistedStateBytes = weightsBytes + optimizerStateBytes

        // Every Transformer block stores one key and one value per cached token.
        val generationKvCacheBytes =
            2L * context * embedding * FLOAT_BYTES * depth.toLong()

        // Shared arrays plus per-block arrays, for weights and both Adam moment sets.
        val parameterArrayCount = SHARED_PARAMETER_ARRAY_COUNT +
            PER_BLOCK_PARAMETER_ARRAY_COUNT * depth.toLong()
        val arrayHeaders = parameterArrayCount * 3L * ARRAY_HEADER_ESTIMATE_BYTES
        val residentLowerBoundBytes =
            persistedStateBytes + generationKvCacheBytes + arrayHeaders
        val checkpointLowerBoundBytes =
            persistedStateBytes + CHECKSUM_BYTES + CHECKPOINT_HEADER_ESTIMATE_BYTES

        return Report(
            depth = depth,
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
