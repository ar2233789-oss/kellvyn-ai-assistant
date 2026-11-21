package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynMultiBlockTrainingPipelineTest {
    @Test
    fun trainsKn004OnlyWithTrainSplitAndRecordsDepthAndHeldOutMetrics() {
        val examples = sampleExamples()
        val config = KellvynMultiBlockTrainingConfig(
            contextLength = 48,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            depth = 2,
            seed = 8501L,
            maxMerges = 8,
            epochsPerExample = 2,
            learningRate = 0.004f,
            weightDecay = 0.0f,
            maxTotalTrainingSteps = 10_000L
        )

        val result = KellvynMultiBlockTrainingPipeline.train(
            examples = examples,
            config = config,
            createdAtMillis = 1_800_000_850_001L
        )

        val expectedSteps = examples
            .filter { it.split == KellvynTrainingSplit.TRAIN }
            .sumOf {
                (result.tokenizer.encode(it.completion).size.toLong() + 1L) *
                    config.epochsPerExample.toLong()
            }

        assertEquals(2, result.model.config.depth)
        assertEquals(config.depth, result.manifest.depth)
        assertEquals(config.contextLength, result.manifest.contextLength)
        assertEquals(config.embeddingSize, result.manifest.embeddingSize)
        assertEquals(config.headCount, result.manifest.headCount)
        assertEquals(config.feedForwardSize, result.manifest.feedForwardSize)
        assertEquals(result.tokenizer.vocabularySize, result.manifest.vocabularySize)
        assertEquals(result.tokenizer.mergeCount, result.manifest.maxMerges)
        assertTrue(result.manifest.maxMerges <= config.maxMerges)
        assertEquals(expectedSteps, result.model.trainingSteps)
        assertEquals(expectedSteps, result.manifest.totalTrainingSteps)
        assertNotNull(result.tokenizer.endOfSequenceTokenId)
        assertEquals(3, result.manifest.entries.size)
        assertTrue(result.manifest.entries.any { it.split == KellvynTrainingSplit.TRAIN })
        assertTrue(result.manifest.entries.any { it.split == KellvynTrainingSplit.VALIDATION })
        assertTrue(result.manifest.entries.any { it.split == KellvynTrainingSplit.TEST })
        assertTrue(result.manifest.initialValidationLoss.isFinite())
        assertTrue(result.manifest.finalValidationLoss.isFinite())
        assertTrue(result.manifest.heldOutTestLoss.isFinite())
        assertFalse(KellvynMultiBlockTrainingQualityGate.evaluate(result.manifest).eligibleForProtocolBenchmark)
    }

    @Test(expected = IllegalArgumentException::class)
    @Test
    fun capsBpeMergesUsingTheActualFormattedTrainCorpusByteCount() {
        assertEquals(
            768,
            KellvynMultiBlockTrainingPipeline.effectiveMaxMerges(
                requestedMerges = 768,
                tokenizerCorpusBytes = 40_000L
            )
        )
        assertEquals(
            160,
            KellvynMultiBlockTrainingPipeline.effectiveMaxMerges(
                requestedMerges = 768,
                tokenizerCorpusBytes = 250_000L
            )
        )
        assertEquals(
            0,
            KellvynMultiBlockTrainingPipeline.effectiveMaxMerges(
                requestedMerges = 768,
                tokenizerCorpusBytes = 50_000_000L
            )
        )
    }

    fun refusesTrainingExamplesWhosePromptCannotFitTheLiveContextWindow() {
        val examples = sampleExamples().toMutableList()
        examples[0] = examples[0].copy(prompt = "P".repeat(80))
        KellvynMultiBlockTrainingPipeline.train(
            examples = examples,
            config = KellvynMultiBlockTrainingConfig(
                contextLength = 48,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                depth = 2,
                maxMerges = 0
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesTrainingDecisionsLongerThanTheLiveGenerationBudget() {
        val examples = sampleExamples().toMutableList()
        examples[0] = examples[0].copy(completion = "D".repeat(30))
        KellvynMultiBlockTrainingPipeline.train(
            examples = examples,
            config = KellvynMultiBlockTrainingConfig(
                contextLength = 48,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                depth = 2,
                maxMerges = 0
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTheSamePromptAcrossDifferentSplits() {
        val examples = listOf(
            instruction("train", "Pergunta repetida", "Resposta A", KellvynTrainingSplit.TRAIN),
            instruction("validation", "Pergunta repetida", "Resposta B", KellvynTrainingSplit.VALIDATION),
            instruction("test", "Outra pergunta", "Resposta C", KellvynTrainingSplit.TEST)
        )
        KellvynMultiBlockTrainingPipeline.train(examples)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesCredentialsInTrainingText() {
        val examples = sampleExamples().toMutableList()
        examples[0] = instruction(
            "train-secret",
            "Pergunta: qual é a senha?",
            "password = superSecretValue123",
            KellvynTrainingSplit.TRAIN
        )
        KellvynMultiBlockTrainingPipeline.train(examples)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesPersonalIdentifiersInTrainingText() {
        val examples = sampleExamples().toMutableList()
        examples[0] = instruction(
            "train-personal",
            "Pergunta: como contato suporte@example.com?",
            "Responda com cuidado.",
            KellvynTrainingSplit.TRAIN
        )
        KellvynMultiBlockTrainingPipeline.train(examples)
    }

    @Test
    fun configurationIsDepthAware() {
        assertEquals(2, KellvynMultiBlockTrainingConfig().depth)
        assertEquals(4, KellvynMultiBlockTrainingConfig(depth = 4).depth)
        assertEquals(8, KellvynMultiBlockTrainingConfig(depth = 8).depth)
        assertFalse(
            KellvynMultiBlockTrainingQualityGate.evaluate(
                manifest = sampleManifestForGate()
            ).eligibleForProtocolBenchmark
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesTrainingBeforeAllocationWhenEstimatedWorkingSetExceedsBudget() {
        KellvynMultiBlockTrainingPipeline.train(
            examples = sampleExamples(),
            config = KellvynMultiBlockTrainingConfig(
                contextLength = 256,
                embeddingSize = 128,
                headCount = 8,
                feedForwardSize = 512,
                depth = 6,
                maxMerges = 64,
                maxTrainingWorkingSetBytes = 16L * 1024L * 1024L
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun configurationRejectsHeadCountThatDoesNotDivideEmbeddingSize() {
        KellvynMultiBlockTrainingConfig(
            contextLength = 48,
            embeddingSize = 8,
            headCount = 3
        )
    }

    private fun sampleManifestForGate(): KellvynMultiBlockTrainingManifest {
        val provenance = KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://kn004-quality-test",
            rightsEvidence = "Conteúdo sintético criado para teste."
        )
        val entry = KellvynTrainingManifestEntry(
            id = "small-context",
            split = KellvynTrainingSplit.TRAIN,
            permission = provenance.permission,
            licenseId = provenance.licenseId,
            sourceReference = provenance.sourceReference,
            rightsEvidence = provenance.rightsEvidence,
            contentSha256 = "a".repeat(64)
        )
        return KellvynMultiBlockTrainingManifest(
            createdAtMillis = 1_800_000_850_002L,
            entries = listOf(
                entry,
                entry.copy(id = "validation", split = KellvynTrainingSplit.VALIDATION),
                entry.copy(id = "test", split = KellvynTrainingSplit.TEST)
            ),
            maxMerges = 8,
            contextLength = 48,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            depth = 2,
            seed = 8502L,
            epochsPerExample = 1,
            learningRate = 0.003f,
            weightDecay = 0.0f,
            initialValidationLoss = 5.0,
            finalValidationLoss = 4.0,
            heldOutTestLoss = 4.1,
            totalTrainingSteps = 10L,
            vocabularySize = 256
        )
    }

    private fun sampleExamples() = listOf(
        instruction(
            "train-1",
            "Pergunta: diga olá. Resposta:",
            "Olá.",
            KellvynTrainingSplit.TRAIN
        ),
        instruction(
            "validation-1",
            "Pergunta: diga bom dia. Resposta:",
            "Bom dia.",
            KellvynTrainingSplit.VALIDATION
        ),
        instruction(
            "test-1",
            "Pergunta: diga até logo. Resposta:",
            "Até logo.",
            KellvynTrainingSplit.TEST
        )
    )

    private fun instruction(
        id: String,
        prompt: String,
        completion: String,
        split: KellvynTrainingSplit
    ) = KellvynInstructionTrainingExample(
        id = id,
        prompt = prompt,
        completion = completion,
        split = split,
        provenance = KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://kn004-pipeline-test/$id",
            rightsEvidence = "Conteúdo sintético criado para teste do pipeline KN-004."
        )
    )
}
