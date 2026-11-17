package com.kellvyn.assistant

import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived, single-use approval for one exact workspace mutation.
 *
 * The approval is bound to the project, normalized relative path, operation and (for writes)
 * SHA-256 of the exact UTF-8 content. It is not a general permission to edit a project.
 */
internal class KellvynProjectWriteApprovalGate(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private enum class Operation { WRITE, DELETE }

    private data class Approval(
        val projectId: String,
        val path: String,
        val operation: Operation,
        val contentSha256: String?,
        val expiresAtMillis: Long
    )

    private val approvals = ConcurrentHashMap<String, Approval>()

    init {
        require(ttlMillis in 1_000L..10 * 60 * 1_000L)
    }

    @Synchronized
    fun approveWrite(projectId: String, path: String, content: String): String {
        validateProjectId(projectId)
        val normalizedPath = normalizePath(path)
        require(content.length in 1..MAX_CONTENT_CHARS) {
            "O conteúdo precisa ter entre 1 e $MAX_CONTENT_CHARS caracteres."
        }
        return createApproval(
            Approval(
                projectId = projectId,
                path = normalizedPath,
                operation = Operation.WRITE,
                contentSha256 = sha256(content),
                expiresAtMillis = safeExpiry()
            )
        )
    }

    @Synchronized
    fun approveDelete(projectId: String, path: String): String {
        validateProjectId(projectId)
        return createApproval(
            Approval(
                projectId = projectId,
                path = normalizePath(path),
                operation = Operation.DELETE,
                contentSha256 = null,
                expiresAtMillis = safeExpiry()
            )
        )
    }

    /** Checks without consuming; the tool itself consumes atomically immediately before mutation. */
    fun authorizesWrite(arguments: Map<String, String>): Boolean =
        matches(arguments, Operation.WRITE)

    fun authorizesDelete(arguments: Map<String, String>): Boolean =
        matches(arguments, Operation.DELETE)

    @Synchronized
    fun consumeWrite(arguments: Map<String, String>): Boolean =
        consume(arguments, Operation.WRITE)

    @Synchronized
    fun consumeDelete(arguments: Map<String, String>): Boolean =
        consume(arguments, Operation.DELETE)

    private fun createApproval(approval: Approval): String {
        purgeExpired()
        val bytes = ByteArray(APPROVAL_ID_BYTES)
        random.nextBytes(bytes)
        val id = bytes.joinToString("") { "%02x".format(it) }
        approvals[id] = approval
        return id
    }

    private fun matches(arguments: Map<String, String>, operation: Operation): Boolean =
        matchingApprovalId(arguments, operation) != null

    private fun matchingApprovalId(
        arguments: Map<String, String>,
        operation: Operation
    ): String? {
        val projectId = arguments["project_id"] ?: return null
        val path = arguments["path"]?.let { runCatching { normalizePath(it) }.getOrNull() }
            ?: return null
        val suppliedId = arguments["approval_id"]
        val candidates: List<Pair<String, Approval?>> = if (suppliedId != null) {
            listOfNotNull(suppliedId to approvals[suppliedId])
        } else {
            approvals.entries.map { it.key to it.value }
        }

        return candidates.firstOrNull { (_, approval) ->
            if (approval == null || approval.expiresAtMillis <= clock() ||
                approval.operation != operation ||
                approval.projectId != projectId ||
                approval.path != path
            ) return@firstOrNull false

            when (operation) {
                Operation.WRITE -> {
                    val content = arguments["content"] ?: return@firstOrNull false
                    content.length in 1..MAX_CONTENT_CHARS &&
                        approval.contentSha256 == sha256(content)
                }
                Operation.DELETE -> true
            }
        }?.first
    }

    private fun consume(arguments: Map<String, String>, operation: Operation): Boolean {
        val id = matchingApprovalId(arguments, operation) ?: return false
        return approvals.remove(id) != null
    }

    private fun purgeExpired() {
        val now = clock()
        approvals.entries.removeIf { it.value.expiresAtMillis <= now }
    }

    private fun safeExpiry(): Long {
        val now = clock()
        require(now > 0L)
        return if (Long.MAX_VALUE - now < ttlMillis) Long.MAX_VALUE else now + ttlMillis
    }

    private fun validateProjectId(projectId: String) {
        require(projectId.matches(PROJECT_ID)) { "Identificador de projeto inválido." }
    }

    private fun normalizePath(value: String): String {
        val path = value.trim()
        require(path.isNotBlank() && path.length <= MAX_PATH_CHARS)
        require(!path.startsWith("/") && !path.startsWith("\\") && !path.contains("\\")) {
            "Use um caminho relativo com barras normais."
        }
        require(!Regex("^[A-Za-z]:").containsMatchIn(path))
        val segments = path.split("/")
        require(segments.all { segment ->
            segment.isNotBlank() && segment != "." && segment != ".." &&
                !segment.contains(":") && segment.none { it.code < 32 }
        }) { "O caminho não é um caminho relativo seguro." }
        require(segments.none { it.equals(".git", true) || it.equals(".gradle", true) }) {
            "Não é permitido alterar diretórios internos de controle."
        }
        return segments.joinToString("/")
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_CONTENT_CHARS = 3_500
        private const val DEFAULT_TTL_MILLIS = 120_000L
        private const val APPROVAL_ID_BYTES = 24
        private const val MAX_PATH_CHARS = 240
        private val PROJECT_ID = Regex("[a-z0-9][a-z0-9-]{0,60}")
    }
}

