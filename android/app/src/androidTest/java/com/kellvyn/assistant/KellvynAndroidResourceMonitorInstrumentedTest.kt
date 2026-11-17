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
    fun runtimeAndBuilderShareOneApplicationScopedBroker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = KellvynResourceRuntime.get(context)
        val second = KellvynResourceRuntime.get(context)

        assertSame(first, second)
        assertSame(first.taskBroker, second.taskBroker)
        assertNotNull(first)
    }
}
