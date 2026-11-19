package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynThermalHeadroomSamplerTest {

    @Test
    fun validSamplesAreCachedForOneSecondWithoutPolling() {
        var now = 1_000L
        var calls = 0
        val sampler = KellvynThermalHeadroomSampler(
            clockMillis = { now },
            sample = { forecast ->
                assertEquals(10, forecast)
                calls++
                calls.toFloat()
            }
        )

        assertEquals(1f, sampler.read()!!)
        now = 1_999L
        assertEquals(1f, sampler.read()!!)
        assertEquals(1, calls)

        now = 2_000L
        assertEquals(2f, sampler.read()!!)
        assertEquals(2, calls)
    }

    @Test
    fun unavailableSamplesBackOffForTenSecondsAndRecoverWhenAvailable() {
        var now = 0L
        var calls = 0
        var value: Float? = Float.NaN
        val sampler = KellvynThermalHeadroomSampler(
            clockMillis = { now },
            sample = {
                calls++
                value
            }
        )

        assertNull(sampler.read())
        now = 9_999L
        assertNull(sampler.read())
        assertEquals(1, calls)

        now = 10_000L
        value = 0.72f
        assertEquals(0.72f, sampler.read()!!)
        assertEquals(2, calls)

        now = 10_999L
        assertEquals(0.72f, sampler.read()!!)
        assertEquals(2, calls)

        now = 11_000L
        value = 0.68f
        assertEquals(0.68f, sampler.read()!!)
        assertEquals(3, calls)
    }

    @Test
    fun thrownSampleUsesSameUnavailableBackoff() {
        var now = 5_000L
        var calls = 0
        val sampler = KellvynThermalHeadroomSampler(
            clockMillis = { now },
            sample = {
                calls++
                if (calls == 1) error("thermal service unavailable")
                0.4f
            }
        )

        assertNull(sampler.read())
        now = 6_000L
        assertNull(sampler.read())
        assertEquals(1, calls)

        now = 15_000L
        assertEquals(0.4f, sampler.read()!!)
        assertEquals(2, calls)
    }

    @Test
    fun negativeOrInfiniteSamplesAreNotCachedAsValidHeadroom() {
        var now = 0L
        var calls = 0
        val sampler = KellvynThermalHeadroomSampler(
            clockMillis = { now },
            sample = {
                calls++
                if (calls == 1) -1f else Float.POSITIVE_INFINITY
            }
        )

        assertNull(sampler.read())
        now = 10_000L
        assertNull(sampler.read())
        assertTrue(calls == 2)
    }
}
