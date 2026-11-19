package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynContextAssemblerTest {
    @Test
    fun systemRulesAndLatestUserRequestAreAlwaysPreserved() {
        val messages = listOf(
            message(KellvynMessageRole.SYSTEM, "Regras que nunca podem ser removidas."),
            message(KellvynMessageRole.USER, "Pedido antigo".repeat(80)),
            message(KellvynMessageRole.TOOL, "Resultado antigo".repeat(80)),
            message(KellvynMessageRole.USER, "Pedido atual.")
        )

        val result = KellvynContextAssembler.fit(messages, maxChars = 400)

        assertTrue(result is KellvynContextAssembler.Result.Ready)
        val ready = result as KellvynContextAssembler.Result.Ready
        assertEquals("Regras que nunca podem ser removidas.", ready.messages.first().content)
        assertTrue(ready.messages.any { it.content == "Pedido atual." })
        assertFalse(ready.messages.any { it.content.startsWith("Pedido antigo") })
        assertTrue(ready.droppedMessages > 0)
    }

    @Test
    fun selectedMessagesKeepTheirOriginalChronologicalOrder() {
        val messages = listOf(
            message(KellvynMessageRole.SYSTEM, "rules"),
            message(KellvynMessageRole.USER, "first"),
            message(KellvynMessageRole.TOOL, "tool result"),
            message(KellvynMessageRole.ASSISTANT, "thought"),
            message(KellvynMessageRole.USER, "latest")
        )

        val result = KellvynContextAssembler.fit(messages, maxChars = 1_000)
            as KellvynContextAssembler.Result.Ready

        assertEquals(listOf("rules", "first", "tool result", "thought", "latest"), result.messages.map { it.content })
    }

    @Test
    fun oversizedRecentMessageDoesNotCauseOlderContextToBeReinserted() {
        val messages = listOf(
            message(KellvynMessageRole.SYSTEM, "rules"),
            message(KellvynMessageRole.USER, "old"),
            message(KellvynMessageRole.TOOL, "large recent tool result".repeat(20)),
            message(KellvynMessageRole.USER, "latest")
        )

        val result = KellvynContextAssembler.fit(messages, maxChars = 120)

        assertTrue(result is KellvynContextAssembler.Result.Ready)
        val ready = result as KellvynContextAssembler.Result.Ready
        assertTrue(ready.messages.any { it.content == "rules" })
        assertTrue(ready.messages.any { it.content == "latest" })
        assertFalse(ready.messages.any { it.content == "old" })
        assertFalse(ready.messages.any { it.content.startsWith("large recent tool result") })
    }

    @Test
    fun essentialInstructionsCannotBeSilentlyTruncated() {
        val messages = listOf(
            message(KellvynMessageRole.SYSTEM, "S".repeat(1_000)),
            message(KellvynMessageRole.USER, "Faça a tarefa.")
        )

        val result = KellvynContextAssembler.fit(messages, maxChars = 512)

        assertTrue(result is KellvynContextAssembler.Result.TooLarge)
        val tooLarge = result as KellvynContextAssembler.Result.TooLarge
        assertTrue(tooLarge.requiredChars > tooLarge.budgetChars)
    }

    @Test
    fun trustAndSourceMetadataArePreserved() {
        val original = KellvynAgentMessage(
            role = KellvynMessageRole.TOOL,
            trust = KellvynTrustLevel.UNTRUSTED,
            source = "memory:decision-2",
            content = "Conteúdo recuperado."
        )

        val result = KellvynContextAssembler.fit(
            listOf(
                message(KellvynMessageRole.SYSTEM, "rules"),
                original,
                message(KellvynMessageRole.USER, "Considere o registro.")
            ),
            maxChars = 2_000
        ) as KellvynContextAssembler.Result.Ready

        assertTrue(result.messages.contains(original))
    }

    @Test(expected = IllegalArgumentException::class)
    fun budgetMustStayWithinConfiguredLimits() {
        KellvynContextAssembler.fit(emptyList(), maxChars = 100)
    }

    private fun message(role: KellvynMessageRole, content: String) =
        KellvynAgentMessage(
            role = role,
            trust = if (role == KellvynMessageRole.SYSTEM) {
                KellvynTrustLevel.TRUSTED
            } else {
                KellvynTrustLevel.UNTRUSTED
            },
            source = role.name.lowercase(),
            content = content
        )
}
