package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynBpeTokenizer
import com.kellvyn.assistant.neural.KellvynMultiBlockTransformer
import kotlinx.coroutines.runBlocking
import org.junit.Test

class KellvynMultiBlockInferenceEngineTest {
    @Test(expected = IllegalArgumentException::class)
    fun refusesToSilentlyDropToolsFromThePresentedDecisionCatalog() = runBlocking {
        val tokenizer = KellvynBpeTokenizer.train(
            corpus = listOf("KELLVYN KN-004 local_lookup hidden_export"),
            maxMerges = 8
        )
        val model = KellvynMultiBlockTransformer.create(
            KellvynMultiBlockTransformer.Config(
                vocabularySize = tokenizer.vocabularySize,
                contextLength = 256,
                embeddingSize = 8,
                headCount = 2,
                feedForwardSize = 16,
                depth = 1,
                seed = 9021L
            )
        )
        val engine = KellvynMultiBlockInferenceEngine(
            model = model,
            tokenizer = tokenizer,
            config = KellvynMultiBlockInferenceEngine.Config(
                maxGenerationTokens = 32,
                maxToolsInPrompt = 1
            )
        )
        val tools = listOf(
            KellvynToolSpec(
                name = "local_lookup",
                description = "Consulta registros locais."
            ),
            KellvynToolSpec(
                name = "hidden_export",
                description = "Exporta documentos."
            )
        )

        engine.next(
            history = listOf(
                KellvynAgentMessage(
                    role = KellvynMessageRole.USER,
                    trust = KellvynTrustLevel.USER_PROVIDED,
                    source = "user",
                    content = "Pesquise o registro local KN."
                )
            ),
            availableTools = tools
        )
    }
}
