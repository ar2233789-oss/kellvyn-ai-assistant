package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynTrainingPipelineTest {
    @Test
    fun trainsTokenizerOnlyOnTrainAndMeasuresHeldOutSplits() {
        val documents = sampleDocuments()
        val config = KellvynTrainingConfig(
            contextLength = 8,
            embeddingSize = 8,
            seed = 991L,
            maxMerges = 20,
            epochsPerDocument = 2,
            maxTotalTrainingSteps = 10_000
        )

        val result = KellvynTrainingPipeline.train(
            documents = documents,
            config = config,
            createdAtMillis = 1_800_000_100_000L
        )
        val trainOnlyTokenizer = KellvynBpeTokenizer.train(
            corpus = documents.filter { it.split == KellvynTrainingSplit.TRAIN }.map { it.text },
            maxMerges = config.maxMerges
        )

        val expectedTrainIds = documents
            .filter { it.split == KellvynTrainingSplit.TRAIN }
            .map { it.id }
            .toSet()
        assertEquals(expectedTrainIds.size, result.manifest.entries.count {
            it.split == KellvynTrainingSplit.TRAIN
        })
        assertEquals(trainOnlyTokenizer.vocabularySize, result.tokenizer.vocabularySize)
        assertEquals(result.tokenizer.vocabularySize, result.model.config.vocabularySize)
        assertTrue(result.model.trainingSteps > 0L)
        assertTrue(result.manifest.initialValidationLoss.isFinite())
        assertTrue(result.manifest.finalValidationLoss.isFinite())
        assertTrue(result.manifest.heldOutTestLoss.isFinite())
        assertEquals(result.model.trainingSteps, result.manifest.totalTrainingSteps)
        assertTrue(
            documents.first { it.split == KellvynTrainingSplit.VALIDATION }.text !=
                documents.first { it.split == KellvynTrainingSplit.TEST }.text
        )
    }

    @Test
    fun completeBundleRestoresTokenizerWeightsOptimizerAndProvenance() {
        val result = KellvynTrainingPipeline.train(
            sampleDocuments(),
            KellvynTrainingConfig(
                contextLength = 8,
                embeddingSize = 8,
                seed = 101L,
                maxMerges = 16,
                epochsPerDocument = 1
            ),
            createdAtMillis = 1_800_000_200_000L
        )
        val bytes = ByteArrayOutputStream().also {
            KellvynNeuralCheckpointBundle.writeCheckpoint(result, it)
        }.toByteArray()

        val restored = KellvynNeuralCheckpointBundle.readCheckpoint(ByteArrayInputStream(bytes))

        assertEquals(result.model.parameterFingerprint(), restored.model.parameterFingerprint())
        assertEquals(result.model.trainingSteps, restored.model.trainingSteps)
        assertEquals(result.manifest, restored.manifest)
        val sample = "Kellvyn cria inteligência própria em português."
        assertEquals(sample, restored.tokenizer.decode(restored.tokenizer.encode(sample)))
        assertEquals(
            result.model.predictNext(result.tokenizer.encode("Oi")),
            restored.model.predictNext(restored.tokenizer.encode("Oi"))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun bundleRejectsModifiedPayload() {
        val result = KellvynTrainingPipeline.train(
            sampleDocuments(),
            KellvynTrainingConfig(
                contextLength = 8,
                embeddingSize = 8,
                maxMerges = 8,
                epochsPerDocument = 1
            )
        )
        val bytes = ByteArrayOutputStream().also {
            KellvynNeuralCheckpointBundle.writeCheckpoint(result, it)
        }.toByteArray()
        bytes[16] = (bytes[16].toInt() xor 1).toByte()
        KellvynNeuralCheckpointBundle.readCheckpoint(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateContentAcrossTrainAndValidation() {
        val docs = sampleDocuments().toMutableList()
        docs[1] = docs[1].copy(text = docs[0].text)
        KellvynTrainingPipeline.train(docs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCredentialBearingTrainingSamples() {
        val docs = sampleDocuments().toMutableList()
        docs[0] = docs[0].copy(
            text = "api_key = \"ghp_abcdefghijklmnopqrstuvwxyz1234567890\""
        )
        KellvynTrainingPipeline.train(docs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresExplicitTrainValidationAndTestPartitions() {
        val docs = sampleDocuments().filter { it.split != KellvynTrainingSplit.TEST }
        KellvynTrainingPipeline.train(docs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCorpusWithoutRightsEvidence() {
        KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.PERMISSIVE_LICENSE,
            licenseId = "CC-BY-4.0",
            sourceReference = "https://example.test/dataset",
            rightsEvidence = ""
        )
    }

    private fun sampleDocuments() = listOf(
        document("train-1", "Kellvyn aprende português e reconhece palavras.", KellvynTrainingSplit.TRAIN),
        document("validation-1", "O cérebro Kellvyn prevê o próximo token.", KellvynTrainingSplit.VALIDATION),
        document("test-1", "A inteligência própria aprende com dados permitidos.", KellvynTrainingSplit.TEST)
    )

    private fun document(
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
            sourceReference = "owner://kellvyn-test/$id",
            rightsEvidence = "Texto sintético criado para teste unitário."
        )
    )
}
