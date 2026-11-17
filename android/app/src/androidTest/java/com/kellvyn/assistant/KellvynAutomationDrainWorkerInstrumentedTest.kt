package com.kellvyn.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class KellvynAutomationDrainWorkerInstrumentedTest {
    @Test
    fun emptyWorkerSessionCompletesWithoutStartingHeavyTasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<KellvynAutomationDrainWorker>(context).build()

        val result = worker.doWork()

        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
    }

    @Test
    fun queueDrainIsScheduledAsUniqueDelayedWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkManager.getInstance(context)
        val runtime = KellvynResourceRuntime.get(context)

        runtime.scheduleQueueDrain(delayMillis = 60_000L)
        val infos = manager.getWorkInfosForUniqueWork(
            KellvynResourceRuntime.QUEUE_DRAIN_WORK_NAME
        ).get(10, TimeUnit.SECONDS)

        assertTrue(infos.isNotEmpty())
        assertTrue(infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED })

        manager.cancelUniqueWork(KellvynResourceRuntime.QUEUE_DRAIN_WORK_NAME)
            .result.get(10, TimeUnit.SECONDS)
    }
}
