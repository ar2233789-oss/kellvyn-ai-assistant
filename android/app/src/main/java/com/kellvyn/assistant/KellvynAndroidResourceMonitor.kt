package com.kellvyn.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock

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

    private val thermalLock = Any()
    private var cachedHeadroomSampleAt = 0L
    private var cachedHeadroom: Float? = null

    fun snapshot(): KellvynResourceSnapshot {
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val heapMax = runtime.maxMemory().coerceAtLeast(1L)
        val totalMemory = memoryInfo.totalMem.takeIf { it > 0L } ?: heapMax
        val availableMemory = memoryInfo.availMem.coerceIn(0L, totalMemory)
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
            thermalBand = readThermalBand(),
            thermalHeadroomForecast = readThermalHeadroomCached(),
            powerSaveMode = powerManager?.isPowerSaveMode ?: false,
            batteryPercent = batteryPercent,
            charging = charging
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

    /** Android recommends not querying thermal headroom more frequently than once per second. */
    private fun readThermalHeadroomCached(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        synchronized(thermalLock) {
            val now = SystemClock.elapsedRealtime()
            if (cachedHeadroomSampleAt > 0L &&
                now - cachedHeadroomSampleAt < THERMAL_SAMPLE_INTERVAL_MS
            ) {
                return cachedHeadroom
            }
            cachedHeadroomSampleAt = now
            cachedHeadroom = runCatching {
                powerManager?.getThermalHeadroom(THERMAL_FORECAST_SECONDS)
            }.getOrNull()?.takeIf { it.isFinite() && it >= 0f }
            return cachedHeadroom
        }
    }

    private companion object {
        const val BYTES_PER_KILOBYTE = 1024L
        const val THERMAL_SAMPLE_INTERVAL_MS = 1_000L
        const val THERMAL_FORECAST_SECONDS = 10
    }
}
