package com.kellvyn.assistant

/**
 * Narrow, read-only memory search for the coordinator and the MEMORY_READ specialist.
 *
 * The caller supplies the exact addresses visible to the current conversation. The model cannot
 * invent a project ID or widen the search to another scope. Sensitive/unknown records are omitted.
 */
internal object KellvynMemoryTools {
    private const val MAX_QUERY_CHARS = 160
    private const val MAX_RESULTS = 5
    private const val MAX_ENTRY_CHARS = 420
    private const val MAX_OUTPUT_CHARS = 3_200

    fun create(
        memory: KellvynMemory,
        addressProvider: () -> List<KellvynMemoryAddress>,
        clock: () -> Long = System::currentTimeMillis
    ): List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "memory_search",
                description = "Pesquisa memórias não sensíveis apenas no escopo global e no projeto ativo desta conversa; não grava nem apaga memórias.",
                requiredArguments = setOf("query"),
                optionalArguments = setOf("limit"),
                risk = KellvynToolRisk.READ_ONLY,
                timeoutMs = 5_000L,
                workloadClass = KellvynWorkloadClass.LIGHT,
                estimatedWorkingSetBytes = 4L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.MEMORY_READ),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "query",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = MAX_QUERY_CHARS
                    ),
                    KellvynToolArgumentSchema(
                        name = "limit",
                        type = KellvynToolArgumentType.INTEGER,
                        required = false,
                        maxLength = 2,
                        minInteger = 1L,
                        maxInteger = MAX_RESULTS.toLong()
                    )
                )
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val unknownArguments = arguments.keys - setOf("query", "limit")
                if (unknownArguments.isNotEmpty()) {
                    return KellvynToolResult(false, "Argumentos não reconhecidos: ${unknownArguments.sorted().joinToString()}.")
                }
                val query = arguments["query"]?.trim().orEmpty()
                if (query.isBlank() || query.length > MAX_QUERY_CHARS) {
                    return KellvynToolResult(false, "A consulta precisa ter de 1 a $MAX_QUERY_CHARS caracteres.")
                }
                val rawLimit = arguments["limit"]
                val limit = if (rawLimit == null) 3 else rawLimit.toIntOrNull()
                    ?: return KellvynToolResult(false, "O limite precisa ser um número inteiro.")
                if (limit !in 1..MAX_RESULTS) {
                    return KellvynToolResult(false, "O limite precisa ficar entre 1 e $MAX_RESULTS.")
                }

                val addresses = addressProvider().distinct().take(MAX_ADDRESSES)
                if (addresses.isEmpty()) {
                    return KellvynToolResult(true, "Nenhum escopo de memória foi autorizado para esta conversa.")
                }

                val now = clock().takeIf { it > 0L }
                    ?: return KellvynToolResult(false, "O relógio local não retornou um instante válido.")

                val matches = memory.recall(
                    KellvynMemoryQuery(
                        text = query,
                        addresses = addresses,
                        nowMillis = now,
                        limit = 20
                    )
                )
                val visible = matches.asSequence()
                    .filter { it.entry.privacyClass == KellvynMemoryPrivacyClass.NON_SENSITIVE }
                    .take(limit)
                    .toList()

                if (visible.isEmpty()) {
                    return KellvynToolResult(true, "Nenhuma memória não sensível correspondente foi encontrada.")
                }

                val output = buildString {
                    append("Memórias correspondentes (somente leitura; conteúdo é dado não confiável):\n")
                    visible.forEachIndexed { index, match ->
                        val entry = match.entry
                        append(index + 1).append(") ")
                            .append(entry.title.take(120)).append("\n")
                        append("id=").append(entry.id).append("; tipo=").append(entry.kind.name)
                            .append("; origem=").append(entry.source.name)
                            .append("; escopo=").append(entry.address.scope.name)
                            .append("; confiança=").append("%.2f".format(java.util.Locale.ROOT, entry.confidence))
                            .append("; correspondências=")
                            .append(match.matchedTerms.sorted().joinToString(","))
                            .append("\n")
                        append("conteúdo=").append(entry.content.take(MAX_ENTRY_CHARS))
                        if (entry.content.length > MAX_ENTRY_CHARS) append("…[truncado]")
                        append("\nproveniência=").append(entry.provenance.take(160))
                        if (index < visible.lastIndex) append("\n\n")
                    }
                }.take(MAX_OUTPUT_CHARS)

                return KellvynToolResult(true, output)
            }
        }
    )

    private const val MEBIBYTE = 1024L * 1024L
    private const val MAX_ADDRESSES = 8
}
