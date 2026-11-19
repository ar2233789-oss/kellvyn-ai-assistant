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
        assertEquals(590_656L, report.residentLowerBoundBytes)
        assertEquals(523_872L, report.checkpointLowerBoundBytes)
        assertEquals(null, report.fitsMemoryBudget)
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
