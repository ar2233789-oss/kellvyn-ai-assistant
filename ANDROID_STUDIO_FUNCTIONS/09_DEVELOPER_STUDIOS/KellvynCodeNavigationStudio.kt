package com.kellvyn.assistant

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import java.security.MessageDigest

/** Headless Kotlin/Java navigation engine with bounded scans and a byte-limited AST-hint cache. */
internal class KellvynCodeNavigationStudio(
    private val workspace: ProjectWorkspaceStore
) : KellvynStudioModule {
    override val manifest: KellvynStudioModuleManifest = MANIFEST

    private val indexCache = KellvynManagedMemoryCache<String, KellvynProjectCodeIndex>(
        reclaimerId = "code-navigation-index",
        reclaimPriority = 70,
        maxBytes = MAX_INDEX_CACHE_BYTES,
        sizeOf = { _, index ->
            (
                256L + index.path.length * 2L +
                    (index.packageName?.length ?: 0) * 2L +
                    index.imports.sumOf { it.length * 2L + 16L } +
                    index.declarations.sumOf { it.name.length * 2L + it.kind.length * 2L + 24L } +
                    index.references.sumOf { it.length * 2L + 16L }
                ).coerceAtLeast(1L)
        }
    )

    override val tools: List<KellvynAgentTool> = listOf(
        findSymbolsTool(),
        findReferencesTool(),
        fileOutlineTool()
    )

    override fun trimTransientResources(level: KellvynCachePressure) {
        indexCache.reclaim(level, Long.MAX_VALUE)
    }

    override fun close() = indexCache.clear()

    private fun findSymbolsTool() = object : KellvynAgentTool {
        override val spec = TOOL_SPECS[0]
        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            val projectId = arguments.getValue("project_id")
            val query = arguments.getValue("query").trim()
            val report = scanProject(projectId) { filePath, index ->
                index.declarations
                    .filter { it.name.contains(query, ignoreCase = true) }
                    .map { item ->
                        "$filePath:$${item.line}  ${item.kind} ${item.name}" +
                            (index.packageName?.let { "  [$it]" } ?: "")
                    }
            }
            return KellvynToolResult(
                true,
                buildString {
                    append("Busca de símbolos em ").append(report.filesScanned).append(" arquivo(s); ")
                    append(report.filesSkipped).append(" ignorado(s).\n")
                    append(if (report.matches.isEmpty()) "Nenhuma definição encontrada." else report.matches.joinToString("\n"))
                }.take(MAX_OUTPUT_CHARS)
            )
        }
    }

    private fun findReferencesTool() = object : KellvynAgentTool {
        override val spec = TOOL_SPECS[1]
        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            val projectId = arguments.getValue("project_id")
            val identifier = arguments.getValue("identifier").trim()
            val report = scanProject(projectId) { filePath, index ->
                index.references
                    .filter { it.equals(identifier, ignoreCase = true) }
                    .map { "$filePath  chamada/referência a $it" }
            }
            return KellvynToolResult(
                true,
                buildString {
                    append("Busca de referências de ").append(identifier).append("; ")
                    append(report.filesScanned).append(" arquivo(s) analisado(s); ")
                    append(report.filesSkipped).append(" ignorado(s).\n")
                    append(if (report.matches.isEmpty()) "Nenhuma referência reconhecida." else report.matches.joinToString("\n"))
                    append("\nObservação: o indexador atual reconhece chamadas sintáticas simples; não é ainda um AST completo.")
                }.take(MAX_OUTPUT_CHARS)
            )
        }
    }

    private fun fileOutlineTool() = object : KellvynAgentTool {
        override val spec = TOOL_SPECS[2]
        override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
            val projectId = arguments.getValue("project_id")
            val path = arguments.getValue("path")
            if (!isSourcePath(path)) {
                return KellvynToolResult(false, "O estúdio aceita somente arquivos Kotlin/Java.")
            }
            val excerpt = workspace.readFileExcerpt(projectId, path, MAX_SOURCE_CHARS)
            if (containsSensitiveMaterial(excerpt.content)) {
                return KellvynToolResult(false, "O arquivo parece conter credenciais e foi omitido da análise.")
            }
            val index = index(projectId, path, excerpt.content)
            return KellvynToolResult(
                true,
                buildString {
                    append("ARQUIVO: ").append(path).append("\n")
                    append("PACOTE: ").append(index.packageName ?: "(sem pacote)").append("\n")
                    append("TRUNCADO: ").append(excerpt.truncated || index.truncatedSource).append("\n")
                    append("IMPORTS:\n")
                    append(if (index.imports.isEmpty()) "(nenhum)" else index.imports.sorted().joinToString("\n"))
                    append("\nDECLARAÇÕES:\n")
                    append(
                        if (index.declarations.isEmpty()) "(nenhuma reconhecida)"
                        else index.declarations.joinToString("\n") {
                            "${it.line}: ${it.kind} ${it.name}"
                        }
                    )
                    append("\nREFERÊNCIAS:\n")
                    append(if (index.references.isEmpty()) "(nenhuma reconhecida)" else index.references.sorted().joinToString(", "))
                }.take(MAX_OUTPUT_CHARS)
            )
        }
    }

    private data class ScanReport(
        val matches: List<String>,
        val filesScanned: Int,
        val filesSkipped: Int
    )

    private fun scanProject(
        projectId: String,
        selectMatches: (String, KellvynProjectCodeIndex) -> List<String>
    ): ScanReport {
        require(projectId.matches(Regex("[a-z0-9][a-z0-9-]{0,60}"))) { "Identificador de projeto inválido." }
        val allFiles = workspace.listFiles(projectId)
        val selected = allFiles.asSequence().filter(::isSourcePath).take(MAX_FILES_PER_SCAN).toList()
        var scanned = 0
        var skipped = (allFiles.size - selected.size).coerceAtLeast(0)
        val matches = mutableListOf<String>()

        for (filePath in selected) {
            try {
                val excerpt = workspace.readFileExcerpt(projectId, filePath, MAX_SOURCE_CHARS)
                if (containsSensitiveMaterial(excerpt.content)) {
                    skipped++
                    continue
                }
                val index = index(projectId, filePath, excerpt.content)
                scanned++
                for (match in selectMatches(filePath, index)) {
                    if (matches.size >= MAX_RESULTS) break
                    matches += match.take(240)
                }
                if (matches.size >= MAX_RESULTS) break
            } catch (_: Exception) {
                skipped++
            }
        }
        return ScanReport(matches, scanned, skipped)
    }

    private fun index(projectId: String, path: String, source: String): KellvynProjectCodeIndex {
        val key = sha256(projectId + "\u0000" + path + "\u0000" + source)
        return indexCache.get(key) ?: KellvynProjectCodeIndexer.index(path, source).also {
            runCatching { indexCache.put(key, it) }
        }
    }

    private fun isSourcePath(path: String): Boolean =
        path.endsWith(".kt", ignoreCase = true) || path.endsWith(".java", ignoreCase = true)

    private fun containsSensitiveMaterial(value: String): Boolean =
        SECRET_ASSIGNMENT.containsMatchIn(value) ||
            BEARER_TOKEN.containsMatchIn(value) ||
            PRIVATE_KEY.containsMatchIn(value) ||
            COMMON_TOKEN.containsMatchIn(value)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_FILES_PER_SCAN = 60
        private const val MAX_RESULTS = 32
        private const val MAX_SOURCE_CHARS = 12_000
        private const val MAX_OUTPUT_CHARS = 8_000
        private const val MAX_INDEX_CACHE_BYTES = 4L * 1024L * 1024L
        private const val MEBIBYTE = 1024L * 1024L

        private val SECRET_ASSIGNMENT = Regex(
            """(?i)\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\b\s*[:=]\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
        )
        private val BEARER_TOKEN = Regex(
            """(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}"""
        )
        private val PRIVATE_KEY = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")
        private val COMMON_TOKEN = Regex(
            """\b(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{16,})\b"""
        )

        private val TOOL_SPECS = listOf(
            KellvynToolSpec(
                name = "code_find_symbols",
                description = "Busca definições Kotlin/Java por nome em um projeto privado, sem executar código.",
                requiredArguments = setOf("project_id", "query"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema("project_id", KellvynToolArgumentType.TEXT, maxLength = 61, pattern = "^[a-z0-9][a-z0-9-]{0,60}$"),
                    KellvynToolArgumentSchema("query", KellvynToolArgumentType.TEXT, maxLength = 80, pattern = "^[A-Za-z_][A-Za-z0-9_]*$")
                ),
                timeoutMs = 20_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 48L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolSpec(
                name = "code_find_references",
                description = "Busca chamadas e referências reconhecidas pelo indexador Kotlin/Java.",
                requiredArguments = setOf("project_id", "identifier"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema("project_id", KellvynToolArgumentType.TEXT, maxLength = 61, pattern = "^[a-z0-9][a-z0-9-]{0,60}$"),
                    KellvynToolArgumentSchema("identifier", KellvynToolArgumentType.TEXT, maxLength = 80, pattern = "^[A-Za-z_][A-Za-z0-9_]*$")
                ),
                timeoutMs = 20_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 48L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            ),
            KellvynToolSpec(
                name = "code_file_outline",
                description = "Retorna pacote, imports, declarações e referências reconhecidas de um arquivo Kotlin/Java.",
                requiredArguments = setOf("project_id", "path"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema("project_id", KellvynToolArgumentType.TEXT, maxLength = 61, pattern = "^[a-z0-9][a-z0-9-]{0,60}$"),
                    KellvynToolArgumentSchema("path", KellvynToolArgumentType.RELATIVE_PATH, maxLength = 240)
                ),
                timeoutMs = 10_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 16L * MEBIBYTE,
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            )
        )

        val MANIFEST = KellvynStudioModuleManifest(
            moduleId = "code-navigation",
            version = 1,
            capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION),
            supportedExtensions = setOf("kt", "java"),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedLoadWorkingSetBytes = 4L * MEBIBYTE,
            estimatedResidentBytes = 2L * MEBIBYTE,
            permissions = setOf(KellvynStudioPermission.PRIVATE_WORKSPACE_READ),
            toolSpecs = TOOL_SPECS
        )

        fun definition(workspace: ProjectWorkspaceStore) =
            KellvynStudioModuleDefinition(MANIFEST) { KellvynCodeNavigationStudio(workspace) }
    }
}
