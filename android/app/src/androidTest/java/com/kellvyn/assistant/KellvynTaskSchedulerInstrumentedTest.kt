package com.kellvyn.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KellvynTaskSchedulerInstrumentedTest {

    @Test
    fun enqueuedReadOnlyHandlerRunsAndPersistsVerifiedSummary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val snapshot = sampleSnapshot()
            val scheduler = scheduler(queue, snapshot)
            scheduler.registerHandler(
                KellvynResourceSnapshotTaskHandler.HANDLER_ID,
                KellvynResourceSnapshotTaskHandler { snapshot }
            )

            val taskId = scheduler.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = KellvynResourceSnapshotTaskHandler.HANDLER_ID,
                    payload = KellvynResourceSnapshotTaskHandler.PAYLOAD_V1,
                    workloadClass = KellvynWorkloadClass.LIGHT,
                    estimatedWorkingSetBytes = 8L * MEBIBYTE,
                    maxAttempts = 2
                )
            )

            val results = scheduler.runReady(maxTasks = 1, allowHeavyWorkload = false)
            assertEquals(1, results.size)
            assertTrue(results.single() is KellvynScheduledTaskResult.Completed)

            val saved = queue.get(taskId)
            assertNotNull(saved)
            assertEquals(KellvynQueuedTaskState.SUCCEEDED, saved!!.state)
            assertEquals(1, saved.attempts)
            assertTrue(saved.resultSummary.orEmpty().contains("probe=resource_snapshot.v1"))
            assertTrue(saved.resultSummary.orEmpty().contains("process_heap.used_percent=25"))
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun invalidDescriptorFailsPermanentlyWithoutRetrying() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val snapshot = sampleSnapshot()
            val scheduler = scheduler(queue, snapshot)
            scheduler.registerHandler(
                KellvynResourceSnapshotTaskHandler.HANDLER_ID,
                KellvynResourceSnapshotTaskHandler { snapshot }
            )

            val taskId = scheduler.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = KellvynResourceSnapshotTaskHandler.HANDLER_ID,
                    payload = "unsupported.resource_snapshot.v2",
                    maxAttempts = 5
                )
            )

            val result = scheduler.runNext(allowHeavyWorkload = false)
            assertTrue(result is KellvynScheduledTaskResult.Failed)
            val saved = queue.get(taskId)
            assertNotNull(saved)
            assertEquals(KellvynQueuedTaskState.FAILED, saved!!.state)
            assertEquals(1, saved.attempts)
            assertEquals("INVALID_PAYLOAD", saved.lastError)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun unavailableHandlerIsDeferredWithoutBlockingAnAvailableHandler() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        val queue = KellvynPersistentTaskQueue(context, database)
        try {
            val snapshot = sampleSnapshot()
            val scheduler = scheduler(queue, snapshot)
            scheduler.registerHandler(
                "test.available",
                KellvynScheduledTaskHandler { "available-handler-ran" }
            )

            val unavailableId = scheduler.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = "test.unavailable",
                    payload = "bounded-descriptor",
                    priority = 100
                )
            )
            val availableId = scheduler.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = "test.available",
                    payload = "bounded-descriptor",
                    priority = 50
                )
            )

            val results = scheduler.runReady(maxTasks = 2, allowHeavyWorkload = false)
            assertEquals(2, results.size)
            assertTrue(results[0] is KellvynScheduledTaskResult.Deferred)
            assertTrue(results[1] is KellvynScheduledTaskResult.Completed)

            assertEquals(
                KellvynQueuedTaskState.WAITING_RESOURCES,
                queue.get(unavailableId)!!.state
            )
            assertEquals("HANDLER_UNAVAILABLE", queue.get(unavailableId)!!.lastError)
            assertEquals(KellvynQueuedTaskState.SUCCEEDED, queue.get(availableId)!!.state)
        } finally {
            queue.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun interruptedRunningTaskIsRecoveredAndExecutedByNewScheduler() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = databaseName()
        var now = 50_000L
        val firstQueue = KellvynPersistentTaskQueue(context, database)
        val taskId: String
        try {
            taskId = firstQueue.enqueue(
                KellvynQueuedTaskSpec(
                    handlerId = "test.recovery",
                    payload = "restart-safe",
                    maxAttempts = 3
                ),
                now
            )
            val claimed = firstQueue.claimNext(now + 1L)
            assertNotNull(claimed)
            assertEquals(KellvynQueuedTaskState.RUNNING, claimed!!.state)
        } finally {
            firstQueue.close()
        }

        val reopenedQueue = KellvynPersistentTaskQueue(context, database)
        try {
            now += 10_000L
            val snapshot = sampleSnapshot()
            val newScheduler = KellvynTaskScheduler(
                queue = reopenedQueue,
                taskBroker = broker(snapshot),
                clock = { now }
            )
            newScheduler.registerHandler(
                "test.recovery",
                KellvynScheduledTaskHandler { "recovered-once" }
            )

            // First call performs process recovery and respects the recovery delay.
            assertNull(newScheduler.runNext(allowHeavyWorkload = false))
            now += 5_001L
            val result = newScheduler.runNext(allowHeavyWorkload = false)
            assertTrue(result is KellvynScheduledTaskResult.Completed)

            val saved = reopenedQueue.get(taskId)
            assertNotNull(saved)
            assertEquals(KellvynQueuedTaskState.SUCCEEDED, saved!!.state)
            assertEquals(2, saved.attempts)
            assertEquals("recovered-once", saved.resultSummary)
        } finally {
            reopenedQueue.close()
            context.deleteDatabase(database)
        }
    }

    private fun scheduler(
        queue: KellvynPersistentTaskQueue,
        snapshot: KellvynResourceSnapshot
    ) = KellvynTaskScheduler(queue, broker(snapshot))

    private fun broker(snapshot: KellvynResourceSnapshot) =
        KellvynTaskBroker(
            governor = KellvynResourceGovernor(),
            queueWaitMillis = 1_000L,
            snapshotProvider = { snapshot }
        )

    private fun sampleSnapshot() = KellvynResourceSnapshot(
        totalSystemMemoryBytes = 8L * 1024L * MEBIBYTE,
        availableSystemMemoryBytes = 4L * 1024L * MEBIBYTE,
        processHeapUsedBytes = 128L * MEBIBYTE,
        processHeapMaxBytes = 512L * MEBIBYTE,
        processPssBytes = 150L * MEBIBYTE,
        lowMemory = false,
        thermalBand = KellvynThermalBand.NONE,
        thermalHeadroomForecast = 0.2f,
        powerSaveMode = false,
        batteryPercent = 80,
        charging = false
    )

    private fun databaseName() = "kellvyn_scheduler_test_${UUID.randomUUID()}.db"

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
