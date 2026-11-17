package com.kellvyn.assistant

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun enqueueScheduledTask(spec: KellvynQueuedTaskSpec): String =
        taskScheduler.enqueue(spec)

    fun registerScheduledTaskHandler(handlerId: String, handler: KellvynScheduledTaskHandler) =
        taskScheduler.registerHandler(handlerId, handler)

    suspend fun runNextScheduledTask(): KellvynScheduledTaskResult? =
        taskScheduler.runNext()

    suspend fun runReadyScheduledTasks(maxTasks: Int = 4): List<KellvynScheduledTaskResult> =
        taskScheduler.runReady(maxTasks)

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
    }
}
