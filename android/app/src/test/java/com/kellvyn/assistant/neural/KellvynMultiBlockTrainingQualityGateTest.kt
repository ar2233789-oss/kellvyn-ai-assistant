package com.kellvyn.assistant.neural

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynMultiBlockTrainingQualityGateTest {
    @Test
    fun rejectsExperimentWithTooSmallContextEvenWhenLossImproves() {
        val result = KellvynMultiBlockTrainingQualityGate.evaluate(
            manifest(contextLength = 48)
        )

        assertFalse(result.eligibleForProtocolBenchmark)
        assertTrue(result.reasons.any { it.contains("pequena demais") })
    }

    @Test
    fun acceptsOnlyMetricsThatClearAllNumericAndProvenanceChecks() {
        val result = KellvynMultiBlockTrainingQualityGate.evaluate(
            manifest(
                contextLength = 128,
                initialLoss = 5.0,
                validationLoss = 3.0,
                testLoss = 3.2
            )
        )

        assertTrue(result.eligibleForProtocolBenchmark)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun rejectsLargeHeldOutGeneralizationGap() {
        val result = KellvynMultiBlockTrainingQualityGate.evaluate(
            manifest(
                contextLength = 128,
                initialLoss = 5.0,
                validationLoss = 3.0,
                testLoss = 4.0
            )
        )

        assertFalse(result.eligibleForProtocolBenchmark)
        assertTrue(result.reasons.any { it.contains("generalização insuficiente") })
    }

    private fun manifest(
        contextLength: Int,
        initialLoss: Double = 5.0,
        validationLoss: Double = 4.0,
        testLoss: Double = 4.1
    ): KellvynMultiBlockTrainingManifest {
        val provenance = KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://kn004-quality-gate-test",
            rightsEvidence = "Conteúdo sintético criado para teste."
        )
        fun entry(id: String, split: KellvynTrainingSplit) =
            KellvynTrainingManifestEntry(
                id = id,
                split = split,
                permission = provenance.permission,
                licenseId = provenance.licenseId,
                sourceReference = provenance.sourceReference,
                rightsEvidence = provenance.rightsEvidence,
                contentSha256 = "a".repeat(64)
            )

        return KellvynMultiBlockTrainingManifest(
            createdAtMillis = 1_800_000_920_001L,
            entries = listOf(
                entry("train", KellvynTrainingSplit.TRAIN),
                entry("validation", KellvynTrainingSplit.VALIDATION),
                entry("test", KellvynTrainingSplit.TEST)
            ),
            maxMerges = 8,
            contextLength = contextLength,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            depth = 2,
            seed = 9201L,
            epochsPerExample = 1,
            learningRate = 0.003f,
            weightDecay = 0.0f,
            initialValidationLoss = initialLoss,
            finalValidationLoss = validationLoss,
            heldOutTestLoss = testLoss,
            totalTrainingSteps = 100L,
            vocabularySize = 256
        )
    }
}
