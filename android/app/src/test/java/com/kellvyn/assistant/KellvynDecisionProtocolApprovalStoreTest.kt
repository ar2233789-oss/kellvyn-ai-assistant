package com.kellvyn.assistant

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class KellvynDecisionProtocolApprovalStoreTest {
    private lateinit var root: File
    private lateinit var file: File
    private lateinit var store: KellvynDecisionProtocolApprovalStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kn-decision-approval").toFile()
        file = File(root, "decision.approval")
        store = KellvynDecisionProtocolApprovalStore(file, AuthenticatedTestCipher())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun persistsDecisionAccuracyForTheExactModelTestSetAndSampleCount() {
        val report = report(
            model = "a".repeat(64),
            test = "b".repeat(64),
            matched = KellvynDecisionProtocolBehaviorBenchmark.Counts(8, 8, 8)
        )

        val saved = store.save(report, approvedAtMillis = 1_800_000_000_000L)
        val restored = store.load(
            modelFingerprint = report.modelFingerprint,
            testSetFingerprint = report.testSetFingerprint,
            expectedSampleCount = 24
        )

        assertTrue(file.isFile)
        assertEquals(1.0, saved.overallDecisionMatchRate, 0.0001)
        assertEquals(1.0, saved.finalDecisionMatchRate, 0.0001)
        assertEquals(1.0, saved.toolDecisionMatchRate, 0.0001)
        assertEquals(1.0, saved.planDecisionMatchRate, 0.0001)
        assertEquals(saved, restored)
    }

    @Test
    fun doesNotReuseApprovalForAnotherModelTestSetOrTestCount() {
        val report = report("c".repeat(64), "d".repeat(64))
        store.save(report, approvedAtMillis = 1_800_000_000_001L)

        assertNull(store.load("e".repeat(64), report.testSetFingerprint, 24))
        assertNull(store.load(report.modelFingerprint, "f".repeat(64), 24))
        assertNull(store.load(report.modelFingerprint, report.testSetFingerprint, 25))
    }

    @Test
    fun rejectsLowClassAccuracyEvenWhenOverallAccuracyIsHigh() {
        val imbalanced = KellvynDecisionProtocolBehaviorBenchmark.Report(
            eligibleForConversationReview = true,
            modelFingerprint = "1".repeat(64),
            testSetFingerprint = "2".repeat(64),
            sampleCount = 24,
            decisionMatchRate = 22.0 / 24.0,
            matched = KellvynDecisionProtocolBehaviorBenchmark.Counts(6, 8, 8),
            expected = KellvynDecisionProtocolBehaviorBenchmark.Counts(8, 8, 8),
            reasons = emptyList()
        )
        try {
            store.save(imbalanced, approvedAtMillis = 1_800_000_000_002L)
            fail("A classe de decisão sem acertos não pode receber aprovação.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertTrue(!file.exists())
    }

    @Test
    fun refusesAReportThatTheBehaviorBenchmarkRejected() {
        try {
            store.save(
                report("3".repeat(64), "4".repeat(64)).copy(
                    eligibleForConversationReview = false
                ),
                approvedAtMillis = 1_800_000_000_003L
            )
            fail("Um benchmark reprovado não pode ser persistido como aprovação.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertTrue(!file.exists())
    }

    @Test
    fun authenticatedApprovalRejectsModifiedCiphertext() {
        val report = report("5".repeat(64), "6".repeat(64))
        store.save(report, approvedAtMillis = 1_800_000_000_004L)

        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x20).toByte()
        file.writeBytes(bytes)

        try {
            store.load(report.modelFingerprint, report.testSetFingerprint, 24)
            fail("A alteração do texto cifrado precisa ser rejeitada.")
        } catch (_: IllegalArgumentException) {
            // Expected from the authenticated test envelope.
        }
    }

    private fun report(
        model: String,
        test: String,
        matched: KellvynDecisionProtocolBehaviorBenchmark.Counts =
            KellvynDecisionProtocolBehaviorBenchmark.Counts(7, 7, 7)
    ) = KellvynDecisionProtocolBehaviorBenchmark.Report(
        eligibleForConversationReview = true,
        modelFingerprint = model,
        testSetFingerprint = test,
        sampleCount = 24,
        decisionMatchRate =
            (matched.finalDecision + matched.callTool + matched.plan).toDouble() / 24.0,
        matched = matched,
        expected = KellvynDecisionProtocolBehaviorBenchmark.Counts(8, 8, 8),
        reasons = emptyList()
    )

    private class AuthenticatedTestCipher : KellvynTaskCheckpointCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
            val digest = digest(associatedData, plaintext)
            return ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(associatedData.size)
                    output.write(associatedData)
                    output.writeInt(plaintext.size)
                    output.write(plaintext)
                    output.writeInt(digest.size)
                    output.write(digest)
                }
            }.toByteArray()
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            val input = DataInputStream(ByteArrayInputStream(ciphertext))
            val aadLength = input.readInt()
            require(aadLength == associatedData.size)
            val storedAad = ByteArray(aadLength)
            input.readFully(storedAad)
            require(storedAad.contentEquals(associatedData))
            val payloadLength = input.readInt()
            require(payloadLength in 1..4_096)
            val payload = ByteArray(payloadLength)
            input.readFully(payload)
            val digestLength = input.readInt()
            require(digestLength == 32)
            val storedDigest = ByteArray(digestLength)
            input.readFully(storedDigest)
            require(input.available() == 0)
            require(storedDigest.contentEquals(digest(associatedData, payload)))
            return payload
        }

        private fun digest(aad: ByteArray, plaintext: ByteArray): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                output.writeInt(aad.size)
                output.write(aad)
                output.write(plaintext)
            }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
        }
    }
}
