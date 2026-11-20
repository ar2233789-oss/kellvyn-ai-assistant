package com.kellvyn.assistant.neural

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynMultiBlockTransformerTest {
    private fun smallTokenizer(): KellvynBpeTokenizer = KellvynBpeTokenizer.train(
        listOf(
            "Kellvyn aprende com atenção causal.",
            "Pergunta: responda B. Resposta:",
            "B",
            "Oi",
            "!"
        ),
        maxMerges = 8
    )

    private fun smallConfig(tokenizer: KellvynBpeTokenizer, depth: Int = 2) =
        KellvynMultiBlockTransformer.Config(
            vocabularySize = tokenizer.vocabularySize,
            contextLength = 32,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 12,
            depth = depth,
            seed = 7401L
        )

    @Test
    fun initializationIsDeterministicAndDepthChangesTheParameterFingerprint() {
        val tokenizer = smallTokenizer()
        val config = smallConfig(tokenizer, depth = 2)
        val first = KellvynMultiBlockTransformer.create(config)
        val repeated = KellvynMultiBlockTransformer.create(config)
        val shallow = KellvynMultiBlockTransformer.create(smallConfig(tokenizer, depth = 1))

        assertEquals(first.parameterFingerprint(), repeated.parameterFingerprint())
        assertNotEquals(first.parameterFingerprint(), shallow.parameterFingerprint())
    }

    @Test
    fun twoBlockBackpropagationMatchesFiniteDifferencesForEveryLayerAndGroup() {
        val tokenizer = smallTokenizer()
        val model = KellvynMultiBlockTransformer.create(smallConfig(tokenizer, depth = 2))
        val context = tokenizer.encode("Oi")
        val target = tokenizer.encode("!").single()

        val report = model.checkGradients(
            contextTokenIds = context,
            targetTokenId = target,
            epsilon = 0.005,
            maxCoordinatesPerGroup = 2
        )

        assertEquals(25, report.checkedParametersByGroup.size)
        assertTrue(report.checkedParametersByGroup.values.all { it in 1..2 })
        assertEquals(report.checkedParametersByGroup.values.sum(), report.checkedParameters)
        assertTrue(
            "KN-004 absolute gradient error was ${report.maxAbsoluteError}",
            report.maxAbsoluteError < 0.006
        )
        assertTrue(
            "KN-004 relative gradient error was ${report.maxRelativeError}",
            report.maxRelativeError < 0.08
        )
        assertTrue(report.checkedParametersByGroup.keys.any { it.startsWith("block.0.") })
        assertTrue(report.checkedParametersByGroup.keys.any { it.startsWith("block.1.") })
    }

    @Test
    fun twoBlockTrainingReducesCompletionLossAndUpdatesBothBlocks() {
        val tokenizer = smallTokenizer()
        val model = KellvynMultiBlockTransformer.create(smallConfig(tokenizer, depth = 2))
        val prompt = tokenizer.encode("Pergunta: responda B. Resposta:")
        val completion = tokenizer.encode("B")
        val before = model.parameterFingerprint()
        val initialLoss = model.evaluateCompletionLoss(prompt, completion)

        val report = model.trainCompletion(
            promptTokenIds = prompt,
            completionTokenIds = completion,
            epochs = 160,
            learningRate = 0.01f,
            weightDecay = 0.0f
        )

        assertEquals(completion.size, report.examples)
        assertEquals(completion.size.toLong() * 160L, report.totalSteps)
        assertTrue(
            "Loss did not decrease: ${report.initialLoss} -> ${report.finalLoss}",
            report.finalLoss < initialLoss
        )
        assertNotEquals(before, model.parameterFingerprint())
    }

    @Test
    fun capacityEstimatorUsesActualMultiBlockDepthAndKvCaches() {
        val tokenizer = smallTokenizer()
        val config = smallConfig(tokenizer, depth = 2)
        val report = KellvynTransformerCapacityEstimator.estimate(config)

        val shared = 2L * config.vocabularySize * config.embeddingSize +
            config.vocabularySize
        val perBlock = 4L * config.embeddingSize * config.embeddingSize +
            4L * config.embeddingSize +
            3L * config.embeddingSize * config.feedForwardSize

        assertEquals(config.depth, report.depth)
        assertEquals(shared + perBlock * config.depth, report.parameterCount)
        assertEquals(report.parameterCount * 4L, report.weightsBytes)
        assertEquals(report.parameterCount * 8L, report.optimizerStateBytes)
        assertEquals(
            2L * config.depth * config.contextLength * config.embeddingSize * 4L,
            report.generationKvCacheBytes
        )
    }

    @Test
    fun checkpointRoundTripPreservesDepthWeightsOptimizerAndPredictions() {
        val tokenizer = smallTokenizer()
        val config = smallConfig(tokenizer, depth = 2)
        val model = KellvynMultiBlockTransformer.create(config)
        val prompt = tokenizer.encode("Pergunta: responda B. Resposta:")
        val completion = tokenizer.encode("B")
        model.trainCompletion(prompt, completion, epochs = 3, weightDecay = 0.0f)

        val checkpoint = ByteArrayOutputStream().also(model::writeCheckpoint).toByteArray()
        val restored = KellvynMultiBlockTransformer.readCheckpoint(ByteArrayInputStream(checkpoint))

        assertEquals(config, restored.config)
        assertEquals(model.trainingSteps, restored.trainingSteps)
        assertEquals(model.parameterFingerprint(), restored.parameterFingerprint())
        assertArrayEquals(
            model.evaluateNextTokenProbabilities(prompt),
            restored.evaluateNextTokenProbabilities(prompt),
            0.0f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkpointRejectsCorruptedPayload() {
        val tokenizer = smallTokenizer()
        val model = KellvynMultiBlockTransformer.create(smallConfig(tokenizer))
        val bytes = ByteArrayOutputStream().also(model::writeCheckpoint).toByteArray()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x20).toByte()
        KellvynMultiBlockTransformer.readCheckpoint(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun configurationRejectsZeroDepth() {
        KellvynMultiBlockTransformer.Config(depth = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun configurationRejectsAttentionAllocationAboveMemoryBudget() {
        KellvynMultiBlockTransformer.Config(
            contextLength = 2_048,
            embeddingSize = 32,
            headCount = 4,
            feedForwardSize = 64,
            depth = 8
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun configurationRejectsParameterAndOptimizerStateAboveMemoryBudget() {
        KellvynMultiBlockTransformer.Config(
            vocabularySize = 8_192,
            contextLength = 128,
            embeddingSize = 256,
            headCount = 8,
            feedForwardSize = 1_024,
            depth = 8
        )
    }
    @Test
    fun perLayerKvCacheMatchesFullForwardAcrossContextWindowEviction() {
        val tokenizer = smallTokenizer()
        val model = KellvynMultiBlockTransformer.create(smallConfig(tokenizer, depth = 2))
        val prefix = tokenizer.encode("Oi")
        val session = model.newGenerationSession(prefix)

        repeat(48) { step ->
            val cached = session.nextTokenProbabilities()
            val reference = model.evaluateNextTokenProbabilities(session.contextTokenIds())
            assertEquals(reference.size, cached.size)
            for (token in reference.indices) {
                assertTrue(
                    "KV mismatch at step $step token $token: " +
                        "full=${reference[token]} cached=${cached[token]}",
                    kotlin.math.abs(reference[token] - cached[token]) < 0.00002f
                )
            }

            val next = cached.indices.maxBy { cached[it] }
            session.appendToken(next)
            for (layer in 0 until model.config.depth) {
                assertEquals(
                    session.contextTokenIds().size,
                    session.cachedTokenCount(layer)
                )
                assertTrue(session.cachedTokenCount(layer) <= model.config.contextLength)
            }
        }

        assertEquals(prefix.size + 48, session.processedTokenCount())
        assertEquals(model.config.contextLength, session.contextTokenIds().size)
        assertEquals(model.config.contextLength, session.cachedTokenCount(0))
        assertEquals(model.config.contextLength, session.cachedTokenCount(1))
    }

    @Test
    fun cachedGreedyGenerationMatchesFullPrefixReference() {
        val tokenizer = smallTokenizer()
        val model = KellvynMultiBlockTransformer.create(smallConfig(tokenizer, depth = 2))
        val prefix = tokenizer.encode("Oi")
        val expected = prefix.toMutableList()

        repeat(24) {
            val context = expected.takeLast(model.config.contextLength).toIntArray()
            expected += model.predictNext(context)
        }

        val actual = model.generate(prefix, maxNewTokens = 24)
        assertArrayEquals(expected.toIntArray(), actual)
    }


}
