package com.kellvyn.assistant

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class KellvynProjectSearchRequest(
    val projectId: String,
    val query: String,
    val maxFilesToScan: Int = 60,
    val maxResults: Int = 8
)

internal data class KellvynProjectSearchDocument(
    val path: String,
    val excerpt: String,
    val truncated: Boolean,
    val sizeBytes: Long
)

internal data class KellvynProjectSearchHit(
    val path: String,
    val snippet: String,
    val score: Double,
    val matchedTerms: Set<String>,
    val truncated: Boolean,
    val sizeBytes: Long
)

internal data class KellvynProjectSearchReport(
    val projectId: String,
    val query: String,
    val hits: List<KellvynProjectSearchHit>,
    val filesScanned: Int,
    val filesSkipped: Int
)

internal object KellvynProjectSearchRanker {
    fun rank(
        documents: List<KellvynProjectSearchDocument>,
        query: String,
        limit: Int
    ): List<KellvynProjectSearchHit> {
        require(query.isNotBlank() && query.length <= 120)
        require(limit in 1..20)
        val queryTerms = KellvynMemoryRanker.tokens(query)
        if (queryTerms.isEmpty()) return emptyList()

        return documents.mapNotNull { document ->
            val pathTerms = KellvynMemoryRanker.tokens(document.path)
            val contentTerms = KellvynMemoryRanker.tokens(document.excerpt)
            val matched = queryTerms.intersect(pathTerms + contentTerms)
            if (matched.isEmpty()) return@mapNotNull null

            val pathMatches = queryTerms.intersect(pathTerms)
            val overlap = matched.size.toDouble() / queryTerms.size.toDouble()
            val pathScore = pathMatches.size.toDouble() / queryTerms.size.toDouble()
            val score = (0.78 * overlap + 0.22 * pathScore).coerceIn(0.0, 1.0)
            val snippet = bestSnippet(document.excerpt, queryTerms)

            KellvynProjectSearchHit(
                path = document.path,
                snippet = snippet.take(400),
                score = score,
                matchedTerms = matched,
                truncated = document.truncated,
                sizeBytes = document.sizeBytes
            )
        }.sortedWith(
            compareByDescending<KellvynProjectSearchHit> { it.score }
                .thenBy { it.path }
        ).take(limit)
    }

    private fun bestSnippet(content: String, queryTerms: Set<String>): String {
        val lines = content.lines()
        if (lines.isEmpty()) return content.take(400)
        val best = lines.mapIndexed { index, line ->
            val terms = KellvynMemoryRanker.tokens(line)
            index to terms.intersect(queryTerms).size
        }.maxByOrNull { it.second }
        val selected = best?.takeIf { it.second > 0 }?.first ?: 0
        val start = (selected - 1).coerceAtLeast(0)
        val end = (selected + 2).coerceAtMost(lines.lastIndex)
        return (start..end).joinToString("\n") { lineIndex ->
            "${lineIndex + 1}: ${lines[lineIndex]}"
        }
    }
}

internal interface KellvynProjectContextSearch {
    suspend fun search(request: KellvynProjectSearchRequest): KellvynProjectSearchReport
}
/**
 * Bounded project retrieval. It never scans .git, binary/unsupported files, known
 * secret-bearing paths, or files whose content resembles credentials.
 * Results are data only and must enter model context as UNTRUSTED.
 */
internal class KellvynProjectContextRetriever(
    private val workspace: ProjectWorkspaceStore
) : KellvynProjectContextSearch {
    override suspend fun search(request: KellvynProjectSearchRequest): KellvynProjectSearchReport =
        withContext(Dispatchers.IO) {
            require(request.projectId.matches(Regex("[a-z0-9][a-z0-9-]{0,60}")))
            require(request.query.trim().length in 2..120)
            require(request.maxFilesToScan in 1..60)
            require(request.maxResults in 1..20)

            val files = workspace.listFiles(request.projectId)
            val selected = files.take(request.maxFilesToScan)
            var skipped = (files.size - selected.size).coerceAtLeast(0)
            val documents = mutableListOf<KellvynProjectSearchDocument>()
            var scanned = 0

            selected.forEach { path ->
                if (!isAllowedPath(path) || !KellvynAgentLimits.isSupportedTextFile(path)) {
                    skipped++
                    return@forEach
                }

                try {
                    val excerpt = workspace.readFileExcerpt(
                        projectId = request.projectId,
                        relativePath = path,
                        maxChars = MAX_EXCERPT_CHARS
                    )
                    scanned++
                    if (containsSecretMaterial(excerpt.content)) {
                        skipped++
                        return@forEach
                    }
                    documents += KellvynProjectSearchDocument(
                        path = excerpt.path,
                        excerpt = excerpt.content,
                        truncated = excerpt.truncated,
                        sizeBytes = excerpt.sizeBytes
                    )
                } catch (_: Exception) {
                    skipped++
                }
            }

            KellvynProjectSearchReport(
                projectId = request.projectId,
                query = request.query,
                hits = KellvynProjectSearchRanker.rank(
                    documents,
                    request.query.trim(),
                    request.maxResults
                ),
                filesScanned = scanned,
                filesSkipped = skipped
            )
        }

    private fun isAllowedPath(path: String): Boolean {
        val segments = path.replace('\\', '/').split('/')
        return segments.none { segment ->
            val value = segment.lowercase()
            value == ".git" ||
                value == ".env" ||
                value.startsWith(".env.") ||
                value.contains("secret") ||
                value.contains("credential") ||
                value.contains("keystore") ||
                value == "local.properties" ||
                value == "gradle.properties" ||
                value.endsWith(".jks") ||
                value.endsWith(".keystore") ||
                value.endsWith(".p12") ||
                value.endsWith(".pem") ||
                value == "id_rsa" ||
                value.endsWith(".key")
        }
    }

    private fun containsSecretMaterial(content: String): Boolean =
        SECRET_ASSIGNMENT.containsMatchIn(content) ||
            BEARER_TOKEN.containsMatchIn(content) ||
            PRIVATE_KEY.containsMatchIn(content) ||
            KNOWN_TOKEN.containsMatchIn(content)

    companion object {
        private const val MAX_EXCERPT_CHARS = 12_000
        private val SECRET_ASSIGNMENT = Regex(
            """(?i)\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\b\s*[:=]\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
        )
        private val BEARER_TOKEN = Regex("""(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}""")
        private val PRIVATE_KEY = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")
        private val KNOWN_TOKEN = Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{16,})\b""")
    }
}
