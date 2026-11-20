package com.kellvyn.assistant.neural

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class KellvynNeuralTrainingControllerTest {
    private lateinit var root: File
    private lateinit var store: KellvynNeuralModelStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kellvyn-neural-trainer").toFile()
        store = KellvynNeuralModelStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun lowContextExperimentIsNotInstalledWhenNoCheckpointExists() = runBlocking {
        val outcome = KellvynNeuralTrainingController(store).trainAndInstall(
            examples = sampleExamples(),
            config = KellvynMultiBlockTrainingConfig(
                contextLength = 48,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                depth = 2,
                maxMerges = 8,
                epochsPerExample = 1,
                weightDecay = 0.0f
            ),
            createdAtMillis = 1_800_000_930_000L
        )

        assertFalse(outcome.installed)
        assertTrue(outcome.heldOutTestExamples.isEmpty())
        assertFalse(store.exists())
        assertNotNull(outcome.qualityGate)
        assertTrue(outcome.qualityGate.reasons.any { it.contains("pequena demais") })
        assertTrue(outcome.detail.contains("não foi instalado"))
    }

    @Test
    fun rejectedTrainingPreservesThePreviouslyInstalledCheckpoint() = runBlocking {
        val trained = KellvynMultiBlockTrainingPipeline.train(
            examples = sampleExamples(),
            config = KellvynMultiBlockTrainingConfig(
                contextLength = 128,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                depth = 2,
                maxMerges = 8,
                epochsPerExample = 1,
                weightDecay = 0.0f
            ),
            createdAtMillis = 1_800_000_930_001L
        )
        val existing = trained.copy(
            manifest = trained.manifest.copy(
                initialValidationLoss = 5.0,
                finalValidationLoss = 3.0,
                heldOutTestLoss = 3.2
            )
        )
        store.saveMultiBlock(existing)
        val previousFingerprint = existing.model.parameterFingerprint()

        val outcome = KellvynNeuralTrainingController(store).trainAndInstall(
            examples = sampleExamples(),
            config = KellvynMultiBlockTrainingConfig(
                contextLength = 48,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 12,
                depth = 2,
                maxMerges = 8,
                epochsPerExample = 1,
                weightDecay = 0.0f
            ),
            createdAtMillis = 1_800_000_930_002L
        )

        assertFalse(outcome.installed)
        assertTrue(outcome.heldOutTestExamples.isEmpty())
        val restored = store.loadMultiBlockForInference()
        assertNotNull(restored)
        assertEquals(previousFingerprint, restored!!.model.parameterFingerprint())
    }

    @Test
    fun auditJsonlIsReadOnlyAndDoesNotAllocateOrInstallTheModel() = runBlocking {
        val source = (0 until 300).joinToString("\n", postfix = "\n") { jsonlRecord(it) }
        var inputClosed = false
        val input = object : ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)) {
            override fun close() {
                inputClosed = true
                super.close()
            }
        }
        val report = KellvynNeuralTrainingController(store).auditJsonl(
            input = input,
            config = smallTrainingConfig(),
            maxExamples = 300,
            minimumTrainExamples = 100,
            minimumValidationExamples = 20,
            minimumTestExamples = 20
        )

        assertTrue(report.readyForTraining)
        assertEquals(300L, report.validRecords)
        assertEquals(300L, report.totalLines)
        assertFalse(store.exists())
        assertFalse(inputClosed)
    }

    @Test
    fun jsonlPreflightRejectsInsufficientPartitionsWithoutAllocatingOrInstallingModel() = runBlocking {
        val source = (0 until 10).joinToString("\n", postfix = "\n") { jsonlRecord(it) }
        val result = KellvynNeuralTrainingController(store).trainAndInstallFromJsonl(
            input = ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            config = smallTrainingConfig(),
            maxExamples = 50,
            minimumTrainExamples = 10,
            minimumValidationExamples = 5,
            minimumTestExamples = 5
        )

        assertFalse(result.audit.readyForTraining)
        assertNull(result.training)
        assertFalse(store.exists())
        assertTrue(result.audit.sourceSha256.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun rejectedJsonlPreflightPreservesPreviouslyInstalledCheckpoint() = runBlocking {
        val validMetrics = KellvynMultiBlockTrainingPipeline.train(
            examples = sampleExamples(),
            config = smallTrainingConfig().copy(contextLength = 128),
            createdAtMillis = 1_800_000_940_010L
        ).let { trained ->
            trained.copy(
                manifest = trained.manifest.copy(
                    initialValidationLoss = 5.0,
                    finalValidationLoss = 3.0,
                    heldOutTestLoss = 3.2
                )
            )
        }
        store.saveMultiBlock(validMetrics)
        val previousFingerprint = validMetrics.model.parameterFingerprint()

        val tooSmallSource = (0 until 10).joinToString("\n", postfix = "\n") { jsonlRecord(it) }
        val outcome = KellvynNeuralTrainingController(store).trainAndInstallFromJsonl(
            input = ByteArrayInputStream(tooSmallSource.toByteArray(Charsets.UTF_8)),
            config = smallTrainingConfig(),
            maxExamples = 50,
            minimumTrainExamples = 10,
            minimumValidationExamples = 5,
            minimumTestExamples = 5
        )

        assertFalse(outcome.audit.readyForTraining)
        assertNull(outcome.training)
        val restored = store.loadMultiBlockForInference()
        assertNotNull(restored)
        assertEquals(previousFingerprint, restored!!.model.parameterFingerprint())
    }

    @Test
    fun jsonlPreflightFeedsOnlyItsBoundedSampleIntoTheExistingTrainingController() = runBlocking {
        val source = (0 until 30).joinToString("\n", postfix = "\n") { jsonlRecord(it) }
        val result = KellvynNeuralTrainingController(store).trainAndInstallFromJsonl(
            input = ByteArrayInputStream(source.toByteArray(Charsets.UTF_8)),
            config = smallTrainingConfig(),
            maxExamples = 50,
            minimumTrainExamples = 10,
            minimumValidationExamples = 1,
            minimumTestExamples = 1,
            createdAtMillis = 1_800_000_940_000L
        )

        assertTrue(result.audit.readyForTraining)
        assertTrue(result.audit.selectedTrain >= 10)
        assertTrue(result.audit.selectedValidation >= 1)
        assertTrue(result.audit.selectedTest >= 1)
        assertNotNull(result.training)
        // The intentionally small context is rejected by the numeric gate; no checkpoint is installed.
        assertFalse(result.training!!.installed)
        assertFalse(store.exists())
    }

    private fun smallTrainingConfig() = KellvynMultiBlockTrainingConfig(
        contextLength = 48,
        embeddingSize = 8,
        headCount = 2,
        feedForwardSize = 12,
        depth = 2,
        seed = 20261120L,
        maxMerges = 8,
        epochsPerExample = 1,
        maxTotalTrainingSteps = 10_000L,
        weightDecay = 0.0f
    )

    private fun jsonlRecord(index: Int): String =
        """{"id":"controller-corpus-$index","prompt":"Pergunta: diga o número $index.","completion":"A resposta é $index.","permission":"OWNER_CREATED","licenseId":"owner-created","sourceReference":"owner://controller-corpus/$index","rightsEvidence":"Texto sintético criado para teste local."}"""

    private fun sampleExamples() = listOf(
        instruction("trainer-train", "Pergunta: diga olá.", "Olá.", KellvynTrainingSplit.TRAIN),
        instruction("trainer-validation", "Pergunta: diga bom dia.", "Bom dia.", KellvynTrainingSplit.VALIDATION),
        instruction("trainer-test", "Pergunta: termine.", "Até logo.", KellvynTrainingSplit.TEST)
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
            sourceReference = "owner://kn004-controller-test/$id",
            rightsEvidence = "Conteúdo sintético criado para teste do controlador de treino."
        )
    )
}
