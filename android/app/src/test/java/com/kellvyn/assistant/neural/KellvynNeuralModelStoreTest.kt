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

        val current = File(root, "kellvyn-kn-002.bundle")
        val backup = File(root, "kellvyn-kn-002.bundle.bak")
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

    @Test
    fun savesAndRestoresKn003TransformerBundleIndependently() {
        val trained = trainTransformerSample()

        store.saveTransformer(trained)
        val restored = store.loadTransformer()

        assertTrue(store.exists())
        assertNotNull(restored)
        assertEquals(trained.model.parameterFingerprint(), restored!!.model.parameterFingerprint())
        assertEquals(trained.model.config, restored.model.config)
        assertEquals(trained.manifest, restored.manifest)
    }

    @Test
    fun verifiedKn003BackupRecoversCorruptedCurrentBundle() {
        val trained = trainTransformerSample()
        store.saveTransformer(trained)

        val current = File(root, "kellvyn-kn-003.bundle")
        val backup = File(root, "kellvyn-kn-003.bundle.bak")
        current.copyTo(backup)
        val damagedBytes = current.readBytes()
        damagedBytes[18] = (damagedBytes[18].toInt() xor 1).toByte()
        current.writeBytes(damagedBytes)

        val restored = store.loadTransformer()

        assertNotNull(restored)
        assertEquals(trained.model.parameterFingerprint(), restored!!.model.parameterFingerprint())
        assertTrue(current.isFile)
        assertFalse(backup.exists())
    }

    @Test
    fun savesAndRestoresKn004BundleWithoutReplacingKn003() {
        val transformer = trainTransformerSample()
        val multiBlock = trainMultiBlockSample(seed = 8401L)

        store.saveTransformer(transformer)
        store.saveMultiBlock(multiBlock)

        val restoredTransformer = store.loadTransformer()
        val restoredMultiBlock = store.loadMultiBlock()

        assertNotNull(restoredTransformer)
        assertNotNull(restoredMultiBlock)
        assertEquals(
            transformer.model.parameterFingerprint(),
            restoredTransformer!!.model.parameterFingerprint()
        )
        assertEquals(
            multiBlock.model.parameterFingerprint(),
            restoredMultiBlock!!.model.parameterFingerprint()
        )
        assertEquals(multiBlock.manifest, restoredMultiBlock.manifest)
        assertEquals(2, restoredMultiBlock.model.config.depth)
    }

    @Test
    fun verifiedKn004BackupRecoversWhenCurrentBundleIsCorrupted() {
        val first = trainMultiBlockSample(seed = 8402L)
        val second = trainMultiBlockSample(seed = 8403L)
        store.saveMultiBlock(first)
        store.saveMultiBlock(second)

        val current = File(root, "kellvyn-kn-004.bundle")
        val backup = File(root, "kellvyn-kn-004.bundle.bak")
        assertTrue(current.isFile)
        assertTrue(backup.isFile)
        val corrupted = current.readBytes()
        corrupted[20] = (corrupted[20].toInt() xor 0x40).toByte()
        current.writeBytes(corrupted)

        val restored = store.loadMultiBlock()

        assertNotNull(restored)
        assertEquals(first.model.parameterFingerprint(), restored!!.model.parameterFingerprint())
        assertTrue(current.isFile)
        assertFalse(backup.exists())
    }

    @Test
    fun rejectedKn004TrainingDoesNotReplacePreviouslyApprovedCheckpoint() {
        val trainedApproved = trainMultiBlockSample(seed = 8404L, contextLength = 128)
        val approved = trainedApproved.copy(
            manifest = trainedApproved.manifest.copy(
                initialValidationLoss = 5.0,
                finalValidationLoss = 3.0,
                heldOutTestLoss = 3.2
            )
        )
        val approval = store.saveMultiBlockIfQualityApproved(approved)
        assertTrue(approval.eligibleForProtocolBenchmark)
        val previousFingerprint = approved.model.parameterFingerprint()

        val rejected = trainMultiBlockSample(seed = 8405L, contextLength = 32)
        var wasRejected = false
        try {
            store.saveMultiBlockIfQualityApproved(rejected)
        } catch (_: IllegalArgumentException) {
            wasRejected = true
        }

        assertTrue(wasRejected)
        val restored = store.loadMultiBlock()
        assertNotNull(restored)
        assertEquals(previousFingerprint, restored!!.model.parameterFingerprint())
        assertEquals(approved.manifest, restored.manifest)
    }

    private fun trainMultiBlockSample(seed: Long, contextLength: Int = 32) = KellvynMultiBlockTrainingPipeline.train(
        examples = listOf(
            multiBlockInstruction(
                "kn004-train-$seed",
                "Pergunta: diga olá.",
                "Olá.",
                KellvynTrainingSplit.TRAIN
            ),
            multiBlockInstruction(
                "kn004-validation-$seed",
                "Pergunta: diga bom dia.",
                "Bom dia.",
                KellvynTrainingSplit.VALIDATION
            ),
            multiBlockInstruction(
                "kn004-test-$seed",
                "Pergunta: termine.",
                "Até logo.",
                KellvynTrainingSplit.TEST
            )
        ),
        config = KellvynMultiBlockTrainingConfig(
            contextLength = contextLength,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            depth = 2,
            seed = seed,
            maxMerges = 8,
            epochsPerExample = 1,
            learningRate = 0.003f,
            weightDecay = 0.0f
        ),
        createdAtMillis = 1_800_000_840_000L + seed
    )

    private fun multiBlockInstruction(
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
            sourceReference = "owner://kn004-store-test/$id",
            rightsEvidence = "Conteúdo sintético criado para teste de persistência KN-004."
        )
    )

    private fun trainTransformerSample() = KellvynTransformerTrainingPipeline.train(
        examples = listOf(
            transformerInstruction("train", "Pergunta: diga olá.", "Olá.", KellvynTrainingSplit.TRAIN),
            transformerInstruction("validation", "Pergunta: diga bom dia.", "Bom dia.", KellvynTrainingSplit.VALIDATION),
            transformerInstruction("test", "Pergunta: termine.", "Até logo.", KellvynTrainingSplit.TEST)
        ),
        config = KellvynTransformerTrainingConfig(
            contextLength = 24,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 3033L,
            maxMerges = 8,
            epochsPerExample = 1,
            weightDecay = 0.0f
        ),
        createdAtMillis = 1_800_000_300_033L
    )

    private fun transformerInstruction(
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
            sourceReference = "owner://kn003-store-test/$id",
            rightsEvidence = "Conteúdo sintético para teste de armazenamento Transformer."
        )
    )

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
