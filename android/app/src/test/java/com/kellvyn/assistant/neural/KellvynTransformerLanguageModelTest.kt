package com.kellvyn.assistant.neural

import org.junit.Assert.assertArrayEquals
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
            // A slightly wider central-difference step reduces float32 cancellation
            // after the vocabulary gained its dedicated EOS class.
            epsilon = 0.005,
            maxCoordinatesPerGroup = 2
        )

        assertEquals(
            setOf(
                "tokenEmbeddings", "queryWeights", "keyWeights", "valueWeights",
                "attentionOutputWeights", "normAttentionGamma", "normAttentionBeta",
                "normFeedForwardGamma", "normFeedForwardBeta", "gateWeights",
                "upWeights", "downWeights", "outputWeights", "outputBias"
            ),
            report.checkedParametersByGroup.keys
        )
        assertTrue(report.checkedParametersByGroup.values.all { it >= 1 })
        assertEquals(report.checkedParametersByGroup.values.sum(), report.checkedParameters)
        assertTrue("absolute gradient error was ${report.maxAbsoluteError}", report.maxAbsoluteError < 0.004)
        assertTrue("relative gradient error was ${report.maxRelativeError}", report.maxRelativeError < 0.06)
    }

    @Test
    fun gradientCheckerCoversEveryParameterGroupAcrossHeadCountsAndSeeds() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn verifica gradientes em português."),
            maxMerges = 0
        )
        val context = tokenizer.encode("Oi")
        val target = tokenizer.encode("!").single()
        for (headCount in listOf(1, 2, 4)) {
            val model = KellvynTransformerLanguageModel.createForTokenizer(
                tokenizer = tokenizer,
                contextLength = 8,
                embeddingSize = 8,
                headCount = headCount,
                feedForwardSize = 12,
                seed = 5200L + headCount
            )
            val report = model.checkGradients(
                contextTokenIds = context,
                targetTokenId = target,
                epsilon = 0.005,
                maxCoordinatesPerGroup = 3
            )
            assertEquals(14, report.checkedParametersByGroup.size)
            assertTrue(report.checkedParametersByGroup.values.all { it in 1..3 })
            assertTrue("absolute gradient error was ${report.maxAbsoluteError}", report.maxAbsoluteError < 0.004)
            assertTrue("relative gradient error was ${report.maxRelativeError}", report.maxRelativeError < 0.06)
        }
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
    fun rawTokenEpochPrimitiveMatchesSingleEpochTrainingExactly() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn aprende linguagem contínua."),
            maxMerges = 8
        )
        val tokens = tokenizer.encode("A previsão do próximo token é a tarefa básica.")
        val config = KellvynTransformerLanguageModel.Config(
            vocabularySize = tokenizer.vocabularySize,
            contextLength = 16,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 8_422L
        )
        val measuredModel = KellvynTransformerLanguageModel.create(config)
        val directModel = KellvynTransformerLanguageModel.create(config)

        measuredModel.trainTokens(
            tokenIds = tokens,
            epochs = 1,
            learningRate = 0.003f,
            weightDecay = 0.0f
        )
        directModel.trainTokensEpoch(
            tokenIds = tokens,
            learningRate = 0.003f,
            weightDecay = 0.0f
        )

        assertEquals(measuredModel.trainingSteps, directModel.trainingSteps)
        assertEquals(measuredModel.parameterFingerprint(), directModel.parameterFingerprint())
    }

    @Test
    fun directEpochPrimitiveMatchesSingleEpochTrainingExactly() {
        val promptText = "Pergunta: responda sim. Resposta:"
        val tokenizer = KellvynBpeTokenizer.train(
            listOf(promptText, "sim"),
            maxMerges = 8
        )
        val prompt = tokenizer.encode(promptText)
        val completion = tokenizer.encode("sim")
        val config = KellvynTransformerLanguageModel.Config(
            vocabularySize = tokenizer.vocabularySize,
            contextLength = 32,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 8_421L
        )
        val measuredModel = KellvynTransformerLanguageModel.create(config)
        val directModel = KellvynTransformerLanguageModel.create(config)

        measuredModel.trainCompletion(
            promptTokenIds = prompt,
            completionTokenIds = completion,
            epochs = 1,
            learningRate = 0.003f,
            weightDecay = 0.0f
        )
        directModel.trainCompletionEpoch(
            promptTokenIds = prompt,
            completionTokenIds = completion,
            learningRate = 0.003f,
            weightDecay = 0.0f
        )

        assertEquals(measuredModel.trainingSteps, directModel.trainingSteps)
        assertEquals(measuredModel.parameterFingerprint(), directModel.parameterFingerprint())
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


    @Test
    fun modelCanLearnAndStopAtDedicatedEndOfSequenceToken() {
        val promptText = "Kellvyn resposta:"
        val tokenizer = KellvynBpeTokenizer.train(listOf(promptText), maxMerges = 0)
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 32,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 4104L
        )
        val prompt = tokenizer.encode(promptText)
        val endTokenId = requireNotNull(tokenizer.endOfSequenceTokenId)

        model.trainCompletion(
            promptTokenIds = prompt,
            completionTokenIds = intArrayOf(endTokenId),
            epochs = 400,
            learningRate = 0.01f,
            weightDecay = 0.0f
        )

        assertEquals(endTokenId, model.predictNext(prompt))
        assertArrayEquals(
            "A sequência retornada deve excluir o token de controle EOS.",
            prompt,
            model.generate(prompt, maxNewTokens = 8, endTokenId = endTokenId)
        )
    }


    @Test
    fun incrementalKvCacheMatchesFullPrefixAcrossContextEviction() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn usa cache incremental e preserva a atenção causal."),
            maxMerges = 24
        )
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 8,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 4105L
        )
        // At most seven UTF-8 byte tokens, leaving room for the 8-token test window.
        val prefix = tokenizer.encode("Kellvyn")
        val session = model.newGenerationSession(prefix)
        val generated = mutableListOf<Int>()

        repeat(20) {
            val cached = session.nextTokenProbabilities()
            val reference = model.evaluateNextTokenProbabilities(session.contextTokenIds())
            assertEquals(reference.size, cached.size)
            for (index in reference.indices) {
                assertTrue(
                    "KV cache divergence at iteration $it token $index: " +
                        "reference=${reference[index]} cached=${cached[index]}",
                    kotlin.math.abs(reference[index] - cached[index]) < 0.00002f
                )
            }

            val next = cached.indices.maxBy { cached[it] }
            session.appendToken(next)
            generated += next
            assertTrue(session.cachedTokenCount() <= model.config.contextLength)
            assertEquals(prefix.size + generated.size, session.outputTokenIds().size)
            assertEquals(
                session.outputTokenIds().takeLast(model.config.contextLength),
                session.contextTokenIds().toList()
            )
        }

        assertEquals(prefix.size + 20, session.processedTokenCount())
        assertEquals(model.config.contextLength, session.cachedTokenCount())
    }

    @Test
    fun incrementalGenerationAndFullPrefixGreedyGenerationProduceSameTokens() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn responde com consistência."),
            maxMerges = 12
        )
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 24,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 4106L
        )
        val prompt = tokenizer.encode("Oi")
        val cachedSession = model.newGenerationSession(prompt)
        val cachedTokens = mutableListOf<Int>()
        repeat(12) {
            val probabilityReference = cachedSession.nextTokenProbabilities()
            val probabilityArgmax = probabilityReference.indices.maxBy { probabilityReference[it] }
            val token = cachedSession.predictNext()
            assertEquals(
                "Greedy logits must select the same token as softmax probabilities.",
                probabilityArgmax,
                token
            )
            cachedTokens += token
            cachedSession.appendToken(token)
        }

        val referenceOutput = prompt.toMutableList()
        repeat(12) {
            val context = referenceOutput.takeLast(model.config.contextLength).toIntArray()
            referenceOutput += model.predictNext(context)
        }

        assertArrayEquals(referenceOutput.toIntArray(), cachedSession.outputTokenIds())
        assertEquals(12, cachedTokens.size)
    }

    @Test
    fun restoredCheckpointRetainsIncrementalKvCacheEquivalenceAfterWindowSlides() {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("Kellvyn mantém o cache incremental consistente após restaurar pesos."),
            maxMerges = 12
        )
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 8,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            seed = 4107L
        )
        val prompt = tokenizer.encode("Pergunta:")
        val completion = tokenizer.encode("sim")
        model.trainCompletion(
            promptTokenIds = prompt,
            completionTokenIds = completion,
            epochs = 2,
            weightDecay = 0.0f
        )
        val checkpoint = ByteArrayOutputStream().also(model::writeCheckpoint).toByteArray()
        val restored = KellvynTransformerLanguageModel.readCheckpoint(
            ByteArrayInputStream(checkpoint)
        )

        assertEquals(model.trainingSteps, restored.trainingSteps)
        assertEquals(model.parameterFingerprint(), restored.parameterFingerprint())

        val prefix = tokenizer.encode("Kellvyn")
        assertTrue(prefix.isNotEmpty())
        assertTrue(prefix.size <= model.config.contextLength)
        val originalSession = model.newGenerationSession(prefix)
        val restoredSession = restored.newGenerationSession(prefix)

        repeat(16) { step ->
            val originalProbabilities = originalSession.nextTokenProbabilities()
            val restoredProbabilities = restoredSession.nextTokenProbabilities()
            val fullPrefixReference = restored.evaluateNextTokenProbabilities(
                restoredSession.contextTokenIds()
            )
            assertEquals(originalProbabilities.size, restoredProbabilities.size)
            assertEquals(restoredProbabilities.size, fullPrefixReference.size)
            for (token in fullPrefixReference.indices) {
                assertTrue(
                    "Restored cache diverged from KN-003 at step $step, token $token",
                    kotlin.math.abs(
                        restoredProbabilities[token] - fullPrefixReference[token]
                    ) < 0.00002f
                )
                assertTrue(
                    "Checkpoint changed cached probabilities at step $step, token $token",
                    kotlin.math.abs(
                        originalProbabilities[token] - restoredProbabilities[token]
                    ) < 0.00002f
                )
            }

            val next = originalProbabilities.indices.maxBy { originalProbabilities[it] }
            originalSession.appendToken(next)
            restoredSession.appendToken(next)
            assertEquals(
                originalSession.outputTokenIds().toList(),
                restoredSession.outputTokenIds().toList()
            )
        }

        assertEquals(prefix.size + 16, restoredSession.processedTokenCount())
        assertEquals(model.config.contextLength, restoredSession.cachedTokenCount())
        assertEquals(
            restoredSession.outputTokenIds().takeLast(model.config.contextLength),
            restoredSession.contextTokenIds().toList()
        )
    }

    @Test
    fun expandedContextCheckpointRoundTripsWithoutChangingExistingDefaults() {
        assertEquals(256, KellvynTransformerLanguageModel.Config().contextLength)

        val tokenizer = KellvynBpeTokenizer.train(listOf("Contexto ampliado Kellvyn."), maxMerges = 0)
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 1_024,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 9_102L
        )
        val bytes = ByteArrayOutputStream().also(model::writeCheckpoint).toByteArray()
        val restored = KellvynTransformerLanguageModel.readCheckpoint(ByteArrayInputStream(bytes))

        assertEquals(1_024, restored.config.contextLength)
        assertEquals(model.config.vocabularySize, restored.config.vocabularySize)
        assertEquals(model.parameterFingerprint(), restored.parameterFingerprint())
        assertEquals(
            model.newGenerationSession(tokenizer.encode("Teste")).contextTokenIds().toList(),
            restored.newGenerationSession(tokenizer.encode("Teste")).contextTokenIds().toList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsContextAboveTheDeviceSafeMaximum() {
        KellvynTransformerLanguageModel.Config(
            vocabularySize = 256,
            contextLength = 2_049,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16
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
