package com.kellvyn.assistant.neural

/**
 * Held-out behavioral benchmark for a single trained KN-004 checkpoint.
 *
 * The numeric loss gate is necessary but not sufficient. This second gate uses only TEST
 * examples and measures exact normalized matches plus token F1. A pass only authorizes the
 * next integration review; it is not a claim of general intelligence or production quality.
 */
internal object KellvynNeuralBehaviorBenchmark {
    const val MINIMUM_EXAMPLES = 20
    const val MINIMUM_EXACT_MATCH_RATE = 0.60
    const val MINIMUM_TOKEN_F1 = 0.80

    data class Report(
        val eligibleForIntegrationReview: Boolean,
        val modelFingerprint: String,
        val sampleCount: Int,
        val exactMatchRate: Double,
        val meanTokenF1: Double,
        val reasons: List<String>
    )

    fun evaluate(
        modelFingerprint: String,
        examples: List<KellvynInstructionTrainingExample>,
        generate: (KellvynInstructionTrainingExample) -> String
    ): Report {
        require(modelFingerprint.matches(Regex("[a-f0-9]{64}"))) {
            "Fingerprint do modelo inválido para o benchmark."
        }
        require(examples.all { it.split == KellvynTrainingSplit.TEST }) {
            "O benchmark comportamental aceita somente exemplos do conjunto TEST."
        }
        require(examples.map { it.id }.distinct().size == examples.size) {
            "O benchmark contém IDs duplicados."
        }
        require(examples.map { normalize(it.prompt) }.distinct().size == examples.size) {
            "O benchmark contém prompts repetidos."
        }

        val reasons = mutableListOf<String>()
        if (examples.size < MINIMUM_EXAMPLES) {
            reasons += "O benchmark precisa de pelo menos $MINIMUM_EXAMPLES exemplos TEST independentes."
        }
        if (examples.any {
                it.prompt.isBlank() || it.completion.isBlank() ||
                    it.provenance.licenseId.isBlank() ||
                    it.provenance.sourceReference.isBlank() ||
                    it.provenance.rightsEvidence.isBlank()
            }
        ) {
            reasons += "Há exemplos TEST vazios ou sem proveniência completa."
        }

        if (reasons.isNotEmpty()) {
            return Report(false, modelFingerprint, examples.size, 0.0, 0.0, reasons)
        }

        var exactMatches = 0
        var tokenF1Total = 0.0
        for (example in examples) {
            val generated = generate(example)
            val expected = normalize(example.completion)
            val actual = normalize(generated)
            if (actual == expected) exactMatches++
            tokenF1Total += tokenF1(actual, expected)
        }

        val exactRate = exactMatches.toDouble() / examples.size
        val meanF1 = tokenF1Total / examples.size
        if (exactRate < MINIMUM_EXACT_MATCH_RATE) {
            reasons += "A taxa de correspondência exata ficou abaixo de $MINIMUM_EXACT_MATCH_RATE."
        }
        if (meanF1 < MINIMUM_TOKEN_F1) {
            reasons += "O F1 médio de tokens ficou abaixo de $MINIMUM_TOKEN_F1."
        }

        return Report(
            eligibleForIntegrationReview = reasons.isEmpty(),
            modelFingerprint = modelFingerprint,
            sampleCount = examples.size,
            exactMatchRate = exactRate,
            meanTokenF1 = meanF1,
            reasons = reasons
        )
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun tokenF1(actual: String, expected: String): Double {
        val actualTokens = actual.split(' ').filter { it.isNotBlank() }
        val expectedTokens = expected.split(' ').filter { it.isNotBlank() }
        if (actualTokens.isEmpty() || expectedTokens.isEmpty()) return 0.0

        val remaining = expectedTokens.toMutableList()
        var overlap = 0
        for (token in actualTokens) {
            val index = remaining.indexOf(token)
            if (index >= 0) {
                overlap++
                remaining.removeAt(index)
            }
        }
        if (overlap == 0) return 0.0
        val precision = overlap.toDouble() / actualTokens.size
        val recall = overlap.toDouble() / expectedTokens.size
        return 2.0 * precision * recall / (precision + recall)
    }
}
