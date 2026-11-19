package com.kellvyn.assistant

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Application-scoped shared resource services. Runtime, agents and the APK builder use the
 * same broker so a neural training run cannot overlap an Android build in another component.
 */
internal class KellvynResourceRuntime private constructor(context: Context) : ComponentCallbacks2 {
    private val appContext = context.applicationContext
    private val monitor = KellvynAndroidResourceMonitor(appContext)
    private val governor = KellvynResourceGovernor()
    private val cacheRegistry = KellvynResourceCacheRegistry()

    val taskBroker = KellvynTaskBroker(governor) {
        withContext(Dispatchers.IO) { monitor.snapshot() }
    }
    private val taskQueue = KellvynPersistentTaskQueue(appContext)
    val taskScheduler = KellvynTaskScheduler(taskQueue, taskBroker)
    private val builtInHandlerMutex = Mutex()
    private var builtInTaskHandlersRegistered = false

    val studioModules = KellvynStudioModuleManager(
        deviceSdkInt = Build.VERSION.SDK_INT,
        taskBroker = taskBroker
    )

    init {
        // One application-scoped callback; no service or polling thread is kept alive.
        appContext.registerComponentCallbacks(this)
    }

    suspend fun snapshot(): KellvynResourceSnapshot =
        withContext(Dispatchers.IO) { monitor.snapshot() }

    suspend fun pendingScheduledTaskCount(): Int =
        withContext(Dispatchers.IO) { taskQueue.pendingCount() }

    suspend fun enqueueScheduledTask(spec: KellvynQueuedTaskSpec): String {
        val id = taskScheduler.enqueue(spec)
        scheduleQueueDrain()
        return id
    }

    /**
     * Installs only explicitly allowlisted internal handlers. Called by foreground enqueue and
     * by the WorkManager drain after process recreation; no model-supplied handler IDs are used.
     */
    suspend fun ensureBuiltInScheduledTaskHandlers() {
        builtInHandlerMutex.withLock {
            if (builtInTaskHandlersRegistered) return@withLock
            taskScheduler.registerHandler(
                KellvynResourceSnapshotTaskHandler.HANDLER_ID,
                KellvynResourceSnapshotTaskHandler { snapshot() }
            )
            builtInTaskHandlersRegistered = true
        }
    }

    /** Queue a safe read-only probe through the same persistent path used by scheduled work. */
    suspend fun enqueueResourceSnapshotProbe(): String {
        ensureBuiltInScheduledTaskHandlers()
        return enqueueScheduledTask(
            KellvynQueuedTaskSpec(
                handlerId = KellvynResourceSnapshotTaskHandler.HANDLER_ID,
                payload = KellvynResourceSnapshotTaskHandler.PAYLOAD_V1,
                priority = 60,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 16L * MEBIBYTE,
                maxAttempts = 2
            )
        )
    }

    /** Bounded status read for audit, diagnostics and tests; never exposes decrypted payloads. */
    suspend fun scheduledTaskStatus(taskId: String): KellvynQueuedTask? =
        withContext(Dispatchers.IO) { taskQueue.get(taskId) }

    suspend fun registerScheduledTaskHandler(
        handlerId: String,
        handler: KellvynScheduledTaskHandler
    ) {
        taskScheduler.registerHandler(handlerId, handler)
        scheduleQueueDrain()
    }

    suspend fun runNextScheduledTask(
        allowHeavyWorkload: Boolean = true
    ): KellvynScheduledTaskResult? = taskScheduler.runNext(allowHeavyWorkload)

    suspend fun runReadyScheduledTasks(
        maxTasks: Int = 4,
        allowHeavyWorkload: Boolean = true
    ): List<KellvynScheduledTaskResult> =
        taskScheduler.runReady(maxTasks, allowHeavyWorkload)

    suspend fun nextRunnableScheduledTaskDelayMillis(
        allowHeavyWorkload: Boolean = false
    ): Long? = taskScheduler.nextRunnableDelayMillis(allowHeavyWorkload)

    fun scheduleQueueDrain(delayMillis: Long = 0L) {
        require(delayMillis >= 0L)
        val request = OneTimeWorkRequestBuilder<KellvynAutomationDrainWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag(QUEUE_DRAIN_TAG)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            QUEUE_DRAIN_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun registerMemoryReclaimer(reclaimer: KellvynMemoryReclaimer): AutoCloseable =
        cacheRegistry.register(reclaimer)

    fun activeReclaimerCount(): Int = cacheRegistry.activeReclaimerCount()

    override fun onTrimMemory(level: Int) {
        monitor.recordTrimMemory(level)
        val pressure = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> KellvynCachePressure.UI_HIDDEN
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> KellvynCachePressure.MODERATE
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> KellvynCachePressure.HIGH
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> KellvynCachePressure.CRITICAL
            else -> if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
                KellvynCachePressure.CRITICAL
            } else {
                null
            }
        } ?: return

        cacheRegistry.reclaim(
            level = pressure,
            targetBytes = when (pressure) {
                KellvynCachePressure.UI_HIDDEN -> 64L * MEBIBYTE
                KellvynCachePressure.MODERATE -> 128L * MEBIBYTE
                KellvynCachePressure.HIGH -> 256L * MEBIBYTE
                KellvynCachePressure.CRITICAL -> Long.MAX_VALUE
            }
        )
        studioModules.trimIdleModules(pressure)
    }

    override fun onLowMemory() {
        monitor.recordLowMemory()
        cacheRegistry.reclaim(KellvynCachePressure.CRITICAL)
        studioModules.trimIdleModules(KellvynCachePressure.CRITICAL)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    companion object {
        @Volatile
        private var instance: KellvynResourceRuntime? = null

        fun get(context: Context): KellvynResourceRuntime {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: KellvynResourceRuntime(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private const val MEBIBYTE = 1024L * 1024L
        const val QUEUE_DRAIN_WORK_NAME = "kellvyn-automation-queue-drain"
        const val QUEUE_DRAIN_TAG = "kellvyn-automation-queue"
    }
}
