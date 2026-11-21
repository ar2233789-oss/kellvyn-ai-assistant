package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class KellvynTokenSamplerTest {
    @Test
    fun topKOneAlwaysChoosesTheHighestProbabilityToken() {
        val selected = KellvynTokenSampler.sample(
            probabilities = floatArrayOf(0.2f, 0.7f, 0.1f),
            recentTokens = emptyList(),
            config = KellvynSamplingConfig(topK = 1, topP = 0.9f),
            random = Random(10L)
        )

        assertEquals(1, selected)
    }

    @Test
    fun fixedSeedProducesReproducibleTopKTopPSamples() {
        val probabilities = floatArrayOf(0.45f, 0.30f, 0.15f, 0.10f)
        val config = KellvynSamplingConfig(
            temperature = 0.8f,
            topK = 3,
            topP = 0.9f,
            repetitionPenalty = 0.0f
        )

        val first = KellvynTokenSampler.sample(
            probabilities, emptyList(), config, Random(20261120L)
        )
        val second = KellvynTokenSampler.sample(
            probabilities, emptyList(), config, Random(20261120L)
        )

        assertEquals(first, second)
        assertTrue(first in 0..2)
    }

    @Test
    fun repetitionPenaltyCanMoveARecentlyRepeatedTokenOutOfTopK() {
        val selected = KellvynTokenSampler.sample(
            probabilities = floatArrayOf(0.6f, 0.4f),
            recentTokens = listOf(0, 0),
            config = KellvynSamplingConfig(
                temperature = 1.0f,
                topK = 1,
                topP = 1.0f,
                repetitionPenalty = 2.0f
            ),
            random = Random(5L)
        )

        assertEquals(1, selected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun samplerRejectsAnEmptyOrInvalidProbabilityDistribution() {
        KellvynTokenSampler.sample(
            probabilities = floatArrayOf(0.0f, 0.0f),
            recentTokens = emptyList()
        )
    }
}
