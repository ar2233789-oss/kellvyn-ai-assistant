package com.kellvyn.assistant.neural

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KellvynNeuralModelStoreTest {
    private lateinit var root: File
    private lateinit var store: KellvynNeuralModelStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kellvyn-neural-store").toFile()
        store = KellvynNeuralModelStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun savesAndRestoresTheSameTrainedModelAndManifest() {
        val trained = trainSample()

        store.save(trained)
        val restored = store.load()

        assertTrue(store.exists())
        assertNotNull(restored)
        assertEquals(trained.model.parameterFingerprint(), restored!!.model.parameterFingerprint())
        assertEquals(trained.manifest, restored.manifest)
        assertEquals(
            trained.tokenizer.decode(trained.tokenizer.encode("Kellvyn offline")),
            restored.tokenizer.decode(restored.tokenizer.encode("Kellvyn offline"))
        )
    }

    @Test
    fun verifiedBackupRecoversWhenCurrentBundleIsCorrupted() {
        val trained = trainSample()
        store.save(trained)

        val current = File(root, "kellvyn-kbn-002.bundle")
        val backup = File(root, "kellvyn-kbn-002.bundle.bak")
        current.copyTo(backup)
        val damagedBytes = current.readBytes()
        damagedBytes[16] = (damagedBytes[16].toInt() xor 1).toByte()
        current.writeBytes(damagedBytes)

        val restored = store.load()

        assertNotNull(restored)
        assertEquals(trained.model.parameterFingerprint(), restored!!.model.parameterFingerprint())
        assertTrue(current.isFile)
        assertFalse(backup.exists())
    }

    @Test
    fun deleteRemovesCurrentModelAndRecoveryFiles() {
        store.save(trainSample())
        assertTrue(store.exists())

        store.delete()

        assertFalse(store.exists())
        assertTrue(root.listFiles().orEmpty().isEmpty())
        assertEquals(null, store.load())
    }

    private fun trainSample() = KellvynTrainingPipeline.train(
        documents = listOf(
            document("train", "Kellvyn aprende português com treino próprio.", KellvynTrainingSplit.TRAIN),
            document("validation", "O modelo prevê o próximo token.", KellvynTrainingSplit.VALIDATION),
            document("test", "A rede foi avaliada em dados separados.", KellvynTrainingSplit.TEST)
        ),
        config = KellvynTrainingConfig(
            contextLength = 8,
            embeddingSize = 8,
            seed = 303L,
            maxMerges = 12,
            epochsPerDocument = 1
        ),
        createdAtMillis = 1_800_000_300_000L
    )

    private fun document(id: String, text: String, split: KellvynTrainingSplit) =
        KellvynTrainingDocument(
            id = id,
            text = text,
            split = split,
            provenance = KellvynTrainingProvenance(
                permission = KellvynTrainingPermission.OWNER_CREATED,
                licenseId = "owner-created",
                sourceReference = "owner://store-test/$id",
                rightsEvidence = "Conteúdo artificial de teste."
            )
        )
}
