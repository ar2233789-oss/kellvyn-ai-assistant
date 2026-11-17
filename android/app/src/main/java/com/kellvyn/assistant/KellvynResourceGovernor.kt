package com.kellvyn.assistant

import kotlinx.coroutines.sync.Semaphore

/** Workload classes are resource hints, not semantic agent roles. */
internal enum class KellvynWorkloadClass {
    LIGHT,
    STANDARD,
    HEAVY
}

internal enum class KellvynThermalBand {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN
}

/** A point-in-time snapshot. Values are sampled on demand; this class has no polling loop. */
internal data class KellvynResourceSnapshot(
    val totalSystemMemoryBytes: Long,
    val availableSystemMemoryBytes: Long,
    val processHeapUsedBytes: Long,
    val processHeapMaxBytes: Long,
    val processPssBytes: Long,
    val lowMemory: Boolean,
    val thermalBand: KellvynThermalBand,
    val thermalHeadroomForecast: Float?,
    val powerSaveMode: Boolean,
    val batteryPercent: Int?,
    val charging: Boolean
) {
    init {
        require(totalSystemMemoryBytes > 0L)
        require(availableSystemMemoryBytes in 0L..totalSystemMemoryBytes)
        require(processHeapUsedBytes >= 0L)
        require(processHeapMaxBytes > 0L)
        require(processPssBytes >= 0L)
        require(batteryPercent == null || batteryPercent in 0..100)
        require(thermalHeadroomForecast == null ||
            (thermalHeadroomForecast.isFinite() && thermalHeadroomForecast >= 0f))
    }

    val processHeapUtilization: Float
        get() = (processHeapUsedBytes.toDouble() / processHeapMaxBytes.toDouble())
            .coerceIn(0.0, 1.0).toFloat()

    val availableMemoryFraction: Float
        get() = (availableSystemMemoryBytes.toDouble() / totalSystemMemoryBytes.toDouble())
            .coerceIn(0.0, 1.0).toFloat()
}

internal data class KellvynResourceRequest(
    val taskId: String,
    val workload: KellvynWorkloadClass,
    val estimatedWorkingSetBytes: Long = 0L
) {
    init {
        require(taskId.isNotBlank() && taskId.length <= 96)
        require(estimatedWorkingSetBytes >= 0L)
    }
}

internal sealed class KellvynResourceDecision {
    data class Admitted(
        /** Number of shared execution permits to reserve. Heavy work reserves both permits. */
        val permitsRequired: Int,
        val reason: String
    ) : KellvynResourceDecision() {
        init { require(permitsRequired in 1..2) }
    }

    data class Deferred(
        val reasonCode: String,
        val message: String,
        val retryAfterMillis: Long
    ) : KellvynResourceDecision() {
        init { require(retryAfterMillis > 0L) }
    }
}

/**
 * Conservative admission policy for the 8 GB Poco F6 target.
 * Thresholds are configurable and must be calibrated with physical-device traces.
 * This class is pure Kotlin so its decisions can be tested without an Android runtime.
 */
