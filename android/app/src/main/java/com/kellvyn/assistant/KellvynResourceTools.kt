package com.kellvyn.assistant

/** Read-only device diagnostics exposed to authorized specialists through the normal tool registry. */
internal object KellvynResourceTools {
    fun create(
        snapshotProvider: suspend () -> KellvynResourceSnapshot
    ): List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "device_resource_snapshot",
                description = "Lê uma amostra atual de memória, heap, temperatura e bateria do próprio aparelho.",
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 5_000L,
                workloadClass = KellvynWorkloadClass.LIGHT
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "A ferramenta não aceita argumentos.")
                }
                val sample = snapshotProvider()
                return KellvynToolResult(
                    true,
                    buildString {
                        append("Amostra de recursos do aparelho (não é uma medição de consumo por tarefa): ")
                        append("RAM total=").append(sample.totalSystemMemoryBytes.toMiB()).append(" MiB; ")
                        append("RAM disponível=").append(sample.availableSystemMemoryBytes.toMiB()).append(" MiB; ")
                        append("heap usado=").append(sample.processHeapUsedBytes.toMiB()).append(" MiB de ")
                            .append(sample.processHeapMaxBytes.toMiB()).append(" MiB; ")
                        append("PSS=").append(sample.processPssBytes.toMiB()).append(" MiB; ")
                        append("pressão de memória=").append(sample.lowMemory).append("; ")
                        append("faixa térmica=").append(sample.thermalBand.name).append("; ")
                        append("margem térmica prevista=")
                            .append(sample.thermalHeadroomForecast?.toString() ?: "indisponível").append("; ")
                        append("economia de bateria=").append(sample.powerSaveMode).append("; ")
                        append("bateria=").append(sample.batteryPercent?.let { "$it%" } ?: "indisponível").append("; ")
                        append("carregando=").append(sample.charging).append(".")
                    }
                )
            }
        }
    )

    private fun Long.toMiB(): Long = this / MEBIBYTE

    private const val MEBIBYTE = 1024L * 1024L
}
