package com.kellvyn.assistant

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic watchdog for a gateway heartbeat ACK.
 *
 * It only decides whether a connection has stopped acknowledging heartbeats. It does not
 * reconnect or send packets itself; the owning adapter controls those side effects.
 */
internal class KellvynHeartbeatWatchdog(
    heartbeatIntervalMillis: Long,
    private val monotonicNanos: () -> Long = System::nanoTime
) {
    private val intervalMillis = heartbeatIntervalMillis.coerceIn(
        MIN_INTERVAL_MILLIS,
        MAX_INTERVAL_MILLIS
    )
    private val ackTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis * 2L)
    private val lastAckNanos = AtomicLong(monotonicNanos())

    fun recordAck() {
        lastAckNanos.set(monotonicNanos())
    }

    fun isExpired(): Boolean {
        val elapsed = monotonicNanos() - lastAckNanos.get()
        return elapsed >= ackTimeoutNanos
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 10_000L
        const val MAX_INTERVAL_MILLIS = 300_000L
    }
}
