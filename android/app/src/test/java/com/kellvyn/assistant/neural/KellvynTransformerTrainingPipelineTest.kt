package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynTransformerTrainingPipelineTest {
    @Test
    fun trainsKbn003OnlyOnTrainResponsesAndMeasuresHeldOutSplits() {
        val examples = sampleExamples()
        val config = KellvynTransformerTrainingConfig(
            contextLength = 32,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 5201L,
            maxMerges = 12,
            epochsPerExample = 2,
            maxTotalTrainingSteps = 10_000L,
            weightDecay = 0.0f
        )

        val result = KellvynTransformerTrainingPipeline.train(
            examples = examples,
            config = config,
            createdAtMillis = 1_800_000_520_000L
        )
        val trainOnlyTokenizer = KellvynBpeTokenizer.train(
            corpus = examples
                .filter { it.split == KellvynTrainingSplit.TRAIN }
                .flatMap { listOf(it.prompt, it.completion) },
            maxMerges = config.maxMerges
        )
        val expectedSteps = examples
            .filter { it.split == KellvynTrainingSplit.TRAIN }
            .sumOf {
                (result.tokenizer.encode(it.completion).size.toLong() + 1L) *
                    config.epochsPerExample
            }

        assertEquals(trainOnlyTokenizer.vocabularySize, result.tokenizer.vocabularySize)
        assertNotNull(result.tokenizer.endOfSequenceTokenId)
        assertEquals(expectedSteps, result.model.trainingSteps)
        assertEquals(expectedSteps, result.manifest.totalTrainingSteps)
        assertEquals(config.headCount, result.manifest.headCount)
        assertEquals(config.feedForwardSize, result.manifest.feedForwardSize)
        assertEquals(3, result.manifest.entries.size)
        assertTrue(result.manifest.initialValidationLoss.isFinite())
        assertTrue(result.manifest.finalValidationLoss.isFinite())
        assertTrue(result.manifest.heldOutTestLoss.isFinite())
    }

    @Test
    fun kn003BundleRestoresTokenizerTransformerOptimizerAndProvenance() {
        val trained = KellvynTransformerTrainingPipeline.train(
            examples = sampleExamples(),
            config = KellvynTransformerTrainingConfig(
                contextLength = 32,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                seed = 5202L,
                maxMerges = 8,
                epochsPerExample = 1,
                weightDecay = 0.0f
            ),
            createdAtMillis = 1_800_000_520_001L
        )
        val bytes = ByteArrayOutputStream().also {
            KellvynTransformerCheckpointBundle.writeCheckpoint(trained, it)
        }.toByteArray()

        val restored = KellvynTransformerCheckpointBundle.readCheckpoint(ByteArrayInputStream(bytes))

        assertEquals(trained.model.parameterFingerprint(), restored.model.parameterFingerprint())
        assertEquals(trained.model.config, restored.model.config)
        assertEquals(trained.model.trainingSteps, restored.model.trainingSteps)
        assertEquals(trained.manifest, restored.manifest)
        val sample = "Kellvyn continua aprendendo com dados autorizados."
        assertEquals(sample, restored.tokenizer.decode(restored.tokenizer.encode(sample)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun kn003BundleRejectsPayloadMutation() {
        val trained = KellvynTransformerTrainingPipeline.train(
            sampleExamples(),
            KellvynTransformerTrainingConfig(
                contextLength = 24,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                seed = 5203L,
                maxMerges = 8
            )
        )
        val bytes = ByteArrayOutputStream().also {
            KellvynTransformerCheckpointBundle.writeCheckpoint(trained, it)
        }.toByteArray()
        bytes[18] = (bytes[18].toInt() xor 1).toByte()
        KellvynTransformerCheckpointBundle.readCheckpoint(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun kn003TrainingRejectsTheSamePromptAcrossDifferentSplits() {
        val examples = listOf(
            instruction("train", "Pergunta repetida", "Resposta A", KellvynTrainingSplit.TRAIN),
            instruction("validation", "Pergunta repetida", "Resposta B", KellvynTrainingSplit.VALIDATION),
            instruction("test", "Outra pergunta", "Resposta C", KellvynTrainingSplit.TEST)
        )
        KellvynTransformerTrainingPipeline.train(examples)
    }

    @Test
    fun tokenizerAndManifestAreBoundToKbn003Configuration() {
        val trained = KellvynTransformerTrainingPipeline.train(
            sampleExamples(),
            KellvynTransformerTrainingConfig(
                contextLength = 24,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 16,
                seed = 5204L,
                maxMerges = 4
            )
        )
        assertEquals(trained.model.config.vocabularySize, trained.manifest.vocabularySize)
        assertEquals(trained.tokenizer.mergeCount, trained.manifest.maxMerges)
        assertEquals(trained.model.config.contextLength, trained.manifest.contextLength)
        assertEquals(trained.model.config.embeddingSize, trained.manifest.embeddingSize)
        assertNotNull(trained.manifest.entries.firstOrNull { it.split == KellvynTrainingSplit.TEST })
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
            sourceReference = "owner://kn003-training-test/$id",
            rightsEvidence = "Exemplo sintético criado pelo proprietário para teste."
        )
    )
}
