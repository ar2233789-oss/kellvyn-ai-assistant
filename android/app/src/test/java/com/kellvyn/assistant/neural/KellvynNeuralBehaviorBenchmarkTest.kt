package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynNeuralBehaviorBenchmarkTest {
    @Test
    fun rejectsTooSmallHeldOutSetWithoutCallingGenerator() {
        var calls = 0
        val examples = examples(5)

        val report = KellvynNeuralBehaviorBenchmark.evaluate(
            modelFingerprint = FINGERPRINT,
            examples = examples
        ) {
            calls++
            it.completion
        }

        assertFalse(report.eligibleForIntegrationReview)
        assertEquals(0, calls)
        assertTrue(report.reasons.any { it.contains("pelo menos 20") })
    }

    @Test
    fun exactHeldOutResponsesPassBothBehaviorMetrics() {
        val examples = examples(20)
        val report = KellvynNeuralBehaviorBenchmark.evaluate(
            modelFingerprint = FINGERPRINT,
            examples = examples
        ) { it.completion }

        assertTrue(report.eligibleForIntegrationReview)
        assertEquals(1.0, report.exactMatchRate, 0.0)
        assertEquals(1.0, report.meanTokenF1, 0.0)
    }

    @Test
    fun unrelatedResponsesFailBehaviorThresholds() {
        val report = KellvynNeuralBehaviorBenchmark.evaluate(
            modelFingerprint = FINGERPRINT,
            examples = examples(20)
        ) { "resposta completamente diferente" }

        assertFalse(report.eligibleForIntegrationReview)
        assertTrue(report.reasons.size >= 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsValidationOrTrainingExamples() {
        KellvynNeuralBehaviorBenchmark.evaluate(
            modelFingerprint = FINGERPRINT,
            examples = listOf(example(0).copy(split = KellvynTrainingSplit.TRAIN))
        ) { it.completion }
    }

    private fun examples(count: Int) = (0 until count).map(::example)

    private fun example(index: Int) = KellvynInstructionTrainingExample(
        id = "behavior-test-$index",
        prompt = "Pergunta: qual o número $index? Resposta:",
        completion = "O número é $index.",
        split = KellvynTrainingSplit.TEST,
        provenance = KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://kn004-behavior-test/$index",
            rightsEvidence = "Exemplo sintético de teste comportamental."
        )
    )

    private companion object {
        const val FINGERPRINT = "a".repeat(64)
    }
}
