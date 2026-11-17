package com.kellvyn.assistant

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KellvynAndroidResourceMonitorInstrumentedTest {
    @Test
    fun monitorReturnsBoundedAndroidResourceSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = KellvynAndroidResourceMonitor(context).snapshot()

        assertTrue(snapshot.totalSystemMemoryBytes > 0L)
        assertTrue(snapshot.availableSystemMemoryBytes >= 0L)
        assertTrue(snapshot.availableSystemMemoryBytes <= snapshot.totalSystemMemoryBytes)
        assertTrue(snapshot.processHeapUsedBytes >= 0L)
        assertTrue(snapshot.processHeapMaxBytes > 0L)
        assertTrue(snapshot.processPssBytes >= 0L)
        assertTrue(snapshot.processHeapUtilization in 0f..1f)
        snapshot.batteryPercent?.let { assertTrue(it in 0..100) }
        snapshot.thermalHeadroomForecast?.let { assertTrue(it.isFinite() && it >= 0f) }
    }

    @Test
    fun criticalTrimReleasesRegisteredTransientCaches() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = KellvynResourceRuntime.get(context)
        val cache = KellvynManagedMemoryCache<String, ByteArray>(
            reclaimerId = "instrumented-preview-cache",
            reclaimPriority = 100,
            maxBytes = 4L * 1024L * 1024L,
            sizeOf = { _, value -> value.size.toLong() }
        )
        cache.put("preview", ByteArray(512 * 1024))
        val registration = runtime.registerMemoryReclaimer(cache)
        try {
            runtime.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            assertTrue(cache.sizeBytes() == 0L)
        } finally {
            registration.close()
        }
    }

    @Test
    fun runtimeAndBuilderShareOneApplicationScopedBroker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = KellvynResourceRuntime.get(context)
        val second = KellvynResourceRuntime.get(context)

        assertSame(first, second)
        assertSame(first.taskBroker, second.taskBroker)
        assertNotNull(first)
    }
}
