package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynAttentionLanguageModel
import com.kellvyn.assistant.neural.KellvynBpeTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Adapts the original local KBN-002 model to the typed agent decision contract.
 *
 * Decoding is greedy and CPU-only. The model sees a bounded prompt and must emit the strict
 * KellvynDecisionProtocol; malformed/free-form output fails closed. This adapter does not
 * enable the application chat by itself and does not claim that random/undertrained weights
 * can follow instructions.
 */
internal class KellvynLocalNeuralInferenceEngine(
    private val model: KellvynAttentionLanguageModel,
    private val tokenizer: KellvynBpeTokenizer,
    private val config: Config = Config()
) : KellvynInferenceEngine {

    data class Config(
        val maxGenerationTokens: Int = 32,
        val maxPromptChars: Int = 24_000,
        val maxToolsInPrompt: Int = 24,
        val maxToolDescriptionChars: Int = 160,
        val maxMessageChars: Int = 8_000
    ) {
        init {
            require(maxGenerationTokens in 1..256)
            require(maxPromptChars in 256..100_000)
            require(maxToolsInPrompt in 0..64)
            require(maxToolDescriptionChars in 16..500)
            require(maxMessageChars in 128..16_000)
        }
    }

    init {
        require(tokenizer.vocabularySize == model.config.vocabularySize) {
            "Tokenizer e modelo Kellvyn usam vocabulários diferentes."
        }
        require(config.maxGenerationTokens < model.config.contextLength) {
            "A geração precisa reservar espaço para o contexto do modelo."
        }
    }

    override suspend fun next(
        history: List<KellvynAgentMessage>,
        availableTools: List<KellvynToolSpec>
    ): KellvynModelDecision = withContext(Dispatchers.Default) {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()

        val prompt = buildBoundedPrompt(history, availableTools)
        val prefixTokens = tokenizer.encode(prompt)
        val maxPromptTokens = model.config.contextLength - config.maxGenerationTokens
        require(prefixTokens.isNotEmpty() && prefixTokens.size <= maxPromptTokens) {
            "O prompt ocupa ${prefixTokens.size} tokens, mas o orçamento é $maxPromptTokens. " +
                "Reduza contexto/ferramentas ou use um modelo com janela maior."
        }

        val generated = ArrayList<Int>(config.maxGenerationTokens)
        val complete = prefixTokens.toMutableList()
        var stopped = false
        for (generationIndex in 0 until config.maxGenerationTokens) {
            coroutine.ensureActive()
            val context = complete.takeLast(model.config.contextLength).toIntArray()
            val nextToken = model.predictNext(context)
            complete += nextToken
            generated += nextToken

            // The protocol terminator is printable ASCII; stop as soon as END appears.
            val partial = tokenizer.decode(generated.toIntArray())
            if (partial.trimEnd().endsWith("\nEND") || partial.trimEnd() == "END") {
                stopped = true
                break
            }
        }
        coroutine.ensureActive()
        coroutine.ensureActive()

        val rawOutput = tokenizer.decode(generated.toIntArray()).trim()
        if (!stopped) {
            throw IllegalArgumentException(
                "O modelo atingiu o limite de geração sem encerrar o protocolo com END."
            )
        }
        when (val parsed = KellvynDecisionProtocol.parse(rawOutput)) {
            is KellvynDecisionProtocol.ParseResult.Accepted -> parsed.decision
            is KellvynDecisionProtocol.ParseResult.Rejected -> throw IllegalArgumentException(
                "A decisão neural foi recusada pelo protocolo: ${parsed.reason}"
            )
        }
    }

    internal fun buildBoundedPrompt(
        history: List<KellvynAgentMessage>,
        availableTools: List<KellvynToolSpec>
    ): String {
        require(availableTools.size <= config.maxToolsInPrompt) {
            "O catálogo de ferramentas excedeu o limite do prompt neural."
        }
        val systems = history.filter { it.role == KellvynMessageRole.SYSTEM }
        val otherMessages = history.filter { it.role != KellvynMessageRole.SYSTEM }
        val latestUserIndex = otherMessages.indexOfLast { it.role == KellvynMessageRole.USER }
        val retained = otherMessages.toMutableList()
        val toolCatalog = renderTools(availableTools)
        val systemContext = systems.joinToString("\n") { message ->
            "[SYSTEM TRUSTED]\n${message.content.take(config.maxMessageChars)}"
        }

        while (true) {
            val prompt = buildString {
                append("KELLVYN LOCAL NEURAL AGENT\n")
                append("OUTPUT CONTRACT: emit exactly one KELLVYN/1 decision ending with END.\n")
                append("Allowed decisions: FINAL, CALL_TOOL, PLAN, DELEGATE.\n")
                append("All free text fields must be UTF-8 hexadecimal. Never add Markdown or commentary.\n")
                append("Tool calls are proposals only; the application validates names, arguments and permissions.\n")
                append(systemContext)
                append("\nTOOL CATALOG\n")
                append(toolCatalog)
                append("\nCONVERSATION\n")
                retained.forEach { message ->
                    val trustLabel = when (message.trust) {
                        KellvynTrustLevel.TRUSTED -> "TRUSTED"
                        KellvynTrustLevel.USER_PROVIDED -> "USER_DATA"
                        KellvynTrustLevel.UNTRUSTED -> "UNTRUSTED_DATA"
                    }
                    append("[${message.role.name} $trustLabel source=${message.source.take(120)} chars=${message.content.length}]\n")
                    append(message.content.take(config.maxMessageChars))
                    append("\n")
                }
                append("ASSISTANT DECISION\n")
            }
            require(prompt.length <= config.maxPromptChars) {
                "O prompt de inferência excedeu o limite de caracteres."
            }
            val tokenCount = tokenizer.encode(prompt).size
            val tokenBudget = model.config.contextLength - config.maxGenerationTokens
            if (tokenCount <= tokenBudget) return prompt

            val removableIndex = retained.indices.firstOrNull { index ->
                index != latestUserIndex
            }
            if (removableIndex == null) {
                throw IllegalArgumentException(
                    "As regras, o catálogo de ferramentas e a última solicitação não cabem na janela neural."
                )
            }
            retained.removeAt(removableIndex)
            if (removableIndex < latestUserIndex) latestUserIndex--
        }
    }

    private fun renderTools(tools: List<KellvynToolSpec>): String {
        if (tools.isEmpty()) return "(nenhuma ferramenta disponível)\n"
        return tools.sortedBy { it.name }.joinToString("\n") { spec ->
            val required = spec.requiredArguments.sorted().joinToString(",")
            val optional = spec.optionalArguments.sorted().joinToString(",")
            val schema = spec.argumentSchemas.joinToString(";") { item ->
                "${item.name}:${item.type.name.lowercase()}" +
                    if (item.type == KellvynToolArgumentType.ENUM) {
                        "=${item.allowedValues.sorted().joinToString("|")}"
                    } else {
                        ""
                    }
            }
            "NAME=${spec.name}; RISK=${spec.risk.name}; REQUIRED=[$required]; " +
                "OPTIONAL=[$optional]; SCHEMA=[$schema]; " +
                "DESCRIPTION=${spec.description.take(config.maxToolDescriptionChars)}"
        } + "\n"
    }
}
