package com.kellvyn.assistant

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore

/** Bounded, read-only static checks. Findings never include the matched secret or source line. */
internal class KellvynSecurityAuditStudio(
    private val workspace: ProjectWorkspaceStore
) : KellvynStudioModule {
    override val manifest: KellvynStudioModuleManifest = MANIFEST

    override val tools: List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = TOOL_SPECS.single()

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val projectId = arguments.getValue("project_id")
                require(projectId.matches(Regex("[a-z0-9][a-z0-9-]{0,60}"))) {
                    "Identificador de projeto inválido."
                }
                val allFiles = workspace.listFiles(projectId)
                val selected = allFiles.asSequence()
                    .filter(KellvynAgentLimits::isSupportedTextFile)
                    .take(MAX_FILES)
                    .toList()
                var skipped = (allFiles.size - selected.size).coerceAtLeast(0)
                var scanned = 0
                val findings = mutableListOf<Finding>()

                for (path in selected) {
                    val excerpt = try {
                        workspace.readFileExcerpt(
                            projectId = projectId,
                            relativePath = path,
                            maxChars = MAX_SOURCE_CHARS
                        )
                    } catch (_: Exception) {
                        skipped++
                        continue
                    }
                    if (containsSensitiveMaterial(excerpt.content)) {
                        // Do not echo or store the secret-bearing text in findings or context.
                        scanned++
                        findings += Finding(
                            path = path,
                            line = 1,
                            rule = "SECRET_MATERIAL_FILE_SKIPPED",
                            severity = Severity.HIGH,
                            description = "Arquivo com possível credencial detectado; conteúdo não foi analisado nem reproduzido."
                        )
                        continue
                    }

                    scanned++
                    excerpt.content.lineSequence().forEachIndexed { index, line ->
                        for (rule in RULES) {
                            if (rule.pattern.containsMatchIn(line)) {
                                findings += Finding(
                                    path = path,
                                    line = index + 1,
                                    rule = rule.id,
                                    severity = rule.severity,
                                    description = rule.description
                                )
                                if (findings.size >= MAX_FINDINGS) break
                            }
                        }
                        if (findings.size >= MAX_FINDINGS) return@forEachIndexed
                    }
                    if (findings.size >= MAX_FINDINGS) break
                }

                val ordered = findings.distinctBy { Triple(it.path, it.line, it.rule) }
                    .sortedWith(
                        compareByDescending<Finding> { it.severity.ordinal }
                            .thenBy { it.path }
                            .thenBy { it.line }
                    )
                return KellvynToolResult(
                    success = true,
                    content = buildString {
                        append("AUDITORIA ESTÁTICA LOCAL (heurística; não é prova de ausência de vulnerabilidades)\n")
                        append("Arquivos lidos: ").append(scanned)
                            .append("; ignorados/fora do limite: ").append(skipped)
                            .append("; achados: ").append(ordered.size).append(".\n")
                        if (ordered.isEmpty()) {
                            append("Nenhum padrão desta lista foi detectado.")
                        } else {
                            ordered.forEach { finding ->
                                append(finding.severity.name)
                                    .append(" [").append(finding.rule).append("] ")
                                    .append(finding.path).append(":").append(finding.line)
                                    .append(" — ").append(finding.description)
                                    .append("\n")
                            }
                        }
                        append("\nEscopo: análise de texto limitada; não executa código, não resolve fluxo de dados e pode gerar falsos positivos/negativos.")
                    }.take(MAX_OUTPUT_CHARS)
                )
            }
        }
    )

    override fun trimTransientResources(level: KellvynCachePressure) = Unit
    override fun close() = Unit

    private data class Finding(
        val path: String,
        val line: Int,
        val rule: String,
        val severity: Severity,
        val description: String
    )

    private enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    private data class Rule(
        val id: String,
        val severity: Severity,
        val pattern: Regex,
        val description: String
    )

    private fun containsSensitiveMaterial(value: String): Boolean =
        SECRET_ASSIGNMENT.containsMatchIn(value) ||
            BEARER_TOKEN.containsMatchIn(value) ||
            PRIVATE_KEY.containsMatchIn(value) ||
            COMMON_TOKEN.containsMatchIn(value)

    companion object {
        private const val MAX_FILES = 60
        private const val MAX_SOURCE_CHARS = 12_000
        private const val MAX_FINDINGS = 60
        private const val MAX_OUTPUT_CHARS = 8_000

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

        private val RULES = listOf(
            Rule(
                "HARDCODED_SECRET",
                Severity.HIGH,
                SECRET_ASSIGNMENT,
                "Possível credencial literal no código/configuração; mova para armazenamento seguro e rotacione se real."
            ),
            Rule(
                "PRIVATE_KEY_MATERIAL",
                Severity.CRITICAL,
                PRIVATE_KEY,
                "Cabeçalho de chave privada presente; remova do projeto e considere a chave comprometida."
            ),
            Rule(
                "BEARER_CREDENTIAL",
                Severity.HIGH,
                BEARER_TOKEN,
                "Possível token Bearer literal; remova e rotacione se real."
            ),
            Rule(
                "COMMON_TOKEN_PATTERN",
                Severity.HIGH,
                COMMON_TOKEN,
                "Padrão semelhante a token de serviço detectado; valide sem copiar o valor para logs."
            ),
            Rule(
                "PROCESS_EXECUTION",
                Severity.HIGH,
                Regex("""\b(?:Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec|ProcessBuilder)\s*\("""),
                "Execução de processo externo; confira origem dos argumentos e necessidade da operação."
            ),
            Rule(
                "WEBVIEW_JAVASCRIPT",
                Severity.MEDIUM,
                Regex("""\bsetJavaScriptEnabled\s*\(\s*true\s*\)"""),
                "JavaScript habilitado em WebView; restrinja conteúdo, navegação e bridges."
            ),
            Rule(
                "WEBVIEW_FILE_ACCESS",
                Severity.MEDIUM,
                Regex("""\bsetAllowFileAccess\s*\(\s*true\s*\)"""),
                "Acesso a arquivos habilitado em WebView; revise risco de conteúdo local."
            ),
            Rule(
                "ANDROID_CLEAR_TEXT",
                Severity.HIGH,
                Regex("""android:usesCleartextTraffic\s*=\s*["']true["']"""),
                "Tráfego cleartext habilitado; prefira TLS e exceções de domínio muito específicas."
            ),
            Rule(
                "ANDROID_DEBUGGABLE",
                Severity.MEDIUM,
                Regex("""android:debuggable\s*=\s*["']true["']"""),
                "Aplicativo configurado como debuggable; confirme que isso não chega à distribuição."
            ),
            Rule(
                "ANDROID_EXPORTED_COMPONENT",
                Severity.MEDIUM,
                Regex("""android:exported\s*=\s*["']true["']"""),
                "Componente exportado; confira intent filters, permissões e validação de entrada."
            ),
            Rule(
                "ANDROID_BACKUP_ENABLED",
                Severity.LOW,
                Regex("""android:allowBackup\s*=\s*["']true["']"""),
                "Backup permitido; confira se os dados sensíveis estão excluídos pelas regras de backup."
            ),
            Rule(
                "EMPTY_TRUST_MANAGER",
                Severity.CRITICAL,
                Regex("""checkServerTrusted\s*\([^)]*\)\s*\{\s*\}"""),
                "Implementação vazia de checkServerTrusted; isso pode desabilitar a validação TLS."
            ),
            Rule(
                "PERMISSIVE_HOSTNAME_VERIFIER",
                Severity.CRITICAL,
                Regex("""HostnameVerifier[\s\S]{0,240}return\s+true"""),
                "Verificador de hostname aparentemente permissivo; confira validação TLS."
            )
        )

        private val TOOL_SPECS = listOf(
            KellvynToolSpec(
                name = "security_scan_project",
                description = "Executa auditoria estática local e limitada em arquivos textuais de um projeto; não executa código nem reproduz segredos.",
                requiredArguments = setOf("project_id"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "project_id",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 61,
                        pattern = "^[a-z0-9][a-z0-9-]{0,60}$"
                    )
                ),
                timeoutMs = 30_000L,
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 40L * 1024L * 1024L,
                capabilities = setOf(
                    KellvynStudioCapability.SECURITY_AUDIT,
                    KellvynStudioCapability.CODE_NAVIGATION
                )
            )
        )

        val MANIFEST = KellvynStudioModuleManifest(
            moduleId = "security-audit",
            version = 1,
            capabilities = setOf(
                KellvynStudioCapability.SECURITY_AUDIT,
                KellvynStudioCapability.CODE_NAVIGATION
            ),
            supportedExtensions = setOf(
                "txt", "md", "json", "csv", "xml", "html", "css", "java", "kt", "kts",
                "gradle", "properties", "js", "jsx", "ts", "tsx", "py", "log", "yaml",
                "yml", "toml"
            ),
            minSdk = 24,
            requiresNetwork = false,
            workloadClass = KellvynWorkloadClass.STANDARD,
            estimatedLoadWorkingSetBytes = 4L * 1024L * 1024L,
            estimatedResidentBytes = 2L * 1024L * 1024L,
            permissions = setOf(KellvynStudioPermission.PRIVATE_WORKSPACE_READ),
            toolSpecs = TOOL_SPECS
        )

        fun definition(workspace: ProjectWorkspaceStore) =
            KellvynStudioModuleDefinition(MANIFEST) {
                KellvynSecurityAuditStudio(workspace)
            }
    }
}
