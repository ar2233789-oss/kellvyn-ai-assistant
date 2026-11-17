package com.kellvyn.assistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WorkManager adapter for short, restartable LIGHT/STANDARD queue handlers.
 *
 * HEAVY jobs are intentionally not claimed here. They remain durable until a foreground
 * execution path explicitly allows them. This worker is capped below WorkManager's normal
 * execution window and never promotes itself to a long-running foreground service.
 */
class KellvynAutomationDrainWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val resources = try {
            KellvynResourceRuntime.get(applicationContext)
        } catch (_: Exception) {
            return Result.retry()
        }

        return try {
            // The timeout applies to the complete worker session, not each individual tool.
            // Cancellation returns a claimed task to the durable queue through the scheduler.
            withTimeoutOrNull(MAX_WORKER_SESSION_MILLIS) {
                resources.runReadyScheduledTasks(
                    maxTasks = MAX_TASKS_PER_WORKER,
                    allowHeavyWorkload = false
                )
            }

            val nextDelay = resources.nextRunnableScheduledTaskDelayMillis(
                allowHeavyWorkload = false
            )
            if (nextDelay != null) {
                resources.scheduleQueueDrain(nextDelay)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private companion object {
        const val MAX_TASKS_PER_WORKER = 2
        const val MAX_WORKER_SESSION_MILLIS = 8L * 60L * 1_000L
    }
}
