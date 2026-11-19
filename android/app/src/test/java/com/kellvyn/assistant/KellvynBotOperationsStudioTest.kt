package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynBotOperationsStudioTest {
    @Test
    fun reportsRecentAndStaleBotSignalsWithoutExposingCredentials() = runBlocking {
        val now = 1_000_000L
        val studio = KellvynBotOperationsStudio(
            probeProvider = {
                listOf(
                    KellvynBotRuntimeProbe(
                        platform = "Telegram",
                        adapterImplemented = true,
                        enabled = true,
                        tokenConfigured = true,
                        lastStatus = "Conectado",
                        lastStatusAtMillis = now - 10_000L,
                        lastHeartbeatAtMillis = now - 20_000L
                    ),
                    KellvynBotRuntimeProbe(
                        platform = "Discord",
                        adapterImplemented = true,
                        enabled = true,
                        tokenConfigured = true,
                        lastStatus = "Conectado",
                        lastStatusAtMillis = now - 200_000L,
                        lastHeartbeatAtMillis = now - 200_000L
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
            },
            clock = { now }
        )

        val result = studio.tools.single().execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("IA de resposta local: NÃO PRONTA"))
        assertTrue(result.content.contains("Telegram: HEARTBEAT RECENTE"))
        assertTrue(result.content.contains("Discord: HEARTBEAT DESATUALIZADO"))
        assertTrue(result.content.contains("WhatsApp: ADAPTADOR NÃO IMPLEMENTADO"))
        assertTrue(result.content.contains("nenhum token foi exposto".replaceFirstChar { it.uppercase() }))
        assertFalse(result.content.contains("token-secret"))
    }

    @Test
    fun rejectsArgumentsAndDoesNotClaimContinuousAvailability() = runBlocking {
        val studio = KellvynBotOperationsStudio(
            probeProvider = { emptyList() },
            clock = { 1_000_000L }
        )

        val invalid = studio.tools.single().execute(mapOf("token" to "must-not-be-read"))
        val empty = studio.tools.single().execute(emptyMap())

        assertFalse(invalid.success)
        assertTrue(empty.success)
        assertTrue(empty.content.contains("Não garante disponibilidade contínua"))
        assertTrue(empty.content.contains("nenhuma ação foi executada"))
    }
}
