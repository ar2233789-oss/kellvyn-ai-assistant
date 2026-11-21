package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynDecisionTrainingProfileTest {
    @Test
    fun defaultDecisionProfileRaisesCapacityWithinTheDeclaredMemoryBudget() {
        val config = KellvynDecisionTrainingProfile.requireWithinBudget()

        assertEquals(1_024, config.contextLength)
        assertEquals(128, config.embeddingSize)
        assertEquals(4, config.headCount)
        assertEquals(512, config.feedForwardSize)
        assertEquals(2, config.depth)
        assertEquals(768, config.maxMerges)
        assertEquals(3, config.epochsPerExample)
        assertTrue(
            KellvynDecisionTrainingProfile.estimatedWorkingSetBytes() <=
                KellvynDecisionTrainingProfile.MAX_WORKING_SET_BYTES
        )
        assertTrue(
            KellvynDecisionTrainingProfile.estimatedWorkingSetBytes() >
                com.kellvyn.assistant.neural.KellvynMultiBlockTransformer.Config(
                    vocabularySize = 256 + config.maxMerges + 1,
                    contextLength = config.contextLength,
                    embeddingSize = config.embeddingSize,
                    headCount = config.headCount,
                    feedForwardSize = config.feedForwardSize,
                    depth = config.depth,
                    seed = config.seed
                ).estimatedTrainingWorkingSetBytes()
        )
    }
}
