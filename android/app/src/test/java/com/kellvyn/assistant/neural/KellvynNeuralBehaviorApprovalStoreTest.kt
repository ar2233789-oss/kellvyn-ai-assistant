package com.kellvyn.assistant.neural

import com.kellvyn.assistant.KellvynTaskCheckpointCipher
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

class KellvynNeuralBehaviorApprovalStoreTest {
    private lateinit var root: File
    private lateinit var file: File
    private lateinit var store: KellvynNeuralBehaviorApprovalStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kn-behavior-approval").toFile()
        file = File(root, "behavior.approval")
        store = KellvynNeuralBehaviorApprovalStore(file, AssociatedDataTestCipher())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun persistsOnlyTheExactModelAndTestManifestApproval() {
        val modelFingerprint = "a".repeat(64)
        val testFingerprint = "b".repeat(64)
        val report = report(modelFingerprint)

        store.save(modelFingerprint, testFingerprint, report)

        val restored = store.load(modelFingerprint, testFingerprint)
        assertTrue(file.isFile)
        assertEquals(true, restored!!.eligibleForIntegrationReview)
        assertEquals(modelFingerprint, restored.modelFingerprint)
        assertEquals(20, restored.sampleCount)
        assertEquals(0.75, restored.exactMatchRate, 0.0)
        assertEquals(0.85, restored.meanTokenF1, 0.0)
        assertTrue(restored.reasons.isEmpty())
    }

    @Test
    fun doesNotReuseApprovalForAnotherModelOrTestManifest() {
        val modelFingerprint = "c".repeat(64)
        val testFingerprint = "d".repeat(64)
        store.save(modelFingerprint, testFingerprint, report(modelFingerprint))

        assertNull(store.load("e".repeat(64), testFingerprint))
        assertNull(store.load(modelFingerprint, "f".repeat(64)))
    }

    @Test
    fun rejectsUnapprovedBehaviorReports() {
        try {
            store.save(
                "1".repeat(64),
                "2".repeat(64),
                report("1".repeat(64)).copy(eligibleForIntegrationReview = false)
            )
            fail("Uma avaliação reprovada não pode ser persistida como aprovação.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertTrue(!file.exists())
    }

    @Test
    fun authenticatedRecordRejectsModifiedCiphertext() {
        val modelFingerprint = "3".repeat(64)
        val testFingerprint = "4".repeat(64)
        store.save(modelFingerprint, testFingerprint, report(modelFingerprint))

        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x40).toByte()
        file.writeBytes(bytes)

        try {
            store.load(modelFingerprint, testFingerprint)
            fail("A alteração do conteúdo criptografado precisa ser rejeitada.")
        } catch (_: IllegalArgumentException) {
            // Expected from the test cipher's associated-data envelope.
        }
    }

    private fun report(modelFingerprint: String) =
        KellvynNeuralBehaviorBenchmark.Report(
            eligibleForIntegrationReview = true,
            modelFingerprint = modelFingerprint,
            sampleCount = 20,
            exactMatchRate = 0.75,
            meanTokenF1 = 0.85,
            reasons = emptyList()
        )

    /**
     * Test double that authenticates the associated-data bytes. Production uses Android Keystore
     * AES-256-GCM; this unit test verifies store binding and file handling without Android APIs.
     */
    private class AssociatedDataTestCipher : KellvynTaskCheckpointCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
            ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(associatedData.size)
                    output.write(associatedData)
                    output.write(plaintext)
                }
            }.toByteArray()

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            val input = DataInputStream(ByteArrayInputStream(ciphertext))
            val associatedLength = input.readInt()
            require(associatedLength == associatedData.size)
            val storedAssociatedData = ByteArray(associatedLength)
            input.readFully(storedAssociatedData)
            require(storedAssociatedData.contentEquals(associatedData))
            val plaintext = ByteArray(input.available())
            input.readFully(plaintext)
            return plaintext
        }
    }
}
