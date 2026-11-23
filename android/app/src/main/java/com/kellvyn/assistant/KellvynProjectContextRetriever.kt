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
    val sizeBytes: Long,
    val codeIndex: KellvynProjectCodeIndex? = null
)

internal data class KellvynProjectSearchHit(
    val path: String,
    val snippet: String,
    val score: Double,
    val matchedTerms: Set<String>,
    val truncated: Boolean,
    val sizeBytes: Long,
    val matchedIdentifiers: Set<String> = emptySet()
)

internal data class KellvynProjectSearchReport(
    val projectId: String,
    val query: String,
    val hits: List<KellvynProjectSearchHit>,
    val filesScanned: Int,
    val filesSkipped: Int
)

internal object KellvynProjectSearchRanker {
    private const val BM25_K1 = 1.2
    private const val BM25_B = 0.75
    private const val BM25_NORMALIZER = 1.0

    private data class PreparedDocument(
        val document: KellvynProjectSearchDocument,
        val bodyTokens: List<String>,
        val termFrequency: Map<String, Int>,
        val pathTerms: Set<String>,
        val identifierTerms: Set<String>
    )

    /**
     * BM25 body relevance fused with path and symbol matches.
     *
     * The corpus is deliberately bounded by the caller (normally <=60 files and <=12k chars
     * per file), so the statistics are computed in memory without a database or background
     * service. This improves ranking without changing the central neural model.
     */
    fun rank(
        documents: List<KellvynProjectSearchDocument>,
        query: String,
        limit: Int
    ): List<KellvynProjectSearchHit> {
        require(query.isNotBlank() && query.length <= 120)
        require(limit in 1..20)
        val queryTerms = KellvynMemoryRanker.tokens(query)
        if (queryTerms.isEmpty() || documents.isEmpty()) return emptyList()

        val prepared = documents.map { document ->
            val bodyTokens = tokenOccurrences(document.excerpt)
            val pathTerms = KellvynMemoryRanker.tokens(document.path)
            val identifierTerms = document.codeIndex?.searchableIdentifiers
                ?.flatMap { KellvynMemoryRanker.tokens(it) }
                ?.toSet()
                .orEmpty()
            PreparedDocument(
                document = document,
                bodyTokens = bodyTokens,
                termFrequency = bodyTokens.groupingBy { it }.eachCount(),
                pathTerms = pathTerms,
                identifierTerms = identifierTerms
            )
        }

        val totalDocuments = prepared.size.toDouble()
        val averageDocumentLength = prepared.sumOf { it.bodyTokens.size.toDouble() }
            .div(totalDocuments)
            .coerceAtLeast(1.0)
        val documentFrequency = queryTerms.associateWith { term ->
            prepared.count { term in it.termFrequency }
        }

        return prepared.mapNotNull { indexed ->
            val bodyTerms = indexed.termFrequency.keys
            val allTextTerms = indexed.pathTerms + bodyTerms
            val matched = queryTerms.intersect(
                allTextTerms + indexed.identifierTerms
            )
            if (matched.isEmpty()) return@mapNotNull null

            val bodyMatches = queryTerms.intersect(bodyTerms)
            val pathMatches = queryTerms.intersect(indexed.pathTerms)
            val textMatches = queryTerms.intersect(allTextTerms)
            val identifierMatches = queryTerms.intersect(indexed.identifierTerms)
            val denominator = queryTerms.size.toDouble()
            val textCoverage = textMatches.size.toDouble() / denominator
            val pathScore = pathMatches.size.toDouble() / denominator
            val identifierScore = identifierMatches.size.toDouble() / denominator

            val rawBm25 = queryTerms.sumOf { term ->
                val frequency = indexed.termFrequency[term] ?: 0
                if (frequency == 0) {
                    0.0
                } else {
                    val df = documentFrequency[term] ?: 0
                    val inverseDocumentFrequency = kotlin.math.ln(
                        1.0 + (totalDocuments - df + 0.5) / (df + 0.5)
                    )
                    val documentLength = indexed.bodyTokens.size.toDouble().coerceAtLeast(1.0)
                    val lengthNorm = frequency + BM25_K1 * (
                        1.0 - BM25_B + BM25_B * documentLength / averageDocumentLength
                    )
                    inverseDocumentFrequency *
                        (frequency * (BM25_K1 + 1.0) / lengthNorm)
                }
            }
            val normalizedBm25 = rawBm25 / (rawBm25 + BM25_NORMALIZER)
            val score = (
                0.65 * textCoverage +
                    0.25 * normalizedBm25 +
                    0.08 * pathScore +
                    0.02 * identifierScore
                ).coerceIn(0.0, 1.0)

            KellvynProjectSearchHit(
                path = indexed.document.path,
                snippet = bestSnippet(indexed.document.excerpt, queryTerms).take(400),
                score = score,
                matchedTerms = matched,
                truncated = indexed.document.truncated,
                sizeBytes = indexed.document.sizeBytes,
                matchedIdentifiers = identifierMatches
            )
        }.sortedWith(
            compareByDescending<KellvynProjectSearchHit> { it.score }
                .thenBy { it.path }
        ).take(limit)
    }

    private fun tokenOccurrences(value: String): List<String> {
        val normalized = java.text.Normalizer.normalize(
            value,
            java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "").lowercase(java.util.Locale.ROOT)
        return Regex("[\\p{L}\\p{N}]{2,}").findAll(normalized)
            .map { it.value }
            .toList()
    }

    private fun bestSnippet(content: String, queryTerms: Set<String>): String {
        val lines = content.lines()
        if (lines.isEmpty()) return content.take(400)
        val best = lines.mapIndexed { index, line ->
            index to KellvynMemoryRanker.tokens(line).intersect(queryTerms).size
        }.maxByOrNull { it.second }
        val selected = best?.takeIf { it.second > 0 }?.first ?: 0
        val start = (selected - 1).coerceAtLeast(0)
        val end = (selected + 2).coerceAtMost(lines.lastIndex)
        return (start..end).joinToString("\n") { lineIndex ->
            (lineIndex + 1).toString() + ": " + lines[lineIndex]
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
                    val codeIndex = if (
                        path.endsWith(".kt", ignoreCase = true) ||
                        path.endsWith(".java", ignoreCase = true)
                    ) {
                        KellvynProjectCodeIndexer.index(path, excerpt.content)
                    } else {
                        null
                    }
                    documents += KellvynProjectSearchDocument(
                        path = excerpt.path,
                        excerpt = excerpt.content,
                        truncated = excerpt.truncated,
                        sizeBytes = excerpt.sizeBytes,
                        codeIndex = codeIndex
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
