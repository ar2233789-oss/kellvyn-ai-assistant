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

class KellvynNeuralChatRuntimeTest {
    private lateinit var root: File
    private lateinit var store: KellvynNeuralModelStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kellvyn-chat-runtime").toFile()
        store = KellvynNeuralModelStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun missingCheckpointDoesNotCreateRandomBrainOrReportReady() {
        val runtime = KellvynNeuralChatRuntime(store)

        val status = runtime.initialize()

        assertFalse(status.ready)
        assertFalse(runtime.isReady)
        assertTrue(status.detail.contains("Nenhum checkpoint"))
    }

    @Test
    fun initializeLoadsVerifiedKn004InInferenceOnlyMode() {
        val trained = trainSample()
        store.saveMultiBlock(trained)
        val runtime = KellvynNeuralChatRuntime(store)

        val status = runtime.initialize()

        assertTrue(status.ready)
        assertTrue(runtime.isReady)
        assertFalse(status.qualityApprovedForBenchmark)
        assertTrue(status.qualityReasons.any { it.contains("pequena demais") })
        assertEquals(trained.model.config.depth, status.depth)
        assertEquals(trained.model.config.contextLength, status.contextLength)
        assertEquals(trained.tokenizer.vocabularySize, status.vocabularySize)

        val compact = store.loadMultiBlockForInference()
        assertNotNull(compact)
        assertFalse(compact!!.model.hasTrainingState())

        runtime.unload()
        assertFalse(runtime.isReady)
    }

    @Test
    fun generationRefusesAReadableButQualityRejectedCheckpoint() {
        val trained = trainSample()
        store.saveMultiBlock(trained)
        val runtime = KellvynNeuralChatRuntime(store)

        val status = runtime.initialize()

        assertTrue(status.ready)
        assertFalse(status.qualityApprovedForBenchmark)
        try {
            runtime.generateReply("Olá")
            throw AssertionError("Checkpoint abaixo do quality gate não pode responder no chat.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("quality gate"))
        }
    }

    @Test
    fun numericQualityPassStillCannotAnswerBeforeHeldOutBehaviorBenchmark() {
        val trained = trainSample(contextLength = 128)
        val passingMetrics = trained.copy(
            manifest = trained.manifest.copy(
                initialValidationLoss = 5.0,
                finalValidationLoss = 3.0,
                heldOutTestLoss = 3.2
            )
        )
        store.saveMultiBlock(passingMetrics)
        val runtime = KellvynNeuralChatRuntime(store)

        val status = runtime.initialize()

        assertTrue(status.ready)
        assertTrue(status.qualityApprovedForBenchmark)
        try {
            runtime.generateReply("Olá")
            throw AssertionError("O gate numérico sozinho não pode liberar respostas.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("benchmark comportamental TEST"))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun generationIsRejectedUntilAValidatedCheckpointIsLoaded() {
        KellvynNeuralChatRuntime(store).generateReply("Olá")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankUserTextIsRejectedBeforeGeneration() {
        KellvynNeuralChatRuntime(store).generateReply("   ")
    }

    private fun trainSample(contextLength: Int = 32) = KellvynMultiBlockTrainingPipeline.train(
        examples = listOf(
            instruction("runtime-train", "Pergunta: diga olá.", "Olá.", KellvynTrainingSplit.TRAIN),
            instruction("runtime-validation", "Pergunta: diga bom dia.", "Bom dia.", KellvynTrainingSplit.VALIDATION),
            instruction("runtime-test", "Pergunta: termine.", "Até logo.", KellvynTrainingSplit.TEST)
        ),
        config = KellvynMultiBlockTrainingConfig(
            contextLength = contextLength,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            depth = 2,
            seed = 20261120L,
            maxMerges = 8,
            epochsPerExample = 1,
            learningRate = 0.003f,
            weightDecay = 0.0f
        ),
        createdAtMillis = 1_800_000_920_000L
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
            sourceReference = "owner://kn004-runtime-test/$id",
            rightsEvidence = "Conteúdo sintético criado para teste do runtime neural local."
        )
    )
}