/**
 * Local workspace tools for the main Kellvyn coordinator and its specialist delegates.
 * Specialists receive only the READ_ONLY subset created by KellvynToolRegistry.
 */
internal object KellvynProjectWorkspaceTools {
    fun create(
        workspace: ProjectWorkspaceStore,
        approvals: KellvynProjectWriteApprovalGate
    ): List<KellvynAgentTool> = listOf(
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "workspace_list_projects",
                description = "Lista os projetos que já existem no workspace local privado.",
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION)
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val projects = workspace.listProjects().take(KellvynAgentLimits.MAX_PROJECTS_RETURNED)
                val output = if (projects.isEmpty()) {
                    "Nenhum projeto local encontrado."
                } else {
                    projects.joinToString("\n") {
                        "${it.id}\t${it.name}\t${it.fileCount} arquivo(s)"
                    }
                }
                return KellvynToolResult(true, output.take(MAX_TOOL_OUTPUT_CHARS))
            }
        },
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "workspace_list_files",
                description = "Lista os caminhos relativos dos arquivos de um projeto.",
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION),
                requiredArguments = setOf("project_id"),
                argumentSchemas = listOf(
                    KellvynToolArgumentSchema(
                        name = "project_id",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = 61,
                        pattern = "^[a-z0-9][a-z0-9-]{0,60}$"
                    )
                )
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val projectId = arguments.getValue("project_id")
                val paths = workspace.listFiles(projectId)
                    .take(KellvynAgentLimits.MAX_PROJECT_FILES_RETURNED)
                return KellvynToolResult(
                    true,
                    if (paths.isEmpty()) "O projeto não contém arquivos." else paths.joinToString("\n")
                )
            }
        },
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "workspace_read_file",
                description = "Lê um trecho UTF-8 limitado de um arquivo do projeto privado.",
                capabilities = setOf(KellvynStudioCapability.CODE_NAVIGATION),
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 8L * 1024L * 1024L,
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
                )
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                val excerpt = workspace.readFileExcerpt(
                    projectId = arguments.getValue("project_id"),
                    relativePath = arguments.getValue("path"),
                    maxChars = KellvynAgentLimits.MAX_FILE_READ_CHARS
                )
                val label = "ARQUIVO: ${excerpt.path}; bytes=${excerpt.sizeBytes}; truncado=${excerpt.truncated}\n"
                return KellvynToolResult(
                    true,
                    (label + excerpt.content).take(MAX_TOOL_OUTPUT_CHARS)
                )
            }
        },
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "workspace_write_file",
                description = "Cria ou substitui um arquivo somente após aprovação única do proprietário vinculada ao conteúdo exato.",
                capabilities = setOf(KellvynStudioCapability.CODE_EDITING),
                workloadClass = KellvynWorkloadClass.STANDARD,
                estimatedWorkingSetBytes = 8L * 1024L * 1024L,
                requiredArguments = setOf("project_id", "path", "content"),
                optionalArguments = setOf("approval_id"),
                risk = KellvynToolRisk.REVERSIBLE_WRITE,
                timeoutMs = 10_000L,
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
                    ),
                    KellvynToolArgumentSchema(
                        name = "content",
                        type = KellvynToolArgumentType.TEXT,
                        maxLength = KellvynProjectWriteApprovalGate.MAX_CONTENT_CHARS
                    ),
                    KellvynToolArgumentSchema(
                        name = "approval_id",
                        type = KellvynToolArgumentType.TEXT,
                        required = false,
                        maxLength = 48,
                        pattern = "^[a-f0-9]{48}$"
                    )
                )
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (!approvals.consumeWrite(arguments)) {
                    return KellvynToolResult(false, "Gravação recusada: aprovação ausente, expirada, divergente ou já utilizada.")
                }
                val project = workspace.addOrReplaceFiles(
                    arguments.getValue("project_id"),
                    listOf(
                        ProjectFileInput(
                            path = arguments.getValue("path"),
                            content = arguments.getValue("content")
                        )
                    )
                )
                return KellvynToolResult(
                    true,
                    "Arquivo gravado: ${arguments.getValue("path")}. Projeto atualizado: ${project.id}."
                )
            }
        },
        object : KellvynAgentTool {
            override val spec = KellvynToolSpec(
                name = "workspace_delete_file",
                description = "Remove um arquivo do projeto somente após aprovação única do proprietário para o caminho exato.",
                capabilities = setOf(KellvynStudioCapability.CODE_EDITING),
                workloadClass = KellvynWorkloadClass.LIGHT,
                requiredArguments = setOf("project_id", "path"),
                optionalArguments = setOf("approval_id"),
                risk = KellvynToolRisk.DESTRUCTIVE,
                timeoutMs = 10_000L,
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
                    ),
                    KellvynToolArgumentSchema(
                        name = "approval_id",
                        type = KellvynToolArgumentType.TEXT,
                        required = false,
                        maxLength = 48,
                        pattern = "^[a-f0-9]{48}$"
                    )
                )
            )

            override suspend fun execute(arguments: Map<String, String>): KellvynToolResult {
                if (!approvals.consumeDelete(arguments)) {
                    return KellvynToolResult(false, "Remoção recusada: aprovação ausente, expirada, divergente ou já utilizada.")
                }
                val deleted = workspace.deleteFile(
                    arguments.getValue("project_id"),
                    arguments.getValue("path")
                )
                return KellvynToolResult(
                    deleted,
                    if (deleted) "Arquivo removido: ${arguments.getValue("path")}."
                    else "O arquivo não existia e nada foi removido."
                )
            }
        }
    )

    fun authorization(approvals: KellvynProjectWriteApprovalGate): KellvynToolAuthorization =
        object : KellvynToolAuthorization {
            override suspend fun authorize(
                spec: KellvynToolSpec,
                arguments: Map<String, String>,
                task: String
            ): Boolean = when (spec.risk) {
                KellvynToolRisk.READ_ONLY -> true
                KellvynToolRisk.REVERSIBLE_WRITE -> approvals.authorizesWrite(arguments)
                KellvynToolRisk.DESTRUCTIVE -> approvals.authorizesDelete(arguments)
                KellvynToolRisk.NETWORK_READ,
                KellvynToolRisk.DEVICE_CONTROL -> false
            }
        }

    private const val MAX_TOOL_OUTPUT_CHARS = 16_000
}
