package com.kellvyn.assistant.neural

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynTransformerTrainingQualityGateTest {
    @Test
    fun admitsOnlyImprovedValidationAndGeneralizingHoldoutForProtocolBenchmark() {
        val report = KellvynTransformerTrainingQualityGate.evaluate(manifest())

        assertTrue(report.eligibleForProtocolBenchmark)
        assertTrue(report.relativeValidationImprovement >= 0.05)
        assertTrue(report.generalizationGap <= 0.50)
        assertTrue(report.reasons.isEmpty())
    }

    @Test
    fun rejectsCheckpointThatDidNotImproveValidation() {
        val report = KellvynTransformerTrainingQualityGate.evaluate(
            manifest(initialLoss = 4.0, validationLoss = 4.1, testLoss = 4.2)
        )

        assertFalse(report.eligibleForProtocolBenchmark)
        assertTrue(report.reasons.any { it.contains("não melhorou") })
    }

    @Test
    fun rejectsOverfitCheckpointWithLargeHeldOutGap() {
        val report = KellvynTransformerTrainingQualityGate.evaluate(
            manifest(initialLoss = 6.0, validationLoss = 4.0, testLoss = 5.0)
        )

        assertFalse(report.eligibleForProtocolBenchmark)
        assertTrue(report.reasons.any { it.contains("generalização") })
    }

    @Test
    fun rejectsAContextWindowTooSmallForAgentProtocol() {
        val report = KellvynTransformerTrainingQualityGate.evaluate(
            manifest(contextLength = 64)
        )

        assertFalse(report.eligibleForProtocolBenchmark)
        assertTrue(report.reasons.any { it.contains("janela") })
    }

    @Test
    fun rejectsIncompleteProvenanceAndMissingHoldoutSplit() {
        val base = manifest()
        val invalidEntries = base.entries
            .filterNot { it.split == KellvynTrainingSplit.TEST }
            .mapIndexed { index, entry ->
                if (index == 0) entry.copy(rightsEvidence = "") else entry
            }
        val report = KellvynTransformerTrainingQualityGate.evaluate(
            base.copy(entries = invalidEntries)
        )

        assertFalse(report.eligibleForProtocolBenchmark)
        assertTrue(report.reasons.any { it.contains("TRAIN, VALIDATION e TEST") })
        assertTrue(report.reasons.any { it.contains("proveniência") })
    }

    private fun manifest(
        contextLength: Int = 256,
        initialLoss: Double = 5.8,
        validationLoss: Double = 4.2,
        testLoss: Double = 4.5
    ) = KellvynTransformerTrainingManifest(
        createdAtMillis = 1_800_000_000_000L,
        entries = listOf(
            entry("train-1", KellvynTrainingSplit.TRAIN),
            entry("validation-1", KellvynTrainingSplit.VALIDATION),
            entry("test-1", KellvynTrainingSplit.TEST)
        ),
        maxMerges = 32,
        contextLength = contextLength,
        embeddingSize = 32,
        headCount = 4,
        feedForwardSize = 64,
        seed = 20261117L,
        epochsPerExample = 2,
        learningRate = 0.003f,
        weightDecay = 0.01f,
        initialValidationLoss = initialLoss,
        finalValidationLoss = validationLoss,
        heldOutTestLoss = testLoss,
        totalTrainingSteps = 100L,
        vocabularySize = 1024
    )

    private fun entry(id: String, split: KellvynTrainingSplit) =
        KellvynTrainingManifestEntry(
            id = id,
            split = split,
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://quality-gate/$id",
            rightsEvidence = "Amostra sintética criada para teste.",
            contentSha256 = "a".repeat(64)
        )
}
