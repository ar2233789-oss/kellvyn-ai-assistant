package com.kellvyn.assistant.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KellvynByteBrainTest {
    @Test
    fun byteTokenizerRoundTripsPortugueseAndUnicode() {
        val original = "Kellvyn: ação, coração, ç, ã, 🔧 e código."
        val tokens = KellvynByteTokenizer.encode(original)
        assertTrue(tokens.all { it in 0..255 })
        assertEquals(original, KellvynByteTokenizer.decode(tokens))
    }

    @Test
    fun trainingUpdatesItsOwnRandomParametersAndReducesTestLoss() {
        val brain = KellvynByteBrain.create(
            KellvynByteBrain.Config(
                contextLength = 4,
                embeddingSize = 8,
                hiddenSize = 24,
                seed = 77L
            )
        )
        val before = brain.parameterFingerprint()
        val report = brain.trainText(
            text = "abababababababababababababababababababab",
            epochs = 18,
            learningRate = 0.035f
        )
        val after = brain.parameterFingerprint()

        assertNotEquals(before, after)
        assertEquals(40 * 18L, report.totalSteps)
        assertTrue(report.initialLoss.isFinite())
        assertTrue(report.finalLoss.isFinite())
        assertTrue(
            "A loss final " + report.finalLoss + " deveria ficar abaixo da inicial " + report.initialLoss,
            report.finalLoss < report.initialLoss
        )
    }

    @Test
    fun analyticGradientsMatchFiniteDifferencesAcrossParameterGroups() {
        val brain = KellvynByteBrain.create(
            KellvynByteBrain.Config(
                contextLength = 4,
                embeddingSize = 6,
                hiddenSize = 12,
                seed = 2026L
            )
        )
        val context = KellvynByteTokenizer.encode("a")
        val target = 'b'.code

        val report = brain.checkGradients(
            contextTokens = context,
            target = target,
            epsilon = 0.001,
            maxCoordinatesPerGroup = 4
        )

        assertTrue("A checagem precisa cobrir vários parâmetros.", report.checkedParameters >= 5)
        assertTrue(
            "Gradiente analítico divergiu da diferença finita: " + report.maxAbsoluteError,
            report.maxAbsoluteError < 0.001
        )
    }

    @Test
    fun ownCheckpointRestoresTheExactParametersAndTrainingCount() {
        val original = KellvynByteBrain.create(
            KellvynByteBrain.Config(
                contextLength = 5,
                embeddingSize = 7,
                hiddenSize = 20,
                seed = 1234L
            )
        )
        original.trainText("Kellvyn aprende com teste.", epochs = 2)

        val bytes = ByteArrayOutputStream()
        original.writeCheckpoint(bytes)
        val restored = KellvynByteBrain.readCheckpoint(ByteArrayInputStream(bytes.toByteArray()))

        assertEquals(original.parameterFingerprint(), restored.parameterFingerprint())
        assertEquals(original.trainingSteps, restored.trainingSteps)
        assertEquals(
            original.predictNext(intArrayOf(75, 101, 108, 108, 118)),
            restored.predictNext(intArrayOf(75, 101, 108, 108, 118))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkpointRejectsUnknownWeightFormats() {
        KellvynByteBrain.readCheckpoint(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tokenizerRejectsNonByteTokenIds() {
        KellvynByteTokenizer.decode(intArrayOf(256))
    }
}
