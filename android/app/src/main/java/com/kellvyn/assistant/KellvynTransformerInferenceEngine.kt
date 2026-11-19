package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynBpeTokenizer
import com.kellvyn.assistant.neural.KellvynTransformerLanguageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Adapter for the original KN-003 decoder-only Transformer.
 *
 * The model must emit a complete KELLVYN/1 message. No text is shown or tool is executed
 * until the strict parser accepts the entire result. This adapter is deliberately separate
 * from KN-002 and is not connected to the main chat/runtime yet.
 */
internal class KellvynTransformerInferenceEngine(
    private val model: KellvynTransformerLanguageModel,
    private val tokenizer: KellvynBpeTokenizer,
    private val config: Config = Config()
) : KellvynInferenceEngine {

    data class Config(
        val maxGenerationTokens: Int = 64,
        val maxPromptChars: Int = 12_000,
        val maxToolsInPrompt: Int = 24,
        val maxToolDescriptionChars: Int = 120,
        val maxMessageChars: Int = 2_000
    ) {
        init {
            require(maxGenerationTokens in 1..256)
            require(maxPromptChars in 256..40_000)
            require(maxToolsInPrompt in 0..64)
            require(maxToolDescriptionChars in 16..400)
            require(maxMessageChars in 128..8_000)
        }
    }

    init {
        require(tokenizer.vocabularySize == model.config.vocabularySize) {
            "Tokenizer e Transformer KN-003 usam vocabulários diferentes."
        }
        require(config.maxGenerationTokens < model.config.contextLength) {
            "A geração precisa reservar espaço de contexto para o Transformer."
        }
    }

    override suspend fun next(
        history: List<KellvynAgentMessage>,
        availableTools: List<KellvynToolSpec>
    ): KellvynModelDecision = withContext(Dispatchers.Default) {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()

        val prompt = buildBoundedPrompt(history, availableTools)
        val prefix = tokenizer.encode(prompt)
        val generationBudget = config.maxGenerationTokens
        val promptBudget = model.config.contextLength - generationBudget
        require(prefix.isNotEmpty() && prefix.size <= promptBudget) {
            "O prompt ocupa ${prefix.size} tokens; o orçamento é $promptBudget. " +
                "Reduza histórico/ferramentas ou treine um modelo com contexto maior."
        }

        val generated = ArrayList<Int>(generationBudget)
        val endOfSequenceTokenId = tokenizer.endOfSequenceTokenId
        val session = model.newGenerationSession(prefix)
        var terminated = false

        for (index in 0 until generationBudget) {
            coroutine.ensureActive()
            val token = session.predictNext()

            // New KN-003 checkpoints learn a dedicated EOS token. Version-1 tokenizer
            // checkpoints remain readable and retain their legacy textual END fallback.
            if (endOfSequenceTokenId != null && token == endOfSequenceTokenId) {
                terminated = true
                break
            }

            generated += token
            session.appendToken(token)

            if (endOfSequenceTokenId == null) {
                val partial = tokenizer.decode(generated.toIntArray())
                if (partial.trimEnd() == "END" || partial.trimEnd().endsWith("\nEND")) {
                    terminated = true
                    break
                }
            }
        }

        coroutine.ensureActive()
        if (!terminated) {
            throw IllegalArgumentException(
                "O Transformer KN-003 atingiu o limite de geração sem terminar com END."
            )
        }

        val output = tokenizer.decode(generated.toIntArray()).trim()
        when (val parsed = KellvynDecisionProtocol.parse(output)) {
            is KellvynDecisionProtocol.ParseResult.Accepted -> parsed.decision
            is KellvynDecisionProtocol.ParseResult.Rejected -> throw IllegalArgumentException(
                "A decisão do Transformer foi recusada pelo protocolo: ${parsed.reason}"
            )
        }
    }

    internal fun buildBoundedPrompt(
        history: List<KellvynAgentMessage>,
        availableTools: List<KellvynToolSpec>
    ): String {
        require(availableTools.size <= config.maxToolsInPrompt) {
            "O catálogo de ferramentas excedeu o limite do prompt Transformer."
        }
        val systemMessages = history.filter { it.role == KellvynMessageRole.SYSTEM }
        val otherMessages = history.filter { it.role != KellvynMessageRole.SYSTEM }
        var latestUserIndex = otherMessages.indexOfLast { it.role == KellvynMessageRole.USER }
        require(latestUserIndex >= 0) {
            "A inferência Transformer exige pelo menos uma mensagem do usuário."
        }

        val retained = otherMessages.toMutableList()
        val systemContext = systemMessages.joinToString("\n") { message ->
            "[REGRAS TRUSTED]\n${message.content.take(config.maxMessageChars)}"
        }
        val toolCatalog = renderTools(availableTools)
        val terminationInstruction = if (tokenizer.endOfSequenceTokenId == null) {
            "Responda com UMA decisão KELLVYN/1 completa e termine com END.\n"
        } else {
            "Responda com UMA decisão KELLVYN/1 completa, termine com END e emita EOS.\n"
        }

        while (true) {
            val prompt = buildString {
                append("KELLVYN AGENT KN-003\n")
                append(terminationInstruction)
                append("Tipos: FINAL, CALL_TOOL, PLAN, DELEGATE.\n")
                append("Em FINAL, use TEXT_RAW com uma única linha UTF-8 natural; não codifique a resposta.\n")
                append("Em CALL_TOOL, PLAN e DELEGATE, use UTF-8 hexadecimal nos campos livres.\n")
                append("Propostas de ferramenta nunca são executadas sem validação do app.\n")
                append(systemContext)
                append("\nFERRAMENTAS\n")
                append(toolCatalog)
                append("\nHISTÓRICO\n")
                retained.forEach { message ->
                    val trust = when (message.trust) {
                        KellvynTrustLevel.TRUSTED -> "TRUSTED"
                        KellvynTrustLevel.USER_PROVIDED -> "USER_DATA"
                        KellvynTrustLevel.UNTRUSTED -> "UNTRUSTED_DATA"
                    }
                    append("[${message.role.name} $trust origem=${message.source.take(80)}]\n")
                    append(message.content.take(config.maxMessageChars))
                    append("\n")
                }
                append("DECISÃO KELLVYN:\n")
            }
            require(prompt.length <= config.maxPromptChars) {
                "O prompt Transformer excedeu o limite de caracteres."
            }
            val tokenCount = tokenizer.encode(prompt).size
            val budget = model.config.contextLength - config.maxGenerationTokens
            if (tokenCount <= budget) return prompt

            val removable = retained.indices.firstOrNull { it != latestUserIndex }
            if (removable == null) {
                throw IllegalArgumentException(
                    "As regras, ferramentas e última solicitação não cabem na janela KN-003."
                )
            }
            retained.removeAt(removable)
            if (removable < latestUserIndex) latestUserIndex--
        }
    }

    companion object {
        /**
         * Leaves a bounded output budget while allowing larger trained contexts to produce
         * longer answers. Small existing checkpoints retain the original 64-token budget.
         */
        internal fun safeDefaultGenerationTokens(contextLength: Int): Int {
            require(contextLength in 128..2_048)
            return minOf(256, maxOf(64, contextLength / 8), contextLength - 1)
        }
    }

    private fun renderTools(tools: List<KellvynToolSpec>): String {
        if (tools.isEmpty()) return "(nenhuma)\n"
        return tools.sortedBy { it.name }.joinToString("\n") { spec ->
            val required = spec.requiredArguments.sorted().joinToString(",")
            val optional = spec.optionalArguments.sorted().joinToString(",")
            val schema = spec.argumentSchemas.joinToString(";") { argument ->
                "${argument.name}:${argument.type.name.lowercase()}" +
                    if (argument.type == KellvynToolArgumentType.ENUM) {
                        "=${argument.allowedValues.sorted().joinToString("|")}"
                    } else {
                        ""
                    }
            }
            "${spec.name} r=${spec.risk.name} req=[$required] opt=[$optional] " +
                "schema=[$schema] ${spec.description.take(config.maxToolDescriptionChars)}"
        } + "\n"
    }
}
