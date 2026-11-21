package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynMultiBlockTrainingConfig

/**
 * Capacity-conscious default for training the actual KELLVYN/1 decision model.
 *
 * The earlier 32-dimensional experiment was useful for validating the training pipeline,
 * but is not an adequate default for the installed decision brain. This profile raises
 * representation capacity while keeping the conservative estimated training working set
 * under the existing 128 MiB admission budget.
 */
internal object KellvynDecisionTrainingProfile {
    const val MAX_WORKING_SET_BYTES = 128L * 1024L * 1024L

    fun config(): KellvynMultiBlockTrainingConfig =
        KellvynMultiBlockTrainingConfig(
            contextLength = 1_024,
            embeddingSize = 128,
            headCount = 4,
            feedForwardSize = 512,
            depth = 2,
            maxMerges = 64,
            epochsPerExample = 1,
            maxTotalTrainingSteps = 100_000L,
            maxTrainingWorkingSetBytes = MAX_WORKING_SET_BYTES
        )

    fun estimatedWorkingSetBytes(): Long {
        val config = config()
        val modelConfig = com.kellvyn.assistant.neural.KellvynMultiBlockTransformer.Config(
            vocabularySize = 256 + config.maxMerges + 1,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            headCount = config.headCount,
            feedForwardSize = config.feedForwardSize,
            depth = config.depth,
            seed = config.seed
        )
        return modelConfig.estimatedTrainingWorkingSetBytes()
    }

    fun requireWithinBudget(): KellvynMultiBlockTrainingConfig {
        val config = config()
        val estimated = estimatedWorkingSetBytes()
        require(estimated <= config.maxTrainingWorkingSetBytes) {
            "O perfil de treino KN-004 excede o orçamento de memória configurado."
        }
        return config
    }
}
