package com.kellvyn.assistant

/**
 * Metadata-only controls for the shared artifact store. Binary payloads remain in private
 * storage and are passed between trusted engines through the store API, never model context.
 */
internal object KellvynArtifactTools {
    fun create(store: KellvynArtifactStore): List<KellvynAgentTool> {
        val statusSpec = KellvynToolSpec(
            name = "artifact_store_status",
            description = "Consulta contagem, espaço usado e limites do armazenamento privado de artefatos.",
            risk = KellvynToolRisk.READ_ONLY,
            timeoutMs = 5_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedWorkingSetBytes = 64L * 1024L,
            capabilities = setOf(KellvynStudioCapability.ARTIFACT_STORAGE)
        )
        val listSpec = KellvynToolSpec(
            name = "artifact_list_task",
            description = "Lista metadados dos artefatos vinculados a uma tarefa; não lê os arquivos binários.",
            requiredArguments = setOf("task_id"),
            risk = KellvynToolRisk.READ_ONLY,
            timeoutMs = 5_000L,
            workloadClass = KellvynWorkloadClass.LIGHT,
            estimatedWorkingSetBytes = 128L * 1024L,
            capabilities = setOf(KellvynStudioCapability.ARTIFACT_STORAGE),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "task_id",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = 96,
                    pattern = "^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$"
                )
            )
        )
        val verifySpec = KellvynToolSpec(
            name = "artifact_verify",
            description = "Verifica tamanho e SHA-256 de um artefato e retorna seus metadados sem expor o conteúdo.",
            requiredArguments = setOf("artifact_id"),
            risk = KellvynToolRisk.READ_ONLY,
            timeoutMs = 15_000L,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedWorkingSetBytes = 128L * 1024L,
            capabilities = setOf(KellvynStudioCapability.ARTIFACT_STORAGE),
            argumentSchemas = listOf(
                KellvynToolArgumentSchema(
                    name = "artifact_id",
                    type = KellvynToolArgumentType.TEXT,
                    maxLength = 64,
                    pattern = "^[a-f0-9]{64}$"
                )
            )
        )

        return listOf(
            object : KellvynAgentTool {
                override val spec = statusSpec

                override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                    if (arguments.isNotEmpty()) {
                        return KellvynToolResult(false, "A consulta de armazenamento não aceita argumentos.")
                    }
                    val snapshot = store.snapshot()
                    return KellvynToolResult(
                        true,
                        buildString {
                            appendLine("ARMAZENAMENTO PRIVADO DE ARTEFATOS")
                            appendLine("records=\${snapshot.recordCount}")
                            appendLine("stored_bytes=\${snapshot.storedBytes}")
                            appendLine("max_artifact_bytes=\${snapshot.maxArtifactBytes}")
                            appendLine("max_store_bytes=\${snapshot.maxStoreBytes}")
                            appendLine("binary_payloads_in_model_context=false")
                        }
                    )
                }
            },
            object : KellvynAgentTool {
                override val spec = listSpec

                override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                    if (arguments.keys != setOf("task_id")) {
                        return KellvynToolResult(false, "A listagem exige somente task_id.")
                    }
                    return try {
                        val refs = store.listForTask(arguments.getValue("task_id"))
                        val shown = refs.take(MAX_LISTED_ARTIFACTS)
                        KellvynToolResult(
                            true,
                            buildString {
                                appendLine("ARTEFATOS DA TAREFA")
                                appendLine("total=\${refs.size}; exibidos=\${shown.size}")
                                shown.forEach { ref ->
                                    append(ref.id).append(" | ")
                                        .append(ref.fileName).append(" | ")
                                        .append(ref.mediaType).append(" | ")
                                        .append(ref.sizeBytes).append(" bytes | ")
                                        .append("producer=").append(ref.producerId).append(" | ")
                                        .append("sha256=").append(ref.sha256).appendLine()
                                }
                                append("Os dados listados são metadados; nenhum conteúdo binário foi aberto.")
                            }.take(MAX_OUTPUT_CHARS)
                        )
                    } catch (error: Exception) {
                        KellvynToolResult(
                            false,
                            "Não foi possível listar os artefatos: " +
                                (error.message ?: error.javaClass.simpleName).take(240)
                        )
                    }
                }
            },
            object : KellvynAgentTool {
                override val spec = verifySpec

                override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                    if (arguments.keys != setOf("artifact_id")) {
                        return KellvynToolResult(false, "A verificação exige somente artifact_id.")
                    }
                    return try {
                        val ref = store.get(arguments.getValue("artifact_id"))
                        val verified = store.verify(ref.id)
                        val content = buildString {
                            appendLine("ARTEFATO")
                            appendLine("artifact_id=\${ref.id}")
                            appendLine("task_id=\${ref.taskId}")
                            appendLine("file_name=\${ref.fileName}")
                            appendLine("media_type=\${ref.mediaType}")
                            appendLine("producer=\${ref.producerId}")
                            appendLine("size_bytes=\${ref.sizeBytes}")
                            appendLine("sha256=\${ref.sha256}")
                            append("integrity=").append(if (verified) "VERIFIED" else "FAILED")
                        }
                        KellvynToolResult(verified, content)
                    } catch (error: Exception) {
                        KellvynToolResult(
                            false,
                            "Não foi possível verificar o artefato: " +
                                (error.message ?: error.javaClass.simpleName).take(240)
                        )
                    }
                }
            }
        )
    }

    private const val MAX_LISTED_ARTIFACTS = 16
    private const val MAX_OUTPUT_CHARS = 6_000
}
