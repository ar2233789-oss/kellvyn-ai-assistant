package com.kellvyn.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynResourceGovernorTest {
    private val governor = KellvynResourceGovernor()

    @Test
    fun comfortableDeviceAdmitsLightAndHeavyWorkWithDifferentPermits() {
        val light = governor.evaluate(snapshot(), request("light", KellvynWorkloadClass.LIGHT))
        val heavy = governor.evaluate(snapshot(), request("heavy", KellvynWorkloadClass.HEAVY))

        assertTrue(light is KellvynResourceDecision.Admitted)
        assertEquals(1, (light as KellvynResourceDecision.Admitted).permitsRequired)
        assertTrue(heavy is KellvynResourceDecision.Admitted)
        assertEquals(2, (heavy as KellvynResourceDecision.Admitted).permitsRequired)
    }

    @Test
    fun systemLowMemoryBlocksStandardAndHeavyButAllowsSmallLightWork() {
        val pressured = snapshot().copy(lowMemory = true)

        assertTrue(governor.evaluate(pressured, request("light", KellvynWorkloadClass.LIGHT)) is KellvynResourceDecision.Admitted)
        assertTrue(governor.evaluate(pressured, request("standard", KellvynWorkloadClass.STANDARD)) is KellvynResourceDecision.Deferred)
        assertTrue(governor.evaluate(pressured, request("heavy", KellvynWorkloadClass.HEAVY)) is KellvynResourceDecision.Deferred)
    }

    @Test
    fun memoryEstimateMustFitAlongsideTheSafetyReserve() {
        val constrained = snapshot().copy(availableSystemMemoryBytes = 700L * MEBIBYTE)
        val decision = governor.evaluate(
            constrained,
            KellvynResourceRequest("large-image", KellvynWorkloadClass.HEAVY, 600L * MEBIBYTE)
        )
        assertTrue(decision is KellvynResourceDecision.Deferred)
        assertEquals("INSUFFICIENT_MEMORY_BUDGET", (decision as KellvynResourceDecision.Deferred).reasonCode)
    }

    @Test
    fun thermalPressureDefersHeavyWorkAndSeverePressureDefersAllButLight() {
        val moderate = snapshot().copy(thermalBand = KellvynThermalBand.MODERATE)
        assertTrue(governor.evaluate(moderate, request("heavy", KellvynWorkloadClass.HEAVY)) is KellvynResourceDecision.Deferred)
        assertTrue(governor.evaluate(moderate, request("standard", KellvynWorkloadClass.STANDARD)) is KellvynResourceDecision.Admitted)

        val severe = snapshot().copy(thermalBand = KellvynThermalBand.SEVERE)
        assertTrue(governor.evaluate(severe, request("standard", KellvynWorkloadClass.STANDARD)) is KellvynResourceDecision.Deferred)
        assertTrue(governor.evaluate(severe, request("light", KellvynWorkloadClass.LIGHT)) is KellvynResourceDecision.Admitted)
    }

    @Test
    fun heapThermalHeadroomAndBatterySaverAreIndependentGuards() {
        val highHeap = snapshot().copy(processHeapUsedBytes = 480L * MEBIBYTE)
        assertEquals("PROCESS_HEAP_PRESSURE", deferredReason(highHeap, KellvynWorkloadClass.HEAVY))

        val hotForecast = snapshot().copy(thermalHeadroomForecast = 0.9f)
        assertEquals("THERMAL_HEADROOM_LOW", deferredReason(hotForecast, KellvynWorkloadClass.HEAVY))

        val saver = snapshot().copy(powerSaveMode = true, charging = false)
        assertEquals("BATTERY_SAVER", deferredReason(saver, KellvynWorkloadClass.HEAVY))

        val charging = saver.copy(charging = true)
        assertTrue(governor.evaluate(charging, request("heavy-charging", KellvynWorkloadClass.HEAVY)) is KellvynResourceDecision.Admitted)
    }

    @Test
    fun brokerDoesNotOverlapHeavyWorkWithAnotherTool() = runBlocking {
        val enteredHeavy = CompletableDeferred<Unit>()
        val releaseHeavy = CompletableDeferred<Unit>()
        val enteredLight = CompletableDeferred<Unit>()
        val broker = KellvynTaskBroker(governor) { snapshot() }

        val heavy = async {
            broker.execute(request("heavy-1", KellvynWorkloadClass.HEAVY)) {
                enteredHeavy.complete(Unit)
                releaseHeavy.await()
                "heavy"
            }
        }
        enteredHeavy.await()
        val light = async {
            broker.execute(request("light-1", KellvynWorkloadClass.LIGHT)) {
                enteredLight.complete(Unit)
                "light"
            }
        }

        yield()
        assertFalse(enteredLight.isCompleted)
        releaseHeavy.complete(Unit)
        assertEquals("heavy", heavy.await())
        assertEquals("light", light.await())
    }

    @Test
    fun brokerRechecksResourcesAfterWaitingAndNeverStartsOnStaleAdmission() = runBlocking {
        var samples = 0
        var executed = false
        val broker = KellvynTaskBroker(governor) {
            samples++
            if (samples == 1) snapshot() else snapshot().copy(lowMemory = true)
        }

        val result = runCatching {
            broker.execute(request("recheck", KellvynWorkloadClass.HEAVY)) {
                executed = true
            }
        }.exceptionOrNull()

        assertTrue(result is KellvynTaskDeferredException)
        assertFalse(executed)
        assertEquals(2, samples)
    }

    private fun deferredReason(snapshot: KellvynResourceSnapshot, workload: KellvynWorkloadClass): String =
        (governor.evaluate(snapshot, request("reason", workload)) as KellvynResourceDecision.Deferred).reasonCode

    private fun request(id: String, workload: KellvynWorkloadClass) =
        KellvynResourceRequest(id, workload)

    private fun snapshot() = KellvynResourceSnapshot(
        totalSystemMemoryBytes = 8L * 1024 * MEBIBYTE,
        availableSystemMemoryBytes = 3L * 1024 * MEBIBYTE,
        processHeapUsedBytes = 96L * MEBIBYTE,
        processHeapMaxBytes = 512L * MEBIBYTE,
        processPssBytes = 128L * MEBIBYTE,
        lowMemory = false,
        thermalBand = KellvynThermalBand.NONE,
        thermalHeadroomForecast = 0.2f,
        powerSaveMode = false,
        batteryPercent = 80,
        charging = false
    )

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
