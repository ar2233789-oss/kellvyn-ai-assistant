package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynTransformerCapacityPlannerTest {
    @Test
    fun oneLayerDefaultShapeMatchesKn003ParameterCountAndFp32Memory() {
        val estimate = KellvynTransformerCapacityPlanner.estimate(
            vocabularySize = 512,
            contextLength = 256,
            embeddingSize = 32,
            headCount = 4,
            feedForwardSize = 64,
            layerCount = 1
        )

        // KN-003: V*(2D+1) + 4D^2 + 4D + 3DF = 43,648 parameters.
        assertEquals(43_648L, estimate.parameterCount)
        assertEquals(174_592L, estimate.fp32WeightBytes)
        assertEquals(349_184L, estimate.fp32AdamMomentBytes)
        assertEquals(65_536L, estimate.fp32KvCacheBytes)
        assertEquals(240_128L, estimate.inferenceCoreBytes)
        assertEquals(589_312L, estimate.trainingCoreBytes)
    }

    @Test
    fun increasingDepthAddsIndependentBlockParametersAndKvCache() {
        val one = estimate(layers = 1)
        val two = estimate(layers = 2)
        val four = estimate(layers = 4)

        assertTrue(two.parameterCount > one.parameterCount)
        assertTrue(four.parameterCount > two.parameterCount)
        assertEquals(one.fp32KvCacheBytes * 2L, two.fp32KvCacheBytes)
        assertEquals(one.fp32KvCacheBytes * 4L, four.fp32KvCacheBytes)
        assertTrue(two.trainingCoreBytes > one.trainingCoreBytes)
        assertTrue(four.trainingCoreBytes > two.trainingCoreBytes)
    }

    @Test
    fun budgetSearchReturnsLargestLayerCountThatFits() {
        val twoLayerBudget = estimate(layers = 2).trainingCoreBytes

        assertEquals(
            2,
            KellvynTransformerCapacityPlanner.maximumLayersWithinTrainingBudget(
                vocabularySize = 512,
                contextLength = 256,
                embeddingSize = 32,
                headCount = 4,
                feedForwardSize = 64,
                budgetBytes = twoLayerBudget,
                maximumLayers = 16
            )
        )
    }

    @Test
    fun budgetSearchReturnsNullWhenOneLayerCannotFit() {
        assertNull(
            KellvynTransformerCapacityPlanner.maximumLayersWithinTrainingBudget(
                vocabularySize = 512,
                contextLength = 256,
                embeddingSize = 32,
                headCount = 4,
                feedForwardSize = 64,
                budgetBytes = 1L
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmbeddingDimensionsNotDivisibleByHeadCount() {
        KellvynTransformerCapacityPlanner.estimate(
            vocabularySize = 512,
            contextLength = 256,
            embeddingSize = 30,
            headCount = 4,
            feedForwardSize = 64,
            layerCount = 2
        )
    }

    @Test
    fun maximumSupportedDimensionsProducePositiveNonOverflowedEstimates() {
        val estimate = KellvynTransformerCapacityPlanner.estimate(
            vocabularySize = 65_536,
            contextLength = 32_768,
            embeddingSize = 8_192,
            headCount = 256,
            feedForwardSize = 32_768,
            layerCount = 256
        )

        assertTrue(estimate.parameterCount > 0L)
        assertTrue(estimate.fp32WeightBytes > 0L)
        assertTrue(estimate.fp32AdamMomentBytes > estimate.fp32WeightBytes)
        assertTrue(estimate.fp32KvCacheBytes > 0L)
        assertTrue(estimate.inferenceCoreBytes > estimate.fp32WeightBytes)
        assertTrue(estimate.trainingCoreBytes > estimate.fp32AdamMomentBytes)
    }

    private fun estimate(layers: Int) =
        KellvynTransformerCapacityPlanner.estimate(
            vocabularySize = 512,
            contextLength = 256,
            embeddingSize = 32,
            headCount = 4,
            feedForwardSize = 64,
            layerCount = layers
        )
}
