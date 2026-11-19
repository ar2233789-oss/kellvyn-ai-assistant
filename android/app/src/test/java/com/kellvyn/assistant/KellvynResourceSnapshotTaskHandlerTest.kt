package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynResourceSnapshotTaskHandlerTest {

    @Test
    fun validVersionedPayloadProducesBoundedReadOnlySnapshotSummary() = runBlocking {
        var sampleCount = 0
        val handler = KellvynResourceSnapshotTaskHandler {
            sampleCount++
            sampleSnapshot()
        }

        val summary = handler.execute(KellvynResourceSnapshotTaskHandler.PAYLOAD_V1)

        assertEquals(1, sampleCount)
        assertTrue(summary.length <= 3_800)
        assertTrue(summary.contains("status=ok"))
        assertTrue(summary.contains("probe=resource_snapshot.v1"))
        assertTrue(summary.contains("system_memory.available_mib=3072"))
        assertTrue(summary.contains("process_heap.used_percent=25"))
        assertTrue(summary.contains("thermal.band=NONE"))
        assertTrue(summary.contains("thermal.headroom_moderate_threshold=0.65"))
        assertTrue(summary.contains("thermal.headroom_severe_threshold=0.9"))
        assertTrue(summary.contains("battery.percent=80"))
    }

    @Test
    fun unsupportedPayloadIsPermanentlyRejectedBeforeReadingDeviceState() {
        var sampleCount = 0
        val handler = KellvynResourceSnapshotTaskHandler {
            sampleCount++
            sampleSnapshot()
        }

        val error = runCatching {
            runBlocking { handler.execute("resource_snapshot.v2") }
        }.exceptionOrNull()

        assertTrue(error is KellvynPermanentScheduledTaskException)
        assertEquals("INVALID_PAYLOAD", (error as KellvynPermanentScheduledTaskException).reasonCode)
        assertEquals(0, sampleCount)
    }

    private fun sampleSnapshot() = KellvynResourceSnapshot(
        totalSystemMemoryBytes = 8L * 1024 * MEBIBYTE,
        availableSystemMemoryBytes = 3L * 1024 * MEBIBYTE,
        processHeapUsedBytes = 128L * MEBIBYTE,
        processHeapMaxBytes = 512L * MEBIBYTE,
        processPssBytes = 150L * MEBIBYTE,
        lowMemory = false,
        thermalBand = KellvynThermalBand.NONE,
        thermalHeadroomForecast = 0.2f,
        thermalModerateHeadroomThreshold = 0.65f,
        thermalSevereHeadroomThreshold = 0.9f,
        powerSaveMode = false,
        batteryPercent = 80,
        charging = false
    )

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
