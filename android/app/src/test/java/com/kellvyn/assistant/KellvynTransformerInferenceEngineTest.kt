package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynBpeTokenizer
import com.kellvyn.assistant.neural.KellvynTransformerLanguageModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynTransformerInferenceEngineTest {
    @Test
    fun boundedPromptPreservesRulesAndLatestUserWhileDroppingOldHistory() {
        val repeatedTemplate = (
            "KELLVYN AGENT KBN-003\n" +
                "Responda com UMA decisão KELLVYN/1 completa e termine com END.\n" +
                "Tipos: FINAL, CALL_TOOL, PLAN, DELEGATE.\n" +
                "Todo texto livre deve ser UTF-8 em hexadecimal. Sem Markdown.\n" +
                "Propostas de ferramenta nunca são executadas sem validação do app.\n" +
                "FERRAMENTAS (nenhuma)\nHISTÓRICO\nDECISÃO KELLVYN:\n"
            ).repeat(4)
        val tokenizer = KellvynBpeTokenizer.train(
            corpus = listOf(repeatedTemplate),
            maxMerges = 256
        )
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 256,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 6101L
        )
        val engine = KellvynTransformerInferenceEngine(
            model,
            tokenizer,
            KellvynTransformerInferenceEngine.Config(
                maxGenerationTokens = 32,
                maxPromptChars = 12_000,
                maxMessageChars = 1_000
            )
        )
        val history = listOf(
            KellvynAgentMessage(
                KellvynMessageRole.SYSTEM,
                KellvynTrustLevel.TRUSTED,
                "system",
                "REGRA_PRINCIPAL_SENTINEL"
            ),
            KellvynAgentMessage(
                KellvynMessageRole.USER,
                KellvynTrustLevel.USER_PROVIDED,
                "old-user",
                "HISTORICO_ANTIGO_SENTINEL ".repeat(120)
            ),
            KellvynAgentMessage(
                KellvynMessageRole.ASSISTANT,
                KellvynTrustLevel.TRUSTED,
                "assistant",
                "RESPOSTA_ANTIGA_SENTINEL ".repeat(80)
            ),
            KellvynAgentMessage(
                KellvynMessageRole.USER,
                KellvynTrustLevel.USER_PROVIDED,
                "latest-user",
                "ULTIMA_SOLICITACAO_SENTINEL"
            )
        )

        val prompt = engine.buildBoundedPrompt(history, emptyList())

        assertTrue(prompt.contains("REGRA_PRINCIPAL_SENTINEL"))
        assertTrue(prompt.contains("ULTIMA_SOLICITACAO_SENTINEL"))
        assertFalse(prompt.contains("HISTORICO_ANTIGO_SENTINEL"))
        assertFalse(prompt.contains("RESPOSTA_ANTIGA_SENTINEL"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHistoryWhenLatestUserCannotFitContextWindow() {
        val tokenizer = KellvynBpeTokenizer.train(listOf("KELLVYN AGENT"), maxMerges = 0)
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 256,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 6102L
        )
        val engine = KellvynTransformerInferenceEngine(
            model,
            tokenizer,
            KellvynTransformerInferenceEngine.Config(
                maxGenerationTokens = 32,
                maxPromptChars = 12_000,
                maxMessageChars = 8_000
            )
        )
        engine.buildBoundedPrompt(
            listOf(
                KellvynAgentMessage(
                    KellvynMessageRole.USER,
                    KellvynTrustLevel.USER_PROVIDED,
                    "latest",
                    "X".repeat(1_000)
                )
            ),
            emptyList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun randomUntrainedTransformerCannotProduceAnAcceptedAgentDecision() = runBlocking {
        val tokenizer = KellvynBpeTokenizer.train(
            listOf("KELLVYN AGENT KBN-003 FINAL TEXT END"),
            maxMerges = 128
        )
        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = 256,
            embeddingSize = 8,
            headCount = 2,
            feedForwardSize = 16,
            seed = 6103L
        )
        val engine = KellvynTransformerInferenceEngine(
            model,
            tokenizer,
            KellvynTransformerInferenceEngine.Config(
                maxGenerationTokens = 64,
                maxPromptChars = 12_000,
                maxMessageChars = 500
            )
        )
        engine.next(
            history = listOf(
                KellvynAgentMessage(
                    KellvynMessageRole.USER,
                    KellvynTrustLevel.USER_PROVIDED,
                    "user",
                    "Responda apenas olá."
                )
            ),
            availableTools = emptyList()
        )
    }
}
