package com.kellvyn.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Application-scoped shared resource services. Runtime, agents and the APK builder use the
 * same broker so a neural training run cannot overlap an Android build in another component.
 */
internal class KellvynResourceRuntime private constructor(context: Context) {
    private val monitor = KellvynAndroidResourceMonitor(context.applicationContext)
    private val governor = KellvynResourceGovernor()

    val taskBroker = KellvynTaskBroker(governor) {
        withContext(Dispatchers.IO) { monitor.snapshot() }
    }

    suspend fun snapshot(): KellvynResourceSnapshot =
        withContext(Dispatchers.IO) { monitor.snapshot() }

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
    }
}
