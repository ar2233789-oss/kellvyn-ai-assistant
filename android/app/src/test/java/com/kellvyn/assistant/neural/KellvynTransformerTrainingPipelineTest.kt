package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynTransformerTrainingPipelineTest {
    @Test
    fun trainsKn003OnlyOnTrainResponsesAndMeasuresHeldOutSplits() {
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
    fun epochTrainingIsReproducibleWhenInputExamplesArriveInDifferentOrder() {
        val examples = sampleExamples() + instruction(
            "train-2",
            "Pergunta: conte até dois. Resposta:",
            "Um, dois.",
            KellvynTrainingSplit.TRAIN
        )
        val config = KellvynTransformerTrainingConfig(
            contextLength = 32,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 7_321L,
            maxMerges = 12,
            epochsPerExample = 3,
            weightDecay = 0.0f
        )

        val first = KellvynTransformerTrainingPipeline.train(
            examples = examples,
            config = config,
            createdAtMillis = 1_800_000_520_010L
        )
        val reordered = KellvynTransformerTrainingPipeline.train(
            examples = examples.reversed(),
            config = config,
            createdAtMillis = 1_800_000_520_010L
        )

        assertEquals(first.tokenizer.vocabularySize, reordered.tokenizer.vocabularySize)
        assertEquals(first.tokenizer.mergeCount, reordered.tokenizer.mergeCount)
        assertEquals(first.model.trainingSteps, reordered.model.trainingSteps)
        assertEquals(first.model.parameterFingerprint(), reordered.model.parameterFingerprint())
    }

    @Test
    fun trainingConfigAllowsLargerContextWithoutChangingDefaultBudget() {
        assertEquals(256, KellvynTransformerTrainingConfig().contextLength)
        val expanded = KellvynTransformerTrainingConfig(contextLength = 1_024)
        assertEquals(1_024, expanded.contextLength)
    }

    @Test(expected = IllegalArgumentException::class)
    fun trainingConfigRejectsContextAboveDeviceSafeMaximum() {
        KellvynTransformerTrainingConfig(contextLength = 2_049)
    }

    @Test
    fun tokenizerAndManifestAreBoundToKn003Configuration() {
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

    @Test
    fun pretrainingUsesOnlyTrainDocumentsAndKeepsHeldOutSplitsUntouched() {
        val documents = sampleDocuments()
        val config = KellvynTransformerTrainingConfig(
            contextLength = 32,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 6_401L,
            maxMerges = 12,
            epochsPerExample = 2,
            maxTotalTrainingSteps = 10_000L,
            weightDecay = 0.0f
        )
        val trained = KellvynTransformerTrainingPipeline.pretrainDocuments(
            documents = documents,
            config = config,
            createdAtMillis = 1_800_000_520_020L
        )
        val trainOnlyTokenizer = KellvynBpeTokenizer.train(
            corpus = documents
                .filter { it.split == KellvynTrainingSplit.TRAIN }
                .sortedBy { it.id }
                .map { it.text },
            maxMerges = config.maxMerges
        )
        val expectedSteps = documents
            .filter { it.split == KellvynTrainingSplit.TRAIN }
            .sumOf {
                trained.tokenizer.encode(it.text).size.toLong() * config.epochsPerExample
            }

        assertEquals(trainOnlyTokenizer.vocabularySize, trained.tokenizer.vocabularySize)
        assertEquals(trainOnlyTokenizer.mergeCount, trained.tokenizer.mergeCount)
        assertTrue(trained.tokenizer.endOfSequenceTokenId != null)
        assertEquals(expectedSteps, trained.model.trainingSteps)
        assertEquals(expectedSteps, trained.manifest.totalTrainingSteps)
        assertEquals(documents.size, trained.manifest.entries.size)
        assertTrue(trained.manifest.initialValidationLoss.isFinite())
        assertTrue(trained.manifest.finalValidationLoss.isFinite())
        assertTrue(trained.manifest.heldOutTestLoss.isFinite())
        assertTrue(trained.manifest.entries.any { it.split == KellvynTrainingSplit.VALIDATION })
        assertTrue(trained.manifest.entries.any { it.split == KellvynTrainingSplit.TEST })
    }

    @Test
    fun documentPretrainingIsReproducibleWhenInputOrderChanges() {
        val documents = sampleDocuments()
        val config = KellvynTransformerTrainingConfig(
            contextLength = 32,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 6_402L,
            maxMerges = 8,
            epochsPerExample = 2,
            weightDecay = 0.0f
        )
        val first = KellvynTransformerTrainingPipeline.pretrainDocuments(
            documents, config, createdAtMillis = 1_800_000_520_021L
        )
        val reordered = KellvynTransformerTrainingPipeline.pretrainDocuments(
            documents.reversed(), config, createdAtMillis = 1_800_000_520_021L
        )

        assertEquals(first.tokenizer.vocabularySize, reordered.tokenizer.vocabularySize)
        assertEquals(first.tokenizer.mergeCount, reordered.tokenizer.mergeCount)
        assertEquals(first.model.trainingSteps, reordered.model.trainingSteps)
        assertEquals(first.model.parameterFingerprint(), reordered.model.parameterFingerprint())
    }

    @Test
    fun supervisedFineTuningContinuesFromPretrainedBundleWithoutMutatingIt() {
        val pretraining = KellvynTransformerTrainingPipeline.pretrainDocuments(
            documents = sampleDocuments(),
            config = KellvynTransformerTrainingConfig(
                contextLength = 32,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 16,
                seed = 6_403L,
                maxMerges = 8,
                epochsPerExample = 1,
                weightDecay = 0.0f
            ),
            createdAtMillis = 1_800_000_520_030L
        )
        val base = KellvynRestoredTransformerBundle(
            tokenizer = pretraining.tokenizer,
            model = pretraining.model,
            manifest = pretraining.manifest
        )
        val baseFingerprint = base.model.parameterFingerprint()
        val baseSteps = base.model.trainingSteps
        val examples = sampleExamples()
        val config = KellvynTransformerFineTuneConfig(
            epochsPerExample = 2,
            learningRate = 0.0005f,
            weightDecay = 0.0f,
            maxTotalTrainingSteps = 10_000L
        )

        val fineTuned = KellvynTransformerTrainingPipeline.fineTuneInstructions(
            base = base,
            examples = examples,
            config = config,
            createdAtMillis = 1_800_000_520_031L
        )
        val expectedNewSteps = examples
            .filter { it.split == KellvynTrainingSplit.TRAIN }
            .sumOf {
                (fineTuned.tokenizer.encode(it.completion).size.toLong() + 1L) *
                    config.epochsPerExample
            }

        assertEquals(baseFingerprint, base.model.parameterFingerprint())
        assertEquals(baseSteps, base.model.trainingSteps)
        assertEquals(base.tokenizer.vocabularySize, fineTuned.tokenizer.vocabularySize)
        assertEquals(base.tokenizer.mergeCount, fineTuned.tokenizer.mergeCount)
        assertEquals(
            base.tokenizer.endOfSequenceTokenId,
            fineTuned.tokenizer.endOfSequenceTokenId
        )
        assertEquals(baseSteps + expectedNewSteps, fineTuned.model.trainingSteps)
        assertTrue(baseFingerprint != fineTuned.model.parameterFingerprint())
        assertEquals(
            base.manifest.entries.size + examples.size,
            fineTuned.manifest.entries.size
        )
        assertTrue(fineTuned.manifest.initialValidationLoss.isFinite())
        assertTrue(fineTuned.manifest.finalValidationLoss.isFinite())
        assertTrue(fineTuned.manifest.heldOutTestLoss.isFinite())
        assertTrue(fineTuned.manifest.entries.any { it.id == "train-1" })
        assertTrue(fineTuned.manifest.entries.any { it.id == "train-document-1" })
    }

    @Test
    fun fineTuningFromSameBaseAndSeedIsReproducible() {
        val pretraining = KellvynTransformerTrainingPipeline.pretrainDocuments(
            sampleDocuments(),
            KellvynTransformerTrainingConfig(
                contextLength = 32,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                seed = 6_404L,
                maxMerges = 8,
                weightDecay = 0.0f
            ),
            createdAtMillis = 1_800_000_520_032L
        )
        val base = KellvynRestoredTransformerBundle(
            pretraining.tokenizer,
            pretraining.model,
            pretraining.manifest
        )
        val config = KellvynTransformerFineTuneConfig(
            epochsPerExample = 2,
            learningRate = 0.0005f,
            weightDecay = 0.0f
        )
        val first = KellvynTransformerTrainingPipeline.fineTuneInstructions(
            base,
            sampleExamples(),
            config,
            createdAtMillis = 1_800_000_520_033L
        )
        val second = KellvynTransformerTrainingPipeline.fineTuneInstructions(
            base,
            sampleExamples().reversed(),
            config,
            createdAtMillis = 1_800_000_520_033L
        )

        assertEquals(first.model.parameterFingerprint(), second.model.parameterFingerprint())
        assertEquals(first.manifest.totalTrainingSteps, second.manifest.totalTrainingSteps)
    }

    @Test(expected = IllegalArgumentException::class)
    fun documentPretrainingRejectsDuplicateContentAcrossPartitions() {
        val documents = listOf(
            trainingDocument("train-document", "Conteúdo duplicado", KellvynTrainingSplit.TRAIN),
            trainingDocument("validation-document", "Conteúdo duplicado", KellvynTrainingSplit.VALIDATION),
            trainingDocument("test-document", "Outro conteúdo", KellvynTrainingSplit.TEST)
        )
        KellvynTransformerTrainingPipeline.pretrainDocuments(documents)
    }

    private fun sampleDocuments() = listOf(
        trainingDocument(
            "train-document-1",
            "Kellvyn aprende padrões de linguagem com documentos autorizados.",
            KellvynTrainingSplit.TRAIN
        ),
        trainingDocument(
            "train-document-2",
            "O modelo prevê o próximo token e aprende relações entre palavras.",
            KellvynTrainingSplit.TRAIN
        ),
        trainingDocument(
            "validation-document",
            "A validação mede a generalização do modelo em texto não treinado.",
            KellvynTrainingSplit.VALIDATION
        ),
        trainingDocument(
            "test-document",
            "O conjunto de teste permanece separado do treinamento.",
            KellvynTrainingSplit.TEST
        )
    )

    private fun trainingDocument(
        id: String,
        text: String,
        split: KellvynTrainingSplit
    ) = KellvynTrainingDocument(
        id = id,
        text = text,
        split = split,
        provenance = KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://kn003-pretraining-test/$id",
            rightsEvidence = "Documento sintético criado pelo proprietário para teste."
        )
    )

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
