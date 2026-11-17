package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynTransformerLanguageModelTest {
    @Test
    fun multiHeadTransformerGradientsMatchFiniteDifferences() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn aprende com atenção causal."),
            maxMerges = 0
        )
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 8,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 4101L
        )
        val context = tokenizer.encode("Oi")
        val target = tokenizer.encode("!").single()

        val report = model.checkGradients(
            contextTokenIds = context,
            targetTokenId = target,
            epsilon = 0.001,
            maxCoordinatesPerGroup = 2
        )

        assertTrue(report.checkedParameters >= 10)
        assertTrue("absolute gradient error was ${report.maxAbsoluteError}", report.maxAbsoluteError < 0.004)
        assertTrue("relative gradient error was ${report.maxRelativeError}", report.maxRelativeError < 0.06)
    }

    @Test
    fun supervisedCompletionTrainingReducesLossAndUpdatesOnlyResponseSteps() {
        val promptText = "Pergunta: escreva B. Resposta:"
        val completionText = "B"
        val tokenizer = KellvynBpeTokenizer.train(
            listOf(promptText, completionText),
            maxMerges = 12
        )
        val prompt = tokenizer.encode(promptText)
        val completion = tokenizer.encode(completionText)
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 48,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 4102L
        )
        val initialLoss = model.evaluateCompletionLoss(prompt, completion)

        val report = model.trainCompletion(
            promptTokenIds = prompt,
            completionTokenIds = completion,
            epochs = 180,
            learningRate = 0.01f,
            weightDecay = 0.0f
        )

        assertEquals(completion.size, report.examples)
        assertEquals(completion.size.toLong() * 180L, model.trainingSteps)
        assertTrue(report.finalLoss < initialLoss)
        assertEquals(report.finalLoss, model.evaluateCompletionLoss(prompt, completion), 1.0e-9)
    }

    @Test
    fun transformerCheckpointRestoresArchitectureWeightsAndOptimizer() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn aprende português."),
            maxMerges = 8
        )
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 16,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 4103L
        )
        model.trainCompletion(
            promptTokenIds = tokenizer.encode("Pergunta: diga sim. Resposta:"),
            completionTokenIds = tokenizer.encode("sim"),
            epochs = 4,
            weightDecay = 0.0f
        )
        val bytes = ByteArrayOutputStream().also(model::writeCheckpoint).toByteArray()

        val restored = KellvynTransformerLanguageModel.readCheckpoint(ByteArrayInputStream(bytes))

        assertEquals(model.config, restored.config)
        assertEquals(model.parameterFingerprint(), restored.parameterFingerprint())
        assertEquals(model.trainingSteps, restored.trainingSteps)
        assertEquals(
            model.predictNext(tokenizer.encode("Oi")),
            restored.predictNext(tokenizer.encode("Oi"))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHeadDimensionThatCannotBeRotatedInPairs() {
        KellvynTransformerLanguageModel.Config(
            vocabularySize = 256,
            contextLength = 16,
            embeddingSize = 12,
            headCount = 4,
            feedForwardSize = 16
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkpointRejectsModifiedTransformerWeights() {
        val tokenizer = KellvynBpeTokenizer.train(listOf("Kellvyn"), maxMerges = 0)
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 8,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12
        )
        val bytes = ByteArrayOutputStream().also(model::writeCheckpoint).toByteArray()
        bytes[20] = (bytes[20].toInt() xor 1).toByte()
        KellvynTransformerLanguageModel.readCheckpoint(ByteArrayInputStream(bytes))
    }
}
