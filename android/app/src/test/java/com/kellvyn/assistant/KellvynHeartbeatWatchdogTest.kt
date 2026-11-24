package com.kellvyn.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynHeartbeatWatchdogTest {
    @Test
    fun expiresOnlyAfterTwoHeartbeatIntervalsWithoutAck() {
        var nowNanos = 0L
        val watchdog = KellvynHeartbeatWatchdog(
            heartbeatIntervalMillis = 10_000L,
            monotonicNanos = { nowNanos }
        )

        assertFalse(watchdog.isExpired())
        nowNanos = 19_999_999_999L
        assertFalse(watchdog.isExpired())
        nowNanos = 20_000_000_000L
        assertTrue(watchdog.isExpired())
    }

    @Test
    fun aNewAckResetsTheWatchdogDeadline() {
        var nowNanos = 0L
        val watchdog = KellvynHeartbeatWatchdog(
            heartbeatIntervalMillis = 15_000L,
            monotonicNanos = { nowNanos }
        )

        nowNanos = 29_000_000_000L
        assertFalse(watchdog.isExpired())
        watchdog.recordAck()
        nowNanos += 29_999_999_999L
        assertFalse(watchdog.isExpired())
        nowNanos += 1L
        assertTrue(watchdog.isExpired())
    }

    @Test
    fun heartbeatIntervalsAreClampedToSafeBounds() {
        var nowNanos = 0L
        val tooShort = KellvynHeartbeatWatchdog(1L) { nowNanos }
        nowNanos = 19_999_999_999L
        assertFalse(tooShort.isExpired())
        nowNanos += 1L
        assertTrue(tooShort.isExpired())

        nowNanos = 0L
        val tooLong = KellvynHeartbeatWatchdog(600_000L) { nowNanos }
        nowNanos = 599_999_999_999L
        assertFalse(tooLong.isExpired())
        nowNanos += 1L
        assertTrue(tooLong.isExpired())
    }
}
