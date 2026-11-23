package com.kellvyn.assistant.neural

import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

internal data class KellvynSamplingConfig(
    val temperature: Float = 0.65f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val repetitionPenalty: Float = 0.12f
) {
    init {
        require(temperature.isFinite() && temperature in 0.1f..2.0f)
        require(topK in 1..128)
        require(topP.isFinite() && topP in 0.5f..1.0f)
        require(repetitionPenalty.isFinite() && repetitionPenalty in 0.0f..2.0f)
    }
}

/** Bounded top-k/top-p sampler shared by the one local KN brain. */
internal object KellvynTokenSampler {
    private data class Candidate(val tokenId: Int, val score: Double, val weight: Double)

    fun sample(
        probabilities: FloatArray,
        recentTokens: List<Int>,
        config: KellvynSamplingConfig = KellvynSamplingConfig(),
        random: Random = Random()
    ): Int {
        require(probabilities.isNotEmpty())
        require(probabilities.all { it.isFinite() && it >= 0.0f })
        require(probabilities.any { it > 0.0f })

        val repeatCounts = HashMap<Int, Int>()
        recentTokens.forEach { token ->
            if (token in probabilities.indices) {
                repeatCounts[token] = (repeatCounts[token] ?: 0) + 1
            }
        }

        val scored = probabilities.indices.mapNotNull { tokenId ->
            val probability = probabilities[tokenId].toDouble()
            if (probability <= 0.0) return@mapNotNull null
            val penalty = config.repetitionPenalty.toDouble() *
                (repeatCounts[tokenId] ?: 0).toDouble()
            val score = ln(probability) / config.temperature.toDouble() - penalty
            Candidate(tokenId, score, 0.0)
        }.sortedByDescending { it.score }
            .take(minOf(config.topK, probabilities.size))

        require(scored.isNotEmpty())
        val maximum = scored.first().score
        val weighted = scored.map { candidate ->
            candidate.copy(weight = exp(candidate.score - maximum))
        }
        val totalWeight = weighted.sumOf { it.weight }
        require(totalWeight.isFinite() && totalWeight > 0.0)

        val nucleus = ArrayList<Candidate>()
        var cumulative = 0.0
        for (candidate in weighted) {
            nucleus += candidate
            cumulative += candidate.weight / totalWeight
            if (cumulative >= config.topP.toDouble()) break
        }

        val nucleusWeight = nucleus.sumOf { it.weight }
        val target = random.nextDouble() * nucleusWeight
        var running = 0.0
        for (candidate in nucleus) {
            running += candidate.weight
            if (target <= running) return candidate.tokenId
        }
        return nucleus.last().tokenId
    }
}
