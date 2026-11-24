package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynAttentionLanguageModelTest {
    @Test
    fun tokenizerAndNeuralModelCanTrainTogetherFromOwnVocabulary() {
        val corpus = listOf(
            "Kellvyn aprende português do zero.",
            "O modelo aprende a prever o próximo token."
        )
        val tokenizer = KellvynBpeTokenizer.train(corpus, maxMerges = 48)
        val model = KellvynAttentionLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 8,
            embeddingSize = 8,
            seed = 20261116L
        )
        val report = model.trainText(
            text = corpus.joinToString(" "),
            tokenizer = tokenizer,
            epochs = 2
        )

        assertTrue(report.initialLoss.isFinite())
        assertTrue(report.finalLoss.isFinite())
        assertTrue(report.totalSteps > 0L)
        assertEquals(tokenizer.vocabularySize, model.config.vocabularySize)
        assertEquals(
            corpus.joinToString(" "),
            tokenizer.decode(tokenizer.encode(corpus.joinToString(" ")))
        )
    }

    @Test
    fun instructionTrainingLearnsEosAndGenerationStopsBeforeTheControlToken() {
        val promptText = "Pergunta: finalize a resposta."
        val tokenizer = KellvynBpeTokenizer.train(listOf(promptText), maxMerges = 8)
        val model = KellvynAttentionLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 32,
            embeddingSize = 8,
            seed = 4108L
        )
        val prompt = tokenizer.encode(promptText)
        val eos = requireNotNull(tokenizer.endOfSequenceTokenId)

        model.trainCompletion(
            promptTokenIds = prompt,
            completionTokenIds = intArrayOf(eos),
            epochs = 400,
            learningRate = 0.01f,
            weightDecay = 0.0f
        )

        assertEquals(eos, model.predictNext(prompt))
        assertEquals(prompt.toList(), model.generate(prompt, 8, endTokenId = eos).toList())
        assertEquals(promptText, model.generateText(promptText, tokenizer, 8))
    }

    @Test
    fun analyticGradientsMatchFiniteDifferences() {
        val model = KellvynAttentionLanguageModel.create(
            KellvynAttentionLanguageModel.Config(
                vocabularySize = 256,
                contextLength = 4,
                embeddingSize = 8,
                seed = 31L
            )
        )
        val report = model.checkGradients(
            contextTokenIds = intArrayOf(65, 66, 65),
            targetTokenId = 66,
            epsilon = 0.001,
            maxCoordinatesPerGroup = 3
        )

        assertTrue(report.checkedParameters >= 10)
        assertTrue(
            "Erro absoluto excessivo: ${report.maxAbsoluteError}",
            report.maxAbsoluteError < 0.003
        )
    }

    @Test
    fun modelLearnsARepeatedAlternatingTokenPatternFromRandomWeights() {
        val model = KellvynAttentionLanguageModel.create(
            KellvynAttentionLanguageModel.Config(
                vocabularySize = 256,
                contextLength = 4,
                embeddingSize = 8,
                seed = 2026L
            )
        )
        val tokens = intArrayOf(65, 66, 65, 66, 65, 66, 65, 66, 65, 66, 65, 66)
        val report = model.trainTokens(tokens, epochs = 100, learningRate = 0.01f)

        assertTrue("A loss não diminuiu: ${report.initialLoss} -> ${report.finalLoss}",
            report.finalLoss < report.initialLoss * 0.85)
        assertTrue(model.trainingSteps > 0L)
    }

    @Test
    fun ownCheckpointRestoresParametersOptimizerAndPredictions() {
        val model = KellvynAttentionLanguageModel.create(
            KellvynAttentionLanguageModel.Config(
                vocabularySize = 256,
                contextLength = 5,
                embeddingSize = 8,
                seed = 77L
            )
        )
        model.trainTokens(intArrayOf(65, 66, 65, 66, 65, 66), epochs = 12)

        val output = ByteArrayOutputStream()
        model.writeCheckpoint(output)
        val restored = KellvynAttentionLanguageModel.readCheckpoint(
            ByteArrayInputStream(output.toByteArray())
        )

        assertEquals(model.parameterFingerprint(), restored.parameterFingerprint())
        assertEquals(model.trainingSteps, restored.trainingSteps)
        assertEquals(
            model.predictNext(intArrayOf(65, 66, 65)),
            restored.predictNext(intArrayOf(65, 66, 65))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkpointRejectsCorruptedPayload() {
        val model = KellvynAttentionLanguageModel.create()
        val output = ByteArrayOutputStream()
        model.writeCheckpoint(output)
        val bytes = output.toByteArray()
        bytes[12] = (bytes[12].toInt() xor 1).toByte()
        KellvynAttentionLanguageModel.readCheckpoint(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun trainingRejectsOutOfVocabularyTokens() {
        val model = KellvynAttentionLanguageModel.create()
        model.trainTokens(intArrayOf(65, 9000), epochs = 1)
    }
}
