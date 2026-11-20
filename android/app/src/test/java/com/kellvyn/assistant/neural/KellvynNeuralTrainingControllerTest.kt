package com.kellvyn.assistant.neural

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
    fun lowContextExperimentIsNotInstalledOverAnyExistingCheckpoint() = runBlocking {
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
        assertFalse(store.exists())
        assertNotNull(outcome.qualityGate)
        assertTrue(outcome.qualityGate.reasons.any { it.contains("pequena demais") })
        assertTrue(outcome.detail.contains("não foi instalado"))
    }

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
