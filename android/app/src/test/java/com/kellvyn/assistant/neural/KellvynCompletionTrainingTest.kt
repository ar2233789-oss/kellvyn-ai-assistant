package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynCompletionTrainingTest {
    @Test
    fun learnsCompletionConditionedOnPromptAndExcludesPromptFromTrainingLoss() {
        val promptText = "Pergunta: escreva a letra B.\nResposta:"
        val completionText = "B"
        val tokenizer = KellvynBpeTokenizer.train(
            corpus = listOf(promptText + completionText),
            maxMerges = 16
        )
        val prompt = tokenizer.encode(promptText)
        val completion = tokenizer.encode(completionText)
        val model = KellvynAttentionLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 64,
            embeddingSize = 8,
            seed = 771L
        )
        val initialLoss = model.evaluateCompletionLoss(prompt, completion)

        val report = model.trainCompletion(
            promptTokenIds = prompt,
            completionTokenIds = completion,
            epochs = 200,
            learningRate = 0.01f,
            weightDecay = 0.0f
        )

        assertEquals(completion.size, report.examples)
        assertEquals(completion.size.toLong() * 200L, model.trainingSteps)
        assertTrue(report.initialLoss.isFinite())
        assertTrue(report.finalLoss < initialLoss)
        assertEquals(report.finalLoss, model.evaluateCompletionLoss(prompt, completion), 1.0e-9)
    }

    @Test
    fun supervisedCompletionWeightsSurviveCheckpointRestore() {
        val promptText = "Pergunta: responda sim.\nResposta:"
        val completionText = "sim"
        val tokenizer = KellvynBpeTokenizer.train(
            corpus = listOf(promptText + completionText),
            maxMerges = 12
        )
        val model = KellvynAttentionLanguageModel.createForTokenizer(
            tokenizer,
            contextLength = 64,
            embeddingSize = 8,
            seed = 772L
        )
        model.trainCompletion(
            tokenizer.encode(promptText),
            tokenizer.encode(completionText),
            epochs = 25,
            weightDecay = 0.0f
        )
        val bytes = ByteArrayOutputStream().also(model::writeCheckpoint).toByteArray()
        val restored = KellvynAttentionLanguageModel.readCheckpoint(ByteArrayInputStream(bytes))

        assertEquals(model.parameterFingerprint(), restored.parameterFingerprint())
        assertEquals(model.trainingSteps, restored.trainingSteps)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptySupervisedPrompt() {
        val tokenizer = KellvynBpeTokenizer.train(listOf("A"), maxMerges = 0)
        val model = KellvynAttentionLanguageModel.createForTokenizer(tokenizer, contextLength = 8, embeddingSize = 8)
        model.trainCompletion(
            promptTokenIds = intArrayOf(),
            completionTokenIds = tokenizer.encode("A"),
            epochs = 1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptySupervisedCompletion() {
        val tokenizer = KellvynBpeTokenizer.train(listOf("A"), maxMerges = 0)
        val model = KellvynAttentionLanguageModel.createForTokenizer(tokenizer, contextLength = 8, embeddingSize = 8)
        model.trainCompletion(
            promptTokenIds = tokenizer.encode("A"),
            completionTokenIds = intArrayOf(),
            epochs = 1
        )
    }
}
