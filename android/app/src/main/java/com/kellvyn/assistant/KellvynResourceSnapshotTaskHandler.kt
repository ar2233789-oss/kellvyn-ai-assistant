package com.kellvyn.assistant

/**
 * First production-safe queue handler: a bounded, read-only device resource snapshot.
 *
 * This handler is deliberately idempotent. It has no write, network, process, or device-control
 * side effects; repeated execution only observes a fresh snapshot and returns a bounded summary.
 */
internal class KellvynResourceSnapshotTaskHandler(
    private val snapshotProvider: suspend () -> KellvynResourceSnapshot
) : KellvynScheduledTaskHandler {

    override suspend fun execute(payload: String): String {
        if (payload != PAYLOAD_V1) {
            throw KellvynPermanentScheduledTaskException(
                reasonCode = "INVALID_PAYLOAD",
                message = "O descritor da tarefa não corresponde à versão suportada."
            )
        }

        val snapshot = snapshotProvider()
        val heapPercent = (snapshot.processHeapUtilization * 100f)
            .toInt()
            .coerceIn(0, 100)

        return listOf(
            "status=ok",
            "probe=$PAYLOAD_V1",
            "system_memory.available_mib=${snapshot.availableSystemMemoryBytes / MEBIBYTE}",
            "system_memory.total_mib=${snapshot.totalSystemMemoryBytes / MEBIBYTE}",
            "system_memory.low=${snapshot.lowMemory}",
            "process_heap.used_mib=${snapshot.processHeapUsedBytes / MEBIBYTE}",
            "process_heap.max_mib=${snapshot.processHeapMaxBytes / MEBIBYTE}",
            "process_heap.used_percent=$heapPercent",
            "process.pss_mib=${snapshot.processPssBytes / MEBIBYTE}",
            "thermal.band=${snapshot.thermalBand.name}",
            "thermal.headroom_10s=${snapshot.thermalHeadroomForecast?.toString() ?: "unknown"}",
            "thermal.headroom_moderate_threshold=${snapshot.thermalModerateHeadroomThreshold?.toString() ?: "unknown"}",
            "thermal.headroom_severe_threshold=${snapshot.thermalSevereHeadroomThreshold?.toString() ?: "unknown"}",
            "battery.percent=${snapshot.batteryPercent?.toString() ?: "unknown"}",
            "battery.charging=${snapshot.charging}",
            "battery.power_save=${snapshot.powerSaveMode}",
            "memory.pressure=${snapshot.memoryPressure.name}"
        ).joinToString(";").take(MAX_SUMMARY_CHARS)
    }

    companion object {
        const val HANDLER_ID = "system.resource_snapshot"
        const val PAYLOAD_V1 = "resource_snapshot.v1"

        private const val MEBIBYTE = 1024L * 1024L
        private const val MAX_SUMMARY_CHARS = 3_800
    }
}
