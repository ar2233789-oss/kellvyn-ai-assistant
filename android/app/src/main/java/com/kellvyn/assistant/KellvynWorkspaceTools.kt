package com.kellvyn.assistant

import android.content.Context
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import java.util.concurrent.atomic.AtomicInteger

/**
 * First tool set exposed to the on-device model.
 *
 * It deliberately contains read-only operations only. There is no shell, arbitrary
 * filesystem access, network client, file mutation, deletion, build, or publication
 * function in this registry.
 */
internal class KellvynWorkspaceTools(context: Context) : ToolSet {
    private val workspace = ProjectWorkspaceStore(context.applicationContext)
    private val callsThisTurn = AtomicInteger(0)

    @Volatile
    private var statusReporter: ((String) -> Unit)? = null

    /** Called once for each user message before inference starts. */
    fun beginTurn(onStatus: ((String) -> Unit)? = null) {
        callsThisTurn.set(0)
        statusReporter = onStatus
    }

    @Tool(description = "Lista os projetos locais que já estão salvos no workspace privado da Kellvyn. Use para descobrir projetos existentes. Esta ferramenta só lê dados.")
    fun listLocalProjects(): Map<String, Any> {
        toolBudgetError()?.let { return it }
        statusReporter?.invoke("CONSULTANDO PROJETOS LOCAIS")
        return try {
            val allProjects = workspace.listProjects()
            val projects = allProjects.take(KellvynAgentLimits.MAX_PROJECTS_RETURNED).map { project ->
                mapOf(
                    "id" to project.id,
                    "name" to project.name,
                    "description" to project.description,
                    "fileCount" to project.fileCount,
                    "updatedAt" to project.updatedAt
                )
            }
            mapOf(
                "ok" to true,
                "projects" to projects,
                "totalProjects" to allProjects.size,
                "truncated" to (allProjects.size > projects.size)
            )
        } catch (error: Exception) {
            failure("Não foi possível listar os projetos locais.", error)
        }
    }

    @Tool(description = "Lista caminhos relativos dos arquivos de um projeto local. Primeiro use listLocalProjects para obter o projectId. Esta ferramenta só lê nomes de arquivos.")
    fun listProjectFiles(
        @ToolParam(description = "Identificador do projeto retornado por listLocalProjects.") projectId: String
    ): Map<String, Any> {
        toolBudgetError()?.let { return it }
        statusReporter?.invoke("MAPEANDO ARQUIVOS DO PROJETO")
        return try {
            val allFiles = workspace.listFiles(projectId)
            val files = allFiles.take(KellvynAgentLimits.MAX_PROJECT_FILES_RETURNED)
            mapOf(
                "ok" to true,
                "projectId" to projectId,
                "files" to files,
                "totalFiles" to allFiles.size,
                "truncated" to (allFiles.size > files.size)
            )
        } catch (error: Exception) {
            failure("Não foi possível listar os arquivos desse projeto.", error)
        }
    }

    @Tool(description = "Lê o conteúdo textual de um arquivo dentro de um projeto local. Use apenas um caminho relativo retornado por listProjectFiles. A resposta é limitada a 12000 caracteres. Esta ferramenta não altera arquivos.")
    fun readProjectFile(
        @ToolParam(description = "Identificador do projeto retornado por listLocalProjects.") projectId: String,
        @ToolParam(description = "Caminho relativo exato retornado por listProjectFiles.") relativePath: String
    ): Map<String, Any> {
        toolBudgetError()?.let { return it }
        if (!KellvynAgentLimits.isSupportedTextFile(relativePath)) {
            return mapOf(
                "ok" to false,
                "error" to "Este leitor aceita apenas arquivos de texto e código conhecidos."
            )
        }
        statusReporter?.invoke("LENDO ARQUIVO DO PROJETO")
        return try {
            val fullText = workspace.readFile(projectId, relativePath)
            val limitedText = fullText.take(KellvynAgentLimits.MAX_FILE_READ_CHARS)
            mapOf(
                "ok" to true,
                "projectId" to projectId,
                "path" to relativePath,
                "content" to limitedText,
                "charactersReturned" to limitedText.length,
                "totalCharacters" to fullText.length,
                "truncated" to (fullText.length > limitedText.length)
            )
        } catch (error: Exception) {
            failure("Não foi possível ler esse arquivo do workspace.", error)
        }
    }

    @Tool(description = "Pesquisa texto dentro dos arquivos de código e documentação de um projeto local. Limita a varredura a 60 arquivos e 20 ocorrências. Use apenas quando o usuário pedir para localizar algo em um projeto.")
    fun searchProjectFiles(
        @ToolParam(description = "Identificador do projeto retornado por listLocalProjects.") projectId: String,
        @ToolParam(description = "Texto literal a procurar, entre 2 e 120 caracteres.") query: String
    ): Map<String, Any> {
        toolBudgetError()?.let { return it }
        if (!KellvynAgentLimits.isValidSearchQuery(query)) {
            return mapOf(
                "ok" to false,
                "error" to "A pesquisa precisa ter entre 2 e 120 caracteres."
            )
        }
        statusReporter?.invoke("PESQUISANDO NOS ARQUIVOS DO PROJETO")
        return try {
            val allPaths = workspace.listFiles(projectId)
            val candidates = allPaths
                .filter(KellvynAgentLimits::isSupportedTextFile)
                .take(KellvynAgentLimits.MAX_FILES_SCANNED_PER_SEARCH)
            val matches = ArrayList<Map<String, Any>>()
            var scannedFiles = 0

            for (path in candidates) {
                val content = try {
                    workspace.readFile(projectId, path)
                } catch (_: Exception) {
                    continue
                }
                scannedFiles++
                content.lineSequence().forEachIndexed { index, line ->
                    if (line.contains(query.trim(), ignoreCase = true)) {
                        matches.add(
                            mapOf(
                                "path" to path,
                                "line" to (index + 1),
                                "snippet" to line.take(KellvynAgentLimits.MAX_SEARCH_SNIPPET_CHARS)
                            )
                        )
                    }
                }
                if (matches.size >= KellvynAgentLimits.MAX_SEARCH_MATCHES) break
            }

            mapOf(
                "ok" to true,
                "projectId" to projectId,
                "query" to query.trim(),
                "matches" to matches.take(KellvynAgentLimits.MAX_SEARCH_MATCHES),
                "scannedFiles" to scannedFiles,
                "candidateFiles" to candidates.size,
                "totalProjectFiles" to allPaths.size,
                "truncated" to (
                    allPaths.size > candidates.size ||
                        matches.size >= KellvynAgentLimits.MAX_SEARCH_MATCHES
                    )
            )
        } catch (error: Exception) {
            failure("Não foi possível pesquisar neste projeto.", error)
        }
    }

    private fun toolBudgetError(): Map<String, Any>? {
        val callNumber = callsThisTurn.incrementAndGet()
        if (KellvynAgentLimits.allowsToolCall(callNumber)) return null
        return mapOf(
            "ok" to false,
            "error" to "Limite de consultas locais desta mensagem atingido.",
            "allowedCallsPerTurn" to KellvynAgentLimits.MAX_TOOL_CALLS_PER_TURN
        )
    }

    private fun failure(message: String, error: Exception): Map<String, Any> =
        mapOf(
            "ok" to false,
            "error" to message,
            "detail" to (error.message ?: error.javaClass.simpleName).take(500)
        )
}
