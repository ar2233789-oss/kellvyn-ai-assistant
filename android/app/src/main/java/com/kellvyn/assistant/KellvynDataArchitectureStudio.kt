package com.kellvyn.assistant

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Bounded structure-only inspection for JSON and CSV files in the private workspace. */
internal class KellvynDataArchitectureStudio(
    private val workspace: ProjectWorkspaceStore
) : KellvynStudioModule {
    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS.single()

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val projectId = arguments.getValue("project_id")
                val path = arguments.getValue("path")
                if (!projectId.matches(Regex("[a-z0-9][a-z0-9-]{0,60}"))) {
                    return KellvynToolResult(false, "Identificador de projeto inválido.")
                }
                val extension = path.substringAfterLast('.', "").lowercase()
                if (extension !in SUPPORTED_EXTENSIONS) {
                    return KellvynToolResult(false, "Formato não suportado; use JSON ou CSV.")
                }
                val excerpt = try {
                    workspace.readFileExcerpt(projectId, path, MAX_SOURCE_CHARS)
                } catch (error: Exception) {
                    return KellvynToolResult(false, "Não foi possível ler o arquivo: " + safeMessage(error))
                }
                if (SECRET_ASSIGNMENT.containsMatchIn(excerpt.content) ||
                    BEARER_TOKEN.containsMatchIn(excerpt.content) ||
                    PRIVATE_KEY.containsMatchIn(excerpt.content)
                ) {
                    return KellvynToolResult(false, "O arquivo contém possível credencial e não foi inspecionado.")
                }

                return try {
                    val report = when (extension) {
                        "json" -> inspectJson(excerpt.content)
                        "csv" -> inspectCsv(excerpt.content)
                        else -> error("Formato não reconhecido.")
                    }
                    KellvynToolResult(
                        true,
                        "ANÁLISE ESTRUTURAL: $path\n" +
                            "Tamanho: ${excerpt.sizeBytes} bytes; trecho truncado: ${excerpt.truncated}.\n" +
                            report +
                            "\nNenhum valor de registro é reproduzido."
                    )
                } catch (error: Exception) {
                    KellvynToolResult(false, "Não foi possível interpretar o arquivo: " + safeMessage(error))
                }
            }
        }
    )

    override fun trimTransientResources(level: KellvynCachePressure) = Unit
    override fun close() = Unit

    private data class Budget(var remaining: Int = MAX_JSON_NODES)

    private fun inspectJson(source: String): String {
        val parsed = JSONTokener(source).nextValue()
        val budget = Budget()
        val structure = describeJson(parsed, "$", 0, budget)
        return "FORMATO: JSON\nNós percorridos: ${MAX_JSON_NODES - budget.remaining}.\n" +
            structure.joinToString("\n")
    }

    private fun describeJson(value: Any?, path: String, depth: Int, budget: Budget): List<String> {
        if (budget.remaining <= 0) return emptyList()
        budget.remaining--
        if (depth > MAX_DEPTH) return listOf("$path: profundidade máxima atingida")
        return when (value) {
            null, JSONObject.NULL -> listOf("$path: nulo")
            is JSONObject -> {
                val keys = value.keys().asSequence().toList().sorted()
                val output = mutableListOf("$path: objeto (${keys.size} campo(s))")
                if (depth < MAX_DEPTH) {
                    keys.take(MAX_CHILDREN).forEach { key ->
                        val label = if (SENSITIVE_NAME.containsMatchIn(key)) "[campo-sensivel]" else key
                        output += describeJson(value.opt(key), "$path.$label", depth + 1, budget)
                    }
                }
                output
            }
            is JSONArray -> {
                val output = mutableListOf("$path: array (${value.length()} item(ns))")
                if (value.length() > 0 && depth < MAX_DEPTH) {
                    output += describeJson(value.opt(0), "$path[0]", depth + 1, budget)
                }
                output
            }
            is String -> listOf("$path: texto")
            is Number -> listOf("$path: número")
            is Boolean -> listOf("$path: booleano")
            else -> listOf("$path: tipo não reconhecido")
        }
    }

    private fun inspectCsv(source: String): String {
        val rows = source.lineSequence().take(MAX_CSV_ROWS).filter { it.isNotBlank() }.toList()
        require(rows.isNotEmpty()) { "CSV vazio." }
        val headers = parseCsvRow(rows.first())
        require(headers.isNotEmpty()) { "Cabeçalho CSV vazio." }
        val sample = rows.drop(1).take(MAX_CSV_SAMPLE_ROWS).map(::parseCsvRow)
        val report = mutableListOf(
            "FORMATO: CSV",
            "Colunas: ${headers.size}; linhas amostradas: ${sample.size}.",
            "Amostragem limitada; campos com quebra de linha não são suportados."
        )
        headers.forEachIndexed { index, header ->
            val label = if (SENSITIVE_NAME.containsMatchIn(header)) "[coluna-sensivel]" else header.take(60)
            val values = sample.mapNotNull { it.getOrNull(index)?.trim()?.takeIf { value -> value.isNotEmpty() } }
            val inferred = when {
                values.isEmpty() -> "sem amostra"
                values.all { it.equals("true", true) || it.equals("false", true) } -> "booleano"
                values.all { it.toLongOrNull() != null } -> "inteiro"
                values.all { it.toBigDecimalOrNull() != null } -> "decimal"
                values.all { DATE_LIKE.matches(it) } -> "data"
                else -> "texto ou misto"
            }
            report += "- $label: $inferred"
        }
        return report.joinToString("\n")
    }

    private fun parseCsvRow(line: String): List<String> {
        val cells = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += value.toString()
                    value.setLength(0)
                }
                else -> value.append(char)
            }
            index++
        }
        require(!quoted) { "Campo CSV com aspas não fechadas." }
        cells += value.toString()
        return cells
    }

    private fun safeMessage(error: Exception): String =
        (error.message ?: error.javaClass.simpleName).take(240)

    companion object {
        private const val MAX_SOURCE_CHARS = 24_000
        private const val MAX_DEPTH = 5
        private const val MAX_JSON_NODES = 128
        private const val MAX_CHILDREN = 32
        private const val MAX_CSV_ROWS = 101
        private const val MAX_CSV_SAMPLE_ROWS = 40
        private val SUPPORTED_EXTENSIONS = setOf("json", "csv")
        private val SENSITIVE_NAME = Regex("""(?i)(password|passwd|secret|token|api[_-]?key|private[_-]?key|authorization)""")
        private val DATE_LIKE = Regex("""\d{4}-\d{2}-\d{2}""")
        private val SECRET_ASSIGNMENT = Regex(
            """(?i)\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\b\s*[:=]\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
        )
        private val BEARER_TOKEN = Regex(
            """(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}"""
        )
        private val PRIVATE_KEY = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")

        private val TOOL_SPECS = listOf(
            KellvynToolSpec(
                name = "data_inspect_structure",
                description = "Inspeciona a estrutura de JSON ou CSV em um projeto privado sem reproduzir valores de registros.",
                requiredArguments = setOf("project_id", "path"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "project_id",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 61,
                        pattern = "^[a-z0-9][a-z0-9-]{0,60}$"
                    ),
                    KellvynToolArgumentSchema(
                        name = "path",
                        type = KellvynToolArgumentType.RELATIVE_PATH,
                        maxLength = 240
                    )
                ),
                timeoutMs = 15_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 24L * 1024L * 1024L,
                capabilities = setOf(KellvynStudioCapability.DATA_MODELING)
            )
        )

        val MANIFEST = KellvynStudioModuleManifest(
            moduleId = "data-architecture",
            version = 1,
            capabilities = setOf(KellvynStudioCapability.DATA_MODELING),
            supportedExtensions = SUPPORTED_EXTENSIONS,
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedLoadWorkingSetBytes = 2L * 1024L * 1024L,
            estimatedResidentBytes = 1L * 1024L * 1024L,
            permissions = setOf(KellvynStudioPermission.PRIVATE_WORKSPACE_READ),
            toolSpecs = TOOL_SPECS
        )

        fun definition(workspace: ProjectWorkspaceStore) =
            KellvynStudioModuleDefinition(MANIFEST) { KellvynDataArchitectureStudio(workspace) }
    }
}
