package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KellvynAgentLabTraceStoreTest {
    private lateinit var root: File
    private lateinit var store: KellvynAgentLabTraceStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kellvyn-agent-lab-traces").toFile()
        store = KellvynAgentLabTraceStore(root, TraceTestCipher())
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun persistsCoordinatorAndSpecialistSpansInOneTrace() = runBlocking {
        val clock = AtomicLong(100L)
        val recorder = KellvynAgentLabTraceStoreRecorder(store)
        val trace = KellvynAgentLabTraceSession.create(recorder) { clock.getAndIncrement() }
        val specialist = trace.forActor("specialist:researcher")

        trace.record(KellvynAgentLabEventKind.RUN_STARTED, outcome = KellvynAgentLabOutcome.STARTED)
        trace.record(
            KellvynAgentLabEventKind.DELEGATION_STARTED,
            specialistRole = KellvynSpecialistRole.RESEARCHER,
            outcome = KellvynAgentLabOutcome.STARTED
        )
        specialist.record(
            KellvynAgentLabEventKind.AGENT_STARTED,
            specialistRole = KellvynSpecialistRole.RESEARCHER,
            outcome = KellvynAgentLabOutcome.STARTED
        )
        specialist.record(
            KellvynAgentLabEventKind.AGENT_FINISHED,
            specialistRole = KellvynSpecialistRole.RESEARCHER,
            outcome = KellvynAgentLabOutcome.SUCCEEDED
        )
        trace.record(
            KellvynAgentLabEventKind.DELEGATION_FINISHED,
            specialistRole = KellvynSpecialistRole.RESEARCHER,
            outcome = KellvynAgentLabOutcome.SUCCEEDED
        )
        trace.record(KellvynAgentLabEventKind.RUN_COMPLETED, outcome = KellvynAgentLabOutcome.SUCCEEDED)

        val history = store.history(trace.traceId)
        assertEquals(6, history.size)
        assertEquals("coordinator", history.first().event.actor)
        assertEquals("specialist:researcher", history[2].event.actor)
        assertEquals(KellvynAgentLabEventKind.AGENT_FINISHED, history[3].event.kind)
        assertEquals(KellvynAgentLabEventKind.RUN_COMPLETED, history.last().event.kind)
        assertEquals(KellvynAgentLabEventKind.RUN_COMPLETED, store.summaries().single().terminalKind)
    }

    @Test
    fun supportsTracesLargerThanLegacy128EventLimitAndReadsBoundedTail() {
        val traceId = "00000000-0000-0000-0000-000000000003"
        repeat(140) { index ->
            store.append(
                KellvynAgentLabEvent(
                    traceId = traceId,
                    occurredAtMillis = 1_000L + index,
                    kind = KellvynAgentLabEventKind.MODEL_CALL_STARTED,
                    step = index + 1,
                    outcome = KellvynAgentLabOutcome.STARTED
                )
            )
        }

        val tail = store.historyTail(traceId, 12)
        assertEquals(12, tail.size)
        assertEquals(129L, tail.first().sequence)
        assertEquals(140L, tail.last().sequence)
        assertEquals(140, store.summaries().single().eventCount)
        assertEquals(1_000L, store.summaries().single().startedAtMillis)
        assertEquals(1_139L, store.summaries().single().lastEventAtMillis)

        // A fresh store instance must rebuild its append cursor from the verified disk chain.
        val reopened = KellvynAgentLabTraceStore(root, TraceTestCipher())
        val finalRecord = reopened.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = 1_140L,
                kind = KellvynAgentLabEventKind.RUN_COMPLETED,
                outcome = KellvynAgentLabOutcome.SUCCEEDED
            )
        )
        assertEquals(141L, finalRecord.sequence)
        assertEquals(141, reopened.historyTail(traceId, 200).size)
        assertEquals(KellvynAgentLabEventKind.RUN_COMPLETED, reopened.summaries().single().terminalKind)
    }

    @Test
    fun separateStoreInstancesReconcileStaleAppendCursorsWithoutOverwriting() {
        val traceId = "00000000-0000-0000-0000-000000000004"
        val secondStore = KellvynAgentLabTraceStore(root, TraceTestCipher())

        val first = store.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = 100L,
                kind = KellvynAgentLabEventKind.RUN_STARTED
            )
        )
        val second = secondStore.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = 110L,
                kind = KellvynAgentLabEventKind.MODEL_CALL_STARTED,
                step = 1
            )
        )
        val third = store.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = 120L,
                kind = KellvynAgentLabEventKind.RUN_COMPLETED
            )
        )

        assertEquals(1L, first.sequence)
        assertEquals(2L, second.sequence)
        assertEquals(3L, third.sequence)
        assertEquals(3L, store.history(traceId).last().sequence)
        assertEquals(3, store.historyTail(traceId, 10).size)
    }

    @Test
    fun cancelledRunIsTerminalAndEligibleForExplicitRetentionPruning() {
        val traceId = "00000000-0000-0000-0000-000000000001"
        store.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = 100L,
                kind = KellvynAgentLabEventKind.RUN_STARTED,
                outcome = KellvynAgentLabOutcome.STARTED
            )
        )
        store.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = 110L,
                kind = KellvynAgentLabEventKind.RUN_CANCELLED,
                outcome = KellvynAgentLabOutcome.FAILED,
                detailCode = "CANCELLED"
            )
        )

        assertEquals(KellvynAgentLabEventKind.RUN_CANCELLED, store.summaries().single().terminalKind)
        assertEquals(1, store.pruneCompletedBefore(200L))
        assertTrue(store.summaries().isEmpty())
    }

    @Test
    fun recorderReportsTransientStorageFailureAndRecoversOnNextWrite() = runBlocking {
        val blockedRoot = File(root, "not-a-directory").apply { writeText("file blocks directory creation") }
        val failingStore = KellvynAgentLabTraceStore(blockedRoot, TraceTestCipher())
        val recorder = KellvynAgentLabTraceStoreRecorder(failingStore)
        assertFalse(recorder.health().hasSuccessfulWrite)
        assertTrue(recorder.health().currentlyWritable)
        assertFalse(recorder.health().healthy)
        val event = KellvynAgentLabEvent(
            traceId = "00000000-0000-0000-0000-000000000005",
            occurredAtMillis = 100L,
            kind = KellvynAgentLabEventKind.RUN_STARTED,
            outcome = KellvynAgentLabOutcome.STARTED
        )

        // Trace persistence is best effort and must not throw into the agent's control loop.
        recorder.record(event)
        assertFalse(recorder.health().healthy)
        assertEquals("IllegalStateException", recorder.health().lastFailureCode)
        assertEquals(event.traceId, recorder.health().lastFailureTraceId)
        assertEquals(KellvynAgentLabEventKind.RUN_STARTED, recorder.health().lastFailureEventKind)

        assertTrue(blockedRoot.delete())
        recorder.record(event)

        assertTrue(recorder.health().currentlyWritable)
        assertFalse(recorder.health().healthy)
        assertEquals(1, recorder.health().failureCount)
        assertEquals("IllegalStateException", recorder.health().lastFailureCode)
        assertEquals(event.traceId, recorder.health().lastFailureTraceId)
        assertEquals(KellvynAgentLabEventKind.RUN_STARTED, recorder.health().lastFailureEventKind)
        assertEquals(1, failingStore.history(event.traceId).size)
    }

    @Test
    fun modifiedTraceRecordIsRejectedBeforeHistoryIsReturned() {
        val traceId = "00000000-0000-0000-0000-000000000002"
        store.append(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = 100L,
                kind = KellvynAgentLabEventKind.RUN_STARTED,
                outcome = KellvynAgentLabOutcome.STARTED
            )
        )
        val eventFile = File(File(root, traceId), "event-00000001.kvt")
        val bytes = eventFile.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        eventFile.writeBytes(bytes)

        val fullHistoryFailure = runCatching { store.history(traceId) }.exceptionOrNull()
        val tailHistoryFailure = runCatching { store.historyTail(traceId, 1) }.exceptionOrNull()
        val summaryFailure = runCatching { store.summaries() }.exceptionOrNull()
        assertTrue("O histórico completo precisa rejeitar a cadeia adulterada.", fullHistoryFailure is IllegalStateException)
        assertTrue("A leitura por janela precisa validar também registros anteriores.", tailHistoryFailure is IllegalStateException)
        assertTrue("O resumo precisa rejeitar a cadeia adulterada.", summaryFailure is IllegalStateException)
    }

    private class TraceTestCipher : KellvynTaskCheckpointCipher {
        private val key = SecretKeySpec(ByteArray(32) { index -> (index * 11).toByte() }, "AES")

        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(associatedData)
            return iv + cipher.doFinal(plaintext)
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            require(ciphertext.size > 12)
            val iv = ciphertext.copyOfRange(0, 12)
            val payload = ciphertext.copyOfRange(12, ciphertext.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(associatedData)
            return cipher.doFinal(payload)
        }
    }
}
