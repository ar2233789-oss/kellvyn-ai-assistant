package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynTransformerCapacityEstimatorTest {
    @Test
    fun defaultKn003ParameterAndMemoryEstimatesMatchTheActualTensorLayout() {
        val report = KellvynTransformerCapacityEstimator.estimate(
            KellvynTransformerLanguageModel.Config()
        )

        assertEquals(43_648L, report.parameterCount)
        assertEquals(174_592L, report.weightsBytes)
        assertEquals(349_184L, report.optimizerStateBytes)
        assertEquals(523_776L, report.persistedStateBytes)
        assertEquals(65_536L, report.generationKvCacheBytes)
        assertEquals(589_984L, report.residentLowerBoundBytes)
        assertEquals(523_872L, report.checkpointLowerBoundBytes)
        assertEquals(null, report.fitsMemoryBudget)
    }

    @Test
    fun depthTwoAddsOneFullBlockAndOneKvCachePerLayer() {
        val report = KellvynTransformerCapacityEstimator.estimate(
            KellvynTransformerLanguageModel.Config(),
            depth = 2
        )

        assertEquals(2, report.depth)
        assertEquals(54_016L, report.parameterCount)
        assertEquals(216_064L, report.weightsBytes)
        assertEquals(432_128L, report.optimizerStateBytes)
        assertEquals(648_192L, report.persistedStateBytes)
        assertEquals(131_072L, report.generationKvCacheBytes)
        assertEquals(780_464L, report.residentLowerBoundBytes)
        assertEquals(648_288L, report.checkpointLowerBoundBytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun depthAboveExperimentalLimitIsRejected() {
        KellvynTransformerCapacityEstimator.estimate(
            KellvynTransformerLanguageModel.Config(),
            depth = 9
        )
    }

    @Test
    fun memoryBudgetDecisionUsesConservativeResidentLowerBound() {
        val config = KellvynTransformerLanguageModel.Config(
            vocabularySize = 4_096,
            contextLength = 1_024,
            embeddingSize = 128,
            headCount = 8,
            feedForwardSize = 512
        )
        val unconstrained = KellvynTransformerCapacityEstimator.estimate(config)
        val fits = KellvynTransformerCapacityEstimator.estimate(
            config,
            memoryBudgetBytes = unconstrained.residentLowerBoundBytes
        )
        val doesNotFit = KellvynTransformerCapacityEstimator.estimate(
            config,
            memoryBudgetBytes = unconstrained.residentLowerBoundBytes - 1L
        )

        assertTrue(fits.fitsMemoryBudget == true)
        assertFalse(doesNotFit.fitsMemoryBudget == true)
        assertTrue(unconstrained.parameterCount > 43_648L)
        assertTrue(unconstrained.residentLowerBoundBytes > 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveMemoryBudgetIsRejected() {
        KellvynTransformerCapacityEstimator.estimate(
            KellvynTransformerLanguageModel.Config(),
            memoryBudgetBytes = 0L
        )
    }
}
