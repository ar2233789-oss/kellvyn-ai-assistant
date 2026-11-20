package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynBpeTokenizer
import com.kellvyn.assistant.neural.KellvynMultiBlockTransformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Strict KELLVYN/1 inference adapter for the original KN-004 multi-block model.
 * This adapter does not enable the main chat or provide a fallback model.
 */
internal class KellvynMultiBlockInferenceEngine(
    private val model: KellvynMultiBlockTransformer,
    private val tokenizer: KellvynBpeTokenizer,
    private val config: Config = Config()
) : KellvynInferenceEngine {

    data class Config(
        val maxGenerationTokens: Int = 64,
        val maxPromptChars: Int = 12_000,
        val maxToolsInPrompt: Int = 6,
        val maxToolDescriptionChars: Int = 80,
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
            "Tokenizer e Transformer KN-004 usam vocabulários diferentes."
        }
        require(config.maxGenerationTokens < model.config.contextLength) {
            "A geração precisa reservar espaço de contexto para o Transformer KN-004."
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
        val promptBudget = model.config.contextLength - config.maxGenerationTokens
        require(prefix.isNotEmpty() && prefix.size <= promptBudget) {
            "O prompt KN-004 ocupa ${prefix.size} tokens; o orçamento é $promptBudget."
        }

        val eos = requireNotNull(tokenizer.endOfSequenceTokenId) {
            "O tokenizer KN-004 não possui token EOS dedicado."
        }
        val session = model.newGenerationSession(prefix)
        val generated = ArrayList<Int>(config.maxGenerationTokens)
        var terminated = false
        for (index in 0 until config.maxGenerationTokens) {
            coroutine.ensureActive()
            val token = session.predictNext()
            if (token == eos) {
                terminated = true
                break
            }
            generated += token
            session.appendToken(token)
        }
        coroutine.ensureActive()
        if (!terminated) {
            throw IllegalArgumentException(
                "O Transformer KN-004 atingiu o limite de geração sem emitir EOS."
            )
        }

        val output = tokenizer.decode(generated.toIntArray()).trim()
        when (val parsed = KellvynDecisionProtocol.parse(output)) {
            is KellvynDecisionProtocol.ParseResult.Accepted -> {
                require(parsed.decision !is KellvynModelDecision.Delegate) {
                    "O KN central não pode delegar decisões a outras inteligências."
                }
                parsed.decision
            }
            is KellvynDecisionProtocol.ParseResult.Rejected -> throw IllegalArgumentException(
                "A decisão do Transformer KN-004 foi recusada: ${parsed.reason}"
            )
        }
    }

    internal fun buildBoundedPrompt(
        history: List<KellvynAgentMessage>,
        availableTools: List<KellvynToolSpec>
    ): String {
        require(availableTools.size <= config.maxToolsInPrompt) {
            "O catálogo de ferramentas excedeu o limite do prompt KN-004."
        }
        val systemMessages = history.filter { it.role == KellvynMessageRole.SYSTEM }
        val otherMessages = history.filter { it.role != KellvynMessageRole.SYSTEM }
        var latestUserIndex = otherMessages.indexOfLast {
            it.role == KellvynMessageRole.USER
        }
        require(latestUserIndex >= 0) {
            "A inferência KN-004 exige pelo menos uma mensagem do usuário."
        }

        val retained = otherMessages.toMutableList()
        val systemContext = systemMessages.joinToString("\n") { message ->
            val trustLabel = when (message.trust) {
                KellvynTrustLevel.TRUSTED -> "TRUSTED"
                KellvynTrustLevel.USER_PROVIDED -> "USER_DATA"
                KellvynTrustLevel.UNTRUSTED -> "UNTRUSTED_DATA"
            }
            "[SYSTEM $trustLabel origem=${message.source.take(80)}]\n" +
                message.content.take(config.maxMessageChars)
        }
        val toolCatalog = renderTools(availableTools)
        val terminationInstruction =
            "Responda com UMA decisão KELLVYN/1 completa, termine com END e emita EOS.\n"

        while (true) {
            val prompt = buildString {
                append("KELLVYN AGENT KN-004\n")
                append(terminationInstruction)
                append("Tipos: FINAL, CALL_TOOL, PLAN. Não delegue.\n")
                append("Todo texto livre deve ser UTF-8 em hexadecimal. Sem Markdown.\n")
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
                "O prompt KN-004 excedeu o limite de caracteres."
            }
            val tokenCount = tokenizer.encode(prompt).size
            val budget = model.config.contextLength - config.maxGenerationTokens
            if (tokenCount <= budget) return prompt

            val removable = retained.indices.firstOrNull { it != latestUserIndex }
            if (removable == null) {
                throw IllegalArgumentException(
                    "As regras, ferramentas e última solicitação não cabem na janela KN-004."
                )
            }
            retained.removeAt(removable)
            if (removable < latestUserIndex) latestUserIndex--
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