internal class KellvynResourceGovernor(
    private val config: Config = Config()
) {
    data class Config(
        val minimumFreeMemoryFraction: Float = 0.06f,
        val minimumFreeMemoryFloorBytes: Long = 192L * MEBIBYTE,
        val appHeapSoftLimit: Float = 0.82f,
        val appHeapHardLimit: Float = 0.93f,
        val heavyThermalHeadroomLimit: Float = 0.85f,
        val hardThermalHeadroomLimit: Float = 1.0f,
        val lightDefaultEstimateBytes: Long = 16L * MEBIBYTE,
        val standardDefaultEstimateBytes: Long = 128L * MEBIBYTE,
        val heavyDefaultEstimateBytes: Long = 512L * MEBIBYTE,
        val lowMemoryRetryMillis: Long = 30_000L,
        val thermalRetryMillis: Long = 60_000L,
        val batteryRetryMillis: Long = 120_000L
    ) {
        init {
            require(minimumFreeMemoryFraction in 0.01f..0.30f)
            require(minimumFreeMemoryFloorBytes >= 0L)
            require(appHeapSoftLimit in 0.50f..0.95f)
            require(appHeapHardLimit > appHeapSoftLimit && appHeapHardLimit <= 0.99f)
            require(heavyThermalHeadroomLimit in 0.50f..0.99f)
            require(hardThermalHeadroomLimit >= 0.90f)
            require(lightDefaultEstimateBytes >= 0L)
            require(standardDefaultEstimateBytes >= lightDefaultEstimateBytes)
            require(heavyDefaultEstimateBytes >= standardDefaultEstimateBytes)
            require(lowMemoryRetryMillis > 0L && thermalRetryMillis > 0L && batteryRetryMillis > 0L)
        }
    }

    fun evaluate(
        snapshot: KellvynResourceSnapshot,
        request: KellvynResourceRequest
    ): KellvynResourceDecision {
        val estimate = request.estimatedWorkingSetBytes.takeIf { it > 0L }
            ?: defaultEstimate(request.workload)
        val reserve = maxOf(
            config.minimumFreeMemoryFloorBytes,
            (snapshot.totalSystemMemoryBytes * config.minimumFreeMemoryFraction).toLong()
        )

        if (snapshot.lowMemory && request.workload != KellvynWorkloadClass.LIGHT) {
            return deferred(
                "SYSTEM_LOW_MEMORY",
                "O Android sinalizou pressão de memória; a tarefa foi adiada para proteger o aplicativo.",
                config.lowMemoryRetryMillis
            )
        }

        if (snapshot.availableSystemMemoryBytes < reserve + estimate) {
            return deferred(
                "INSUFFICIENT_MEMORY_BUDGET",
                "A memória disponível não cobre a estimativa da tarefa e a reserva de segurança.",
                config.lowMemoryRetryMillis
            )
        }

        val heapUse = snapshot.processHeapUtilization
        if (request.workload == KellvynWorkloadClass.HEAVY && heapUse >= config.appHeapSoftLimit) {
            return deferred(
                "PROCESS_HEAP_PRESSURE",
                "O heap do processo está próximo do limite; o trabalho pesado foi adiado.",
                config.lowMemoryRetryMillis
            )
        }
        if (request.workload != KellvynWorkloadClass.LIGHT && heapUse >= config.appHeapHardLimit) {
            return deferred(
                "PROCESS_HEAP_CRITICAL",
                "O heap do processo está em nível crítico para iniciar outra tarefa de processamento.",
                config.lowMemoryRetryMillis
            )
        }

        if (request.workload == KellvynWorkloadClass.HEAVY &&
            snapshot.thermalBand.ordinal >= KellvynThermalBand.MODERATE.ordinal &&
            snapshot.thermalBand != KellvynThermalBand.UNKNOWN
        ) {
            return deferred(
                "THERMAL_PRESSURE",
                "A condição térmica do aparelho não permite iniciar outra tarefa pesada com segurança.",
                config.thermalRetryMillis
            )
        }
        if (request.workload != KellvynWorkloadClass.LIGHT &&
            snapshot.thermalBand.ordinal >= KellvynThermalBand.SEVERE.ordinal &&
            snapshot.thermalBand != KellvynThermalBand.UNKNOWN
        ) {
            return deferred(
                "THERMAL_CRITICAL",
                "A temperatura do aparelho está em uma faixa crítica para processamento adicional.",
                config.thermalRetryMillis
            )
        }

        val headroom = snapshot.thermalHeadroomForecast
        if (headroom != null) {
            if (request.workload == KellvynWorkloadClass.HEAVY &&
                headroom >= config.heavyThermalHeadroomLimit
            ) {
                return deferred(
                    "THERMAL_HEADROOM_LOW",
                    "A previsão térmica indica pouca margem para iniciar trabalho pesado.",
                    config.thermalRetryMillis
                )
            }
            if (request.workload != KellvynWorkloadClass.LIGHT &&
                headroom >= config.hardThermalHeadroomLimit
            ) {
                return deferred(
                    "THERMAL_HEADROOM_CRITICAL",
                    "A previsão térmica indica risco de throttling severo.",
                    config.thermalRetryMillis
                )
            }
        }

        if (request.workload == KellvynWorkloadClass.HEAVY && snapshot.powerSaveMode && !snapshot.charging) {
            return deferred(
                "BATTERY_SAVER",
                "A economia de bateria está ativa e o aparelho não está carregando; o trabalho pesado foi adiado.",
                config.batteryRetryMillis
            )
        }
        if (request.workload == KellvynWorkloadClass.HEAVY &&
            snapshot.batteryPercent != null && snapshot.batteryPercent <= 10 && !snapshot.charging
        ) {
            return deferred(
                "BATTERY_LOW",
                "A bateria está muito baixa para iniciar uma tarefa pesada sem carregamento.",
                config.batteryRetryMillis
            )
        }

        // Two LIGHT jobs may coexist only when the system and app heap both have comfortable headroom.
        // STANDARD and HEAVY jobs reserve both permits and therefore never overlap each other.
        val lightCanShare = request.workload == KellvynWorkloadClass.LIGHT &&
            snapshot.availableMemoryFraction >= 0.18f &&
            heapUse < 0.75f &&
            snapshot.thermalBand.ordinal <= KellvynThermalBand.LIGHT.ordinal
        return KellvynResourceDecision.Admitted(
            permitsRequired = if (lightCanShare) 1 else 2,
            reason = if (lightCanShare) "HEADROOM_COMFORTABLE" else "EXCLUSIVE_RESOURCE_SLOT"
        )
    }

    private fun defaultEstimate(workload: KellvynWorkloadClass): Long = when (workload) {
        KellvynWorkloadClass.LIGHT -> config.lightDefaultEstimateBytes
        KellvynWorkloadClass.STANDARD -> config.standardDefaultEstimateBytes
        KellvynWorkloadClass.HEAVY -> config.heavyDefaultEstimateBytes
    }

    private fun deferred(code: String, message: String, retry: Long) =
        KellvynResourceDecision.Deferred(code, message, retry)

    companion object {
        private const val MEBIBYTE = 1024L * 1024L
    }
}

