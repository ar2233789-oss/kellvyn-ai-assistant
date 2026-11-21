package com.kellvyn.assistant

/**
 * On-demand thermal forecast cache.
 *
 * Android's PowerManager headroom API has no benefit from sampling more often than about once
 * per second. When a device returns NaN or throws (unsupported/temporarily unavailable), back
 * off for ten seconds instead of repeatedly probing a value that cannot currently be supplied.
 * This class has no timer or background polling; the next caller performs the next eligible read.
 */
internal class KellvynThermalHeadroomSampler(
    private val clockMillis: () -> Long,
    private val sample: (forecastSeconds: Int) -> Float?,
    private val forecastSeconds: Int = 10,
    private val validSampleIntervalMillis: Long = 1_000L,
    private val unavailableRetryIntervalMillis: Long = 10_000L
) {
    private val lock = Any()
    private var lastSampleAtMillis: Long? = null
    private var cachedHeadroom: Float? = null

    init {
        require(forecastSeconds in 0..60)
        require(validSampleIntervalMillis > 0L)
        require(unavailableRetryIntervalMillis >= validSampleIntervalMillis)
    }

    fun read(): Float? = synchronized(lock) {
        val now = clockMillis().coerceAtLeast(0L)
        val previous = lastSampleAtMillis
        val interval = if (cachedHeadroom == null) {
            unavailableRetryIntervalMillis
        } else {
            validSampleIntervalMillis
        }

        if (previous != null && now >= previous && now - previous < interval) {
            return@synchronized cachedHeadroom
        }

        lastSampleAtMillis = now
        cachedHeadroom = runCatching { sample(forecastSeconds) }
            .getOrNull()
            ?.takeIf { it.isFinite() && it >= 0f }
        cachedHeadroom
    }
}
