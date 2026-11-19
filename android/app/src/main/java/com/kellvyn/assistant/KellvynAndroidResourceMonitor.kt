package com.kellvyn.assistant

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads Android resource state only when a task is about to start or resume.
 * Call from a worker/coroutine dispatcher, not from the main thread.
 */
internal class KellvynAndroidResourceMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val thermalHeadroomSampler = KellvynThermalHeadroomSampler(
        clockMillis = { SystemClock.elapsedRealtime() },
        sample = { forecastSeconds -> powerManager?.getThermalHeadroom(forecastSeconds) },
        forecastSeconds = THERMAL_FORECAST_SECONDS
    )
    private val thermalHeadroomThresholds: Pair<Float?, Float?> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        readThermalHeadroomThresholds()
    }
    private val memoryPressure = AtomicReference(KellvynMemoryPressure.NORMAL)

    /** Called by the application-scoped ComponentCallbacks2 listener, never polled. */
    fun recordTrimMemory(level: Int) {
        val incoming = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> KellvynMemoryPressure.MODERATE
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> KellvynMemoryPressure.HIGH
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> KellvynMemoryPressure.CRITICAL
            else -> null
        } ?: return
        memoryPressure.updateAndGet { current ->
            if (incoming.ordinal > current.ordinal) incoming else current
        }
    }

    fun recordLowMemory() {
        memoryPressure.set(KellvynMemoryPressure.CRITICAL)
    }

    fun snapshot(): KellvynResourceSnapshot {
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val heapMax = runtime.maxMemory().coerceAtLeast(1L)
        val totalMemory = memoryInfo.totalMem.takeIf { it > 0L } ?: heapMax
        val availableMemory = memoryInfo.availMem.coerceIn(0L, totalMemory)
        if (!memoryInfo.lowMemory && availableMemory.toDouble() / totalMemory.toDouble() >= MEMORY_PRESSURE_RESET_FRACTION) {
            memoryPressure.set(KellvynMemoryPressure.NORMAL)
        }
        val pssBytes = runCatching { Debug.getPss().coerceAtLeast(0L) * BYTES_PER_KILOBYTE }
            .getOrDefault(0L)
        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        val rawBattery = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()
        val batteryPercent = rawBattery?.takeIf { it in 0..100 }

        return KellvynResourceSnapshot(
            totalSystemMemoryBytes = totalMemory,
            availableSystemMemoryBytes = availableMemory,
            processHeapUsedBytes = heapUsed,
            processHeapMaxBytes = heapMax,
            processPssBytes = pssBytes,
            lowMemory = memoryInfo.lowMemory,
            processCpuTimeMillis = android.os.Process.getElapsedCpuTime().coerceAtLeast(0L),
            sampledAtElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            availableProcessors = runtime.availableProcessors().coerceAtLeast(1),
            thermalBand = readThermalBand(),
            thermalHeadroomForecast = readThermalHeadroomCached(),
            thermalModerateHeadroomThreshold = thermalHeadroomThresholds.first,
            thermalSevereHeadroomThreshold = thermalHeadroomThresholds.second,
            powerSaveMode = powerManager?.isPowerSaveMode ?: false,
            batteryPercent = batteryPercent,
            charging = charging,
            memoryPressure = memoryPressure.get()
        )
    }

    private fun readThermalBand(): KellvynThermalBand {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return KellvynThermalBand.UNKNOWN
        val status = runCatching { powerManager?.currentThermalStatus }
            .getOrNull() ?: return KellvynThermalBand.UNKNOWN
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> KellvynThermalBand.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> KellvynThermalBand.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> KellvynThermalBand.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> KellvynThermalBand.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> KellvynThermalBand.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> KellvynThermalBand.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> KellvynThermalBand.SHUTDOWN
            else -> KellvynThermalBand.UNKNOWN
        }
    }

    /** OEM thresholds are static device metadata; read once, only on Android 15/API 35+. */
    private fun readThermalHeadroomThresholds(): Pair<Float?, Float?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null to null
        val thresholds = runCatching {
            powerManager?.getThermalHeadroomThresholds()
        }.getOrNull() ?: return null to null

        fun threshold(status: Int): Float? = thresholds[status]
            ?.takeIf { it.isFinite() && it >= 0f }

        return threshold(PowerManager.THERMAL_STATUS_MODERATE) to
            threshold(PowerManager.THERMAL_STATUS_SEVERE)
    }

    /** Valid samples refresh each second; unsupported/NaN readings back off for ten seconds. */
    private fun readThermalHeadroomCached(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return thermalHeadroomSampler.read()
    }

    private companion object {
        const val BYTES_PER_KILOBYTE = 1024L
        const val THERMAL_FORECAST_SECONDS = 10
        const val MEMORY_PRESSURE_RESET_FRACTION = 0.20
    }
}
