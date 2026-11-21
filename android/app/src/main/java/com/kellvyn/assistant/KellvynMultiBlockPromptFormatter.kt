package com.kellvyn.assistant

/**
 * Canonical input format shared by KN-004 training examples and live inference.
 * A training example must be formatted with the same system rules and tool catalogue
 * that the model will receive when the single-brain loop asks for its next decision.
 */
internal object KellvynMultiBlockPromptFormatter {
    fun build(
        history: List<KellvynAgentMessage>,
        availableTools: List<KellvynToolSpec>,
        maxMessageChars: Int = 900,
        maxToolDescriptionChars: Int = 100
    ): String {
        require(maxMessageChars in 128..8_000)
        require(maxToolDescriptionChars in 16..400)
        val systemMessages = history.filter { it.role == KellvynMessageRole.SYSTEM }
        val otherMessages = history.filter { it.role != KellvynMessageRole.SYSTEM }
        require(otherMessages.any { it.role == KellvynMessageRole.USER }) {
            "A formatação de entrada KN-004 exige uma mensagem do usuário."
        }
        val systemContext = systemMessages.joinToString("\n") { message ->
            val trustLabel = when (message.trust) {
                KellvynTrustLevel.TRUSTED -> "TRUSTED"
                KellvynTrustLevel.USER_PROVIDED -> "USER_DATA"
                KellvynTrustLevel.UNTRUSTED -> "UNTRUSTED_DATA"
            }
            "[SYSTEM $trustLabel origem=${message.source.take(80)}]\n" +
                boundedExcerpt(message.content, maxMessageChars)
        }
        val toolCatalog = renderTools(availableTools, maxToolDescriptionChars)
        return render(systemContext, toolCatalog, otherMessages, maxMessageChars)
    }

    fun buildTrainingPrompt(
        userPrompt: String,
        availableTools: List<KellvynToolSpec>,
        systemInstruction: String = KellvynAgentConfig().systemInstruction,
        maxMessageChars: Int = 900,
        maxToolDescriptionChars: Int = 80
    ): String {
        require(userPrompt.isNotBlank())
        val history = listOf(
            KellvynAgentMessage(
                role = KellvynMessageRole.SYSTEM,
                trust = KellvynTrustLevel.TRUSTED,
                source = "kellvyn-core",
                content = systemInstruction
            ),
            KellvynAgentMessage(
                role = KellvynMessageRole.USER,
                trust = KellvynTrustLevel.USER_PROVIDED,
                source = "user",
                content = userPrompt
            )
        )
        return build(history, availableTools, maxMessageChars, maxToolDescriptionChars)
    }

    private fun boundedExcerpt(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        val marker = "\n[conteúdo intermediário omitido para caber no contexto KN]\n"
        val usable = (maxChars - marker.length).coerceAtLeast(0)
        val head = (usable * 3) / 4
        val tail = usable - head
        return value.take(head) + marker + value.takeLast(tail)
    }

    private fun renderTools(
        tools: List<KellvynToolSpec>,
        maxToolDescriptionChars: Int
    ): String {
        if (tools.isEmpty()) return "(nenhuma)\n"
        return tools.sortedBy { it.name }.joinToString("\n") { spec ->
            val required = spec.requiredArguments.sorted().joinToString(",")
            val optional = spec.optionalArguments.sorted().joinToString(",")
            val schema = spec.argumentSchemas.joinToString(";") { argument ->
                buildString {
                    append(argument.name)
                    append(':')
                    append(argument.type.name.lowercase())
                    append(if (argument.required) "!" else "?")
                    append("[max=").append(argument.maxLength)
                    argument.pattern?.let {
                        append(",pattern=").append(it.take(120))
                    }
                    if (argument.type == KellvynToolArgumentType.ENUM) {
                        append(",values=").append(argument.allowedValues.sorted().joinToString("|"))
                    }
                    argument.minInteger?.let { append(",min=").append(it) }
                    argument.maxInteger?.let { append(",maxInt=").append(it) }
                    argument.minNumber?.let { append(",minNum=").append(it) }
                    argument.maxNumber?.let { append(",maxNum=").append(it) }
                    append(']')
                }
            }
            val capabilities = spec.capabilities
                .map { it.name.lowercase() }
                .sorted()
                .joinToString(",")
            "${spec.name} r=${spec.risk.name} workload=${spec.workloadClass.name} " +
                "cap=[$capabilities] req=[$required] opt=[$optional] " +
                "schema=[$schema] ${spec.description.take(maxToolDescriptionChars)}"
        } + "\n"
    }

    private fun render(
        systemContext: String,
        toolCatalog: String,
        retained: List<KellvynAgentMessage>,
        maxMessageChars: Int
    ): String = buildString {
        append("KELLVYN KN-004\n")
        append("Responda com UMA decisão KELLVYN/1 completa, termine com END e emita EOS.\n")
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
            append(boundedExcerpt(message.content, maxMessageChars))
            append("\n")
        }
        append("DECISÃO KELLVYN:\n")
    }
}