internal class KellvynTaskDeferredException(
    val reasonCode: String,
    val retryAfterMillis: Long,
    message: String
) : Exception(message)

/**
 * A fair two-permit execution gate. LIGHT work can share the permits only when admitted with
 * one permit; STANDARD and HEAVY work reserve both. The device is sampled before waiting and
 * immediately before entering the operation, preventing stale admission.
 *
 * This is an execution gate, not a persistent background scheduler or a priority queue.
 */
internal class KellvynTaskBroker(
    private val governor: KellvynResourceGovernor,
    private val snapshotProvider: suspend () -> KellvynResourceSnapshot
) {
    private val permits = Semaphore(2)

    suspend fun <T> execute(
        request: KellvynResourceRequest,
        operation: suspend () -> T
    ): T {
        val first = evaluate(request)
        if (first is KellvynResourceDecision.Deferred) throw first.toException()

        var acquired = 0
        try {
            val initialPermits = (first as KellvynResourceDecision.Admitted).permitsRequired
            acquire(initialPermits)
            acquired = initialPermits

            // Recheck after waiting. If conditions worsened, do not launch using stale metrics.
            repeat(3) {
                when (val latest = evaluate(request)) {
                    is KellvynResourceDecision.Deferred -> throw latest.toException()
                    is KellvynResourceDecision.Admitted -> {
                        if (latest.permitsRequired <= acquired) {
                            if (latest.permitsRequired < acquired) {
                                release(acquired - latest.permitsRequired)
                                acquired = latest.permitsRequired
                            }
                            return operation()
                        }
                        val additional = latest.permitsRequired - acquired
                        acquire(additional)
                        acquired += additional
                    }
                }
            }
            throw KellvynTaskDeferredException(
                reasonCode = "RESOURCE_STATE_UNSTABLE",
                retryAfterMillis = 5_000L,
                message = "As condições do aparelho mudaram repetidamente durante a admissão da tarefa."
            )
        } finally {
            if (acquired > 0) release(acquired)
        }
    }

    private suspend fun evaluate(request: KellvynResourceRequest): KellvynResourceDecision =
        governor.evaluate(snapshotProvider(), request)

    private suspend fun acquire(count: Int) {
        var acquiredHere = 0
        try {
            repeat(count) {
                permits.acquire()
                acquiredHere++
            }
        } catch (error: Throwable) {
            if (acquiredHere > 0) release(acquiredHere)
            throw error
        }
    }

    private fun release(count: Int) {
        repeat(count) { permits.release() }
    }

    private fun KellvynResourceDecision.Deferred.toException() =
        KellvynTaskDeferredException(reasonCode, retryAfterMillis, message)
}
