package com.kellvyn.assistant

/**
 * Read-only operational view of configured bot adapters.
 *
 * It reports app-level polling/heartbeat evidence, not a guarantee of 24/7 availability.
 * Credentials are never returned to the model and this module cannot start, stop or send messages.
 */
internal data class KellvynBotRuntimeProbe(
    val platform: String,
    val adapterImplemented: Boolean,
    val enabled: Boolean,
    val tokenConfigured: Boolean,
    val lastStatus: String?,
    val lastStatusAtMillis: Long?,
    val lastHeartbeatAtMillis: Long?
) {
    fun describe(nowMillis: Long): String {
        val status = when {
            !adapterImplemented -> "ADAPTADOR NÃO IMPLEMENTADO"
            !enabled -> "DESATIVADO"
            !tokenConfigured -> "ATIVADO, MAS SEM CREDENCIAL"
            lastHeartbeatAtMillis == null -> "SEM HEARTBEAT OBSERVADO"
            elapsed(nowMillis, lastHeartbeatAtMillis) > HEARTBEAT_STALE_AFTER_MILLIS ->
                "HEARTBEAT DESATUALIZADO"
            else -> "HEARTBEAT RECENTE"
        }
        val heartbeatAge = lastHeartbeatAtMillis?.let {
            "${elapsed(nowMillis, it) / 1_000}s atrás"
        } ?: "nunca registrado"
        val statusAge = lastStatusAtMillis?.let {
            "${elapsed(nowMillis, it) / 1_000}s atrás"
        } ?: "nunca registrado"
        val method = when (platform.lowercase()) {
            "telegram" -> "resposta bem-sucedida do long polling"
            "discord" -> "ACK de heartbeat do Gateway"
            else -> "sinal do adaptador"
        }
        return buildString {
            append(platform).append(": ").append(status).append("\n")
            append("  habilitado=").append(enabled)
                .append("; credencial configurada=").append(tokenConfigured).append("\n")
            append("  evidência mais recente (").append(method).append("): ")
                .append(heartbeatAge).append("\n")
            append("  último estado: ")
                .append(lastStatus?.take(120) ?: "sem estado registrado")
                .append(" (").append(statusAge).append(")")
        }
    }

    private fun elapsed(nowMillis: Long, timestampMillis: Long): Long =
        (nowMillis - timestampMillis).coerceAtLeast(0L)

    companion object {
        const val HEARTBEAT_STALE_AFTER_MILLIS = 90_000L
    }
}

internal class KellvynBotOperationsStudio(
    private val probeProvider: suspend () -> List<KellvynBotRuntimeProbe>,
    private val brainReplyReadyProvider: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis
) : KellvynStudioModule {
    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = TOOL_SPEC

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (arguments.isNotEmpty()) {
                    return KellvynToolResult(false, "O diagnóstico de bots não aceita argumentos.")
                }
                val probes = probeProvider()
                val output = buildString {
                    append("DIAGNÓSTICO DE BOTS KELLVYN — SOMENTE LEITURA\n")
                    append("IA de resposta local: ")
                        .append(if (brainReplyReadyProvider()) "PRONTA" else "NÃO PRONTA")
                        .append("\n\n")
                    probes.sortedBy { it.platform }.forEach { probe ->
                        append("\n").append(probe.describe(clock())).append("\n")
                    }
                    append("\nO sinal recente comprova apenas a última resposta/ACK observada pelo app. ")
                    append("Não garante disponibilidade contínua da plataforma nem execução futura em segundo plano. ")
                    append("Nenhum token foi exposto e nenhuma ação foi executada.")
                }
                return KellvynToolResult(true, output.take(MAX_OUTPUT_CHARS))
            }
        }
    )

    override fun trimTransientResources(level: KellvynCachePressure) = Unit
    override fun close() = Unit

    companion object {
        const val MODULE_ID = "bot-operations"
        const val HEALTH_TOOL = "bot_runtime_health"
        private const val MAX_OUTPUT_CHARS = 3_000
        private const val KIBIBYTE = 1_024L

        private val TOOL_SPEC = KellvynToolSpec(
            name = HEALTH_TOOL,
            description = "Consulta estado, credencial configurada e idade do último sinal de Telegram/Discord; não revela tokens, não envia mensagens nem altera bots.",
            risk = KellvynToolRisk.READ_ONLY,
            timeoutMs = 4_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedWorkingSetBytes = 32L * KIBIBYTE,
            capabilities = setOf(KellvynStudioCapability.WORKFLOW_AUTOMATION)
        )

        private val MANIFEST = KellvynStudioModuleManifest(
            moduleId = MODULE_ID,
            version = 1,
            capabilities = setOf(KellvynStudioCapability.WORKFLOW_AUTOMATION),
            supportedExtensions = emptySet(),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedLoadWorkingSetBytes = 32L * KIBIBYTE,
            estimatedResidentBytes = 8L * KIBIBYTE,
            permissions = emptySet(),
            toolSpecs = listOf(TOOL_SPEC)
        )

        fun definition(
            context: android.content.Context,
            brainReplyReadyProvider: () -> Boolean = { false }
        ): KellvynStudioModuleDefinition {
            val app = context.applicationContext
            return KellvynStudioModuleDefinition(MANIFEST) {
                KellvynBotOperationsStudio(
                    brainReplyReadyProvider = brainReplyReadyProvider,
                    probeProvider = {
                        val prefs = app.getSharedPreferences("kellvyn_native", android.content.Context.MODE_PRIVATE)
                        listOf(
                            KellvynBotRuntimeProbe(
                                platform = "Telegram",
                                adapterImplemented = true,
                                enabled = prefs.getBoolean("bot_enabled_telegram", false),
                                tokenConfigured = BotTokenStore.hasToken(app, "telegram"),
                                lastStatus = prefs.getString("bot_status_telegram", null),
                                lastStatusAtMillis = prefs.getLong("bot_status_updated_at_telegram", 0L)
                                    .takeIf { it > 0L },
                                lastHeartbeatAtMillis = prefs.getLong("bot_last_heartbeat_telegram", 0L)
                                    .takeIf { it > 0L }
                            ),
                            KellvynBotRuntimeProbe(
                                platform = "Discord",
                                adapterImplemented = true,
                                enabled = prefs.getBoolean("bot_enabled_discord", false),
                                tokenConfigured = BotTokenStore.hasToken(app, "discord"),
                                lastStatus = prefs.getString("bot_status_discord", null),
                                lastStatusAtMillis = prefs.getLong("bot_status_updated_at_discord", 0L)
                                    .takeIf { it > 0L },
                                lastHeartbeatAtMillis = prefs.getLong("bot_last_heartbeat_discord", 0L)
                                    .takeIf { it > 0L }
                            ),
                            KellvynBotRuntimeProbe(
                                platform = "WhatsApp",
                                adapterImplemented = false,
                                enabled = false,
                                tokenConfigured = false,
                                lastStatus = null,
                                lastStatusAtMillis = null,
                                lastHeartbeatAtMillis = null
                            )
                        )
                    }
                )
            }
        }
    }
}
