package com.kellvyn.assistant

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KellvynPersistentTaskQueueInstrumentedTest {
    @Test
    fun payloadAndResultAreEncryptedAndRoundTripThroughKeystore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val sensitivePayload = """{"private_note":"do-not-store-in-plaintext","target":"project-1"}"""
            val id = queue.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = "test.handler",
                    payload = sensitivePayload,
                    priority = 70,
                    workloadClass = KellvynWorkloadClass.STANDARD,
                    estimatedWorkingSetBytes = 8L * MEBIBYTE
                ),
                nowMillis = 10_000L
            )

            val encryptedPayload = queue.readableDatabase.rawQuery(
                "SELECT payload FROM kellvyn_tasks WHERE id = ?",
                arrayOf(id)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getBlob(0)
            }
            assertFalse(encryptedPayload.toString(Charsets.ISO_8859_1).contains("do-not-store-in-plaintext"))
            assertEquals(sensitivePayload, queue.get(id)!!.payload)

            val claimed = queue.claimNext(10_001L)!!
            assertEquals(id, claimed.id)
            queue.complete(id, "private result summary", 10_002L)

            val encryptedResult = queue.readableDatabase.rawQuery(
                "SELECT result_summary FROM kellvyn_tasks WHERE id = ?",
                arrayOf(id)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getBlob(0)
            }
            assertFalse(encryptedResult.toString(Charsets.ISO_8859_1).contains("private result summary"))
            val restored = queue.get(id)!!
            assertEquals(KellvynQueuedTaskState.SUCCEEDED, restored.state)
            assertEquals("private result summary", restored.resultSummary)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun queueClaimsHigherPriorityFirstAndFifoWithinSamePriority() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val low = queue.enqueue(
                KellvynQueuedTaskSpec("test.handler", "low", priority = 10),
                1_000L
            )
            val highFirst = queue.enqueue(
                KellvynQueuedTaskSpec("test.handler", "high-a", priority = 90),
                1_001L
            )
            val highSecond = queue.enqueue(
                KellvynQueuedTaskSpec("test.handler", "high-b", priority = 90),
                1_002L
            )

            assertEquals(highFirst, queue.claimNext(1_003L)!!.id)
            queue.complete(highFirst, "done-a", 1_004L)
            assertEquals(highSecond, queue.claimNext(1_005L)!!.id)
            queue.complete(highSecond, "done-b", 1_006L)
            assertEquals(low, queue.claimNext(1_007L)!!.id)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun resourceDeferralAndRetryRespectNotBeforeAndAttemptLimits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val deferredId = queue.enqueue(
                KellvynQueuedTaskSpec("test.handler", "deferred", maxAttempts = 3),
                1_000L
            )
            queue.claimNext(1_001L)
            queue.defer(deferredId, notBeforeMillis = 5_000L, reasonCode = "RESOURCE_QUEUE_BUSY", nowMillis = 1_002L)
            assertNull(queue.claimNext(4_999L))
            assertEquals(deferredId, queue.claimNext(5_000L)!!.id)
            queue.complete(deferredId, "deferred-done", 5_001L)

            val retryId = queue.enqueue(
                KellvynQueuedTaskSpec("test.handler", "retry", maxAttempts = 2),
                10_000L
            )
            val retryClaim = queue.claimNext(10_001L)!!
            assertEquals(retryId, retryClaim.id)
            assertEquals(
                KellvynQueuedTaskState.QUEUED,
                queue.failOrRetry(retryId, "EXECUTION_FAILED", 10_002L)
            )
            assertNull(queue.claimNext(15_001L))
            val finalAttempt = queue.claimNext(15_002L)!!
            assertEquals(2, finalAttempt.attempts)
            assertEquals(
                KellvynQueuedTaskState.FAILED,
                queue.failOrRetry(retryId, "EXECUTION_FAILED", 15_003L)
            )
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun backgroundClaimSkipsHeavyTasksAndLeavesThemForForegroundExecution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val heavy = queue.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = "test.heavy",
                    payload = "local-build",
                    priority = 100,
                    workloadClass = KellvynWorkloadClass.HEAVY
                ),
                1_000L
            )
            val standard = queue.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = "test.standard",
                    payload = "small-index",
                    priority = 10,
                    workloadClass = KellvynWorkloadClass.STANDARD
                ),
                1_001L
            )

            assertEquals(standard, queue.claimNext(1_002L, allowHeavyWorkload = false)!!.id)
            queue.complete(standard, "indexed", 1_003L)
            assertNull(queue.nextRunnableDelayMillis(1_004L, allowHeavyWorkload = false))
            assertEquals(heavy, queue.claimNext(1_005L, allowHeavyWorkload = true)!!.id)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun registeringHandlerReleasesOnlyItsPreviouslyUnavailableTasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val taskA = queue.enqueue(KellvynQueuedTaskSpec("test.handler.a", "a"), 1_000L)
            val taskB = queue.enqueue(KellvynQueuedTaskSpec("test.handler.b", "b"), 1_001L)
            assertEquals(taskA, queue.claimNext(1_002L)!!.id)
            queue.defer(taskA, 60_000L, "HANDLER_UNAVAILABLE", 1_003L)
            assertEquals(taskB, queue.claimNext(1_004L)!!.id)
            queue.defer(taskB, 60_000L, "HANDLER_UNAVAILABLE", 1_005L)

            assertEquals(1, queue.markHandlerAvailable("test.handler.a", 2_000L))
            assertEquals("QUEUED", queue.get(taskA)!!.state.name)
            assertEquals(KellvynQueuedTaskState.WAITING_RESOURCES, queue.get(taskB)!!.state)
            assertEquals(taskA, queue.claimNext(2_001L)!!.id)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun processRecoveryRequeuesInterruptedTaskAndFailsExhaustedAttempts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val resumable = queue.enqueue(
                KellvynQueuedTaskSpec("test.handler", "resume", maxAttempts = 3),
                1_000L
            )
            queue.claimNext(1_001L)
            assertEquals(1, queue.recoverInterruptedTasks(nowMillis = 2_000L, recoveryDelayMillis = 1_000L))
            assertNull(queue.claimNext(2_999L))
            assertEquals(resumable, queue.claimNext(3_000L)!!.id)
            queue.complete(resumable, "resumed-and-completed", 3_001L)

            val exhausted = queue.enqueue(
                KellvynQueuedTaskSpec("test.handler", "exhausted", maxAttempts = 1),
                4_000L
            )
            assertEquals(exhausted, queue.claimNext(4_001L)!!.id)
            assertEquals(1, queue.recoverInterruptedTasks(nowMillis = 4_002L))
            assertEquals(KellvynQueuedTaskState.FAILED, queue.get(exhausted)!!.state)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun schedulerDefersUnknownHandlerWithoutLeavingTaskRunningAndRunsRegisteredHandler() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            var now = 20_000L
            val broker = KellvynTaskBroker(
                governor = KellvynResourceGovernor(),
                queueWaitMillis = 100L,
                snapshotProvider = { snapshot() }
            )
            val scheduler = KellvynTaskScheduler(queue, broker) { now }
            val missingId = scheduler.enqueue(
                KellvynQueuedTaskSpec(handlerId = "missing.handler", payload = "descriptor")
            )
            val missing = scheduler.runNext() as KellvynScheduledTaskResult.Deferred
            assertEquals(missingId, missing.taskId)
            assertEquals(KellvynQueuedTaskState.WAITING_RESOURCES, queue.get(missingId)!!.state)
            assertEquals("HANDLER_UNAVAILABLE", queue.get(missingId)!!.lastError)
            assertTrue(queue.cancelQueued(missingId, now + 1L))

            scheduler.registerHandler("test.success") { payload -> "completed:$payload" }
            now += 2L
            val successId = scheduler.enqueue(
                KellvynQueuedTaskSpec(handlerId = "test.success", payload = "payload")
            )
            val completed = scheduler.runNext() as KellvynScheduledTaskResult.Completed
            assertEquals(successId, completed.taskId)
            assertEquals("completed:payload", completed.summary)
            assertEquals(KellvynQueuedTaskState.SUCCEEDED, queue.get(successId)!!.state)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    private fun databaseName(): String = "kellvyn-queue-test-" + UUID.randomUUID() + ".db"

    private fun snapshot() = KellvynResourceSnapshot(
        totalSystemMemoryBytes = 8L * 1024L * MEBIBYTE,
        availableSystemMemoryBytes = 3L * 1024L * MEBIBYTE,
        processHeapUsedBytes = 96L * MEBIBYTE,
        processHeapMaxBytes = 512L * MEBIBYTE,
        processPssBytes = 128L * MEBIBYTE,
        lowMemory = false,
        thermalBand = KellvynThermalBand.NONE,
        thermalHeadroomForecast = 0.2f,
        powerSaveMode = false,
        batteryPercent = 80,
        charging = false
    )

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
