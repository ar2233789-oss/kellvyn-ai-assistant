package com.kellvyn.assistant

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import java.util.Locale
import kotlinx.coroutines.CancellationException
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

internal object KellvynProjectSearchLexicon {
    private const val TRANSLATED_TERM_WEIGHT = 0.35

    private val STOP_WORDS = setOf(
        "a", "as", "ao", "aos", "com", "da", "das", "de", "do", "dos", "e",
        "em", "na", "nas", "no", "nos", "o", "os", "ou", "para", "por",
        "que", "um", "uma", "the", "and", "for", "from", "into", "is", "it",
        "of", "on", "or", "to", "with", "how", "what", "this", "that"
    )

    private val synonymGroups = listOf(
        listOf("arquivo", "arquivos", "file", "files", "document", "documento", "documentos"),
        listOf("projeto", "projetos", "project", "workspace"),
        listOf("codigo", "code", "source"),
        listOf("busca", "buscar", "procurar", "pesquisar", "pesquisa", "search", "find", "lookup"),
        listOf("erro", "falha", "excecao", "problema", "bug", "error", "failure", "exception", "crash", "issue"),
        listOf("cancelamento", "cancelar", "cancellation", "cancel"),
        listOf("corrotina", "coroutines", "coroutine"),
        listOf("travamento", "travar", "congelamento", "freeze", "hang"),
        listOf("desempenho", "lentidao", "lento", "performance", "latency", "slow"),
        listOf("memoria", "memory"),
        listOf("seguranca", "security"),
        listOf("teste", "testes", "tests", "test"),
        listOf("compilar", "compilacao", "compile", "compilation", "build"),
        listOf("dependencia", "dependencias", "dependency", "dependencies"),
        listOf("funcao", "funcoes", "function", "functions", "method", "metodo", "metodos"),
        listOf("classe", "classes", "class"),
        listOf("interface", "tela", "screen", "ui"),
        listOf("configuracao", "configuracoes", "configuration", "config"),
        listOf("ferramenta", "ferramentas", "tool", "tools"),
        listOf("artigo", "artigos", "paper", "papers"),
        listOf("cientifico", "cientifica", "scientific", "research", "paper", "papers", "artigo", "artigos")
    )

    private val synonymsByTerm: Map<String, Set<String>> = buildMap {
        synonymGroups.forEach { group ->
            val normalized = group.toSet()
            group.forEach { term -> put(term, normalized - term) }
        }
    }

    /** Splits camelCase, PascalCase and acronyms before normalizing accents. */
    fun occurrences(value: String): List<String> {
        val separated = value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
        val normalized = java.text.Normalizer.normalize(
            separated,
            java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "").lowercase(java.util.Locale.ROOT)
        return Regex("[\\p{L}\\p{N}]{2,}").findAll(normalized)
            .map { it.value }
            .filterNot { it in STOP_WORDS }
            .toList()
    }

    fun tokens(value: String): Set<String> = occurrences(value).toCollection(LinkedHashSet())

    fun weightedQueryTerms(queryTerms: Set<String>): Map<String, Double> {
        val weights = LinkedHashMap<String, Double>()
        queryTerms.sorted().forEach { term ->
            weights[term] = 1.0
            synonymsByTerm[term].orEmpty().forEach { synonym ->
                weights[synonym] = maxOf(weights[synonym] ?: 0.0, TRANSLATED_TERM_WEIGHT)
            }
        }
        return weights
    }

    fun expandedTerms(queryTerms: Set<String>): Set<String> =
        weightedQueryTerms(queryTerms).keys

    /** Returns original query terms that matched directly or through one translation. */
    fun matchingQueryTerms(queryTerms: Set<String>, documentTerms: Set<String>): Set<String> =
        queryTerms.filterTo(LinkedHashSet()) { queryTerm ->
            queryTerm in documentTerms ||
                synonymsByTerm[queryTerm].orEmpty().any { it in documentTerms }
        }

    /** Exact matches score 1.0; translated matches are weaker evidence. */
    fun coverage(queryTerms: Set<String>, documentTerms: Set<String>): Double {
        if (queryTerms.isEmpty()) return 0.0
        val coveredWeight = queryTerms.sumOf { queryTerm ->
            when {
                queryTerm in documentTerms -> 1.0
                synonymsByTerm[queryTerm].orEmpty().any { it in documentTerms } ->
                    TRANSLATED_TERM_WEIGHT
                else -> 0.0
            }
        }
        return coveredWeight / queryTerms.size.toDouble()
    }
}

internal object KellvynProjectPathRanker {
    /**
     * Prioritizes candidate files by query-term coverage in their filename and full path.
     * Filename matches weigh twice as much as directory matches. Ties remain deterministic.
     */
    fun rank(paths: List<String>, query: String): List<String> {
        val queryTerms = KellvynProjectSearchLexicon.tokens(query)
        return paths.distinct().map { path ->
            val normalized = path.replace('\\', '/')
            val fileName = normalized.substringAfterLast('/').substringBeforeLast('.')
            val fileTerms = KellvynProjectSearchLexicon.tokens(fileName)
            val pathTerms = KellvynProjectSearchLexicon.tokens(normalized)
            val fileCoverage = KellvynProjectSearchLexicon.coverage(queryTerms, fileTerms)
            val pathCoverage = KellvynProjectSearchLexicon.coverage(queryTerms, pathTerms)
            val score = (2.0 * fileCoverage + pathCoverage) / 3.0
            path to score
        }.sortedWith(
            compareByDescending<Pair<String, Double>> { it.second }
                .thenBy { it.first }
        ).map { it.first }
    }
}

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
     * BM25 body relevance fused with path and symbol matches plus conservative
     * Portuguese/English query expansion. Exact query terms weigh 1.0; translations
     * weigh 0.35 and never become instructions or new actions.
     *
     * The corpus is deliberately bounded by the caller (normally <=60 files and <=12k chars
     * per file), so the statistics are computed in memory without a database or background
     * service. This improves retrieval without changing the central neural model.
     */
    fun rank(
        documents: List<KellvynProjectSearchDocument>,
        query: String,
        limit: Int
    ): List<KellvynProjectSearchHit> {
        require(query.isNotBlank() && query.length <= 120)
        require(limit in 1..20)
        val queryTerms = KellvynProjectSearchLexicon.tokens(query)
        if (queryTerms.isEmpty() || documents.isEmpty()) return emptyList()
        val weightedQuery = KellvynProjectSearchLexicon.weightedQueryTerms(queryTerms)
        val expandedQueryTerms = weightedQuery.keys

        val prepared = documents.map { document ->
            val bodyTokens = KellvynProjectSearchLexicon.occurrences(document.excerpt)
            val pathTerms = KellvynProjectSearchLexicon.tokens(document.path)
            val identifierTerms = document.codeIndex?.searchableIdentifiers
                ?.flatMap { KellvynProjectSearchLexicon.occurrences(it) }
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
        val documentFrequency = expandedQueryTerms.associateWith { term ->
            prepared.count { term in it.termFrequency }
        }

        return prepared.mapNotNull { indexed ->
            val bodyTerms = indexed.termFrequency.keys
            val allTextTerms = indexed.pathTerms + bodyTerms
            val allSearchableTerms = allTextTerms + indexed.identifierTerms
            val matched = KellvynProjectSearchLexicon.matchingQueryTerms(
                queryTerms,
                allSearchableTerms
            )
            if (matched.isEmpty()) return@mapNotNull null

            val identifierMatches = KellvynProjectSearchLexicon.matchingQueryTerms(
                queryTerms,
                indexed.identifierTerms
            )
            // Translated matches count as weaker evidence than exact lexical matches.
            val textCoverage = KellvynProjectSearchLexicon.coverage(queryTerms, allTextTerms)
            val pathScore = KellvynProjectSearchLexicon.coverage(queryTerms, indexed.pathTerms)
            val identifierScore = KellvynProjectSearchLexicon.coverage(
                queryTerms,
                indexed.identifierTerms
            )

            val rawBm25 = weightedQuery.entries.sumOf { (term, queryWeight) ->
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
                        (frequency * (BM25_K1 + 1.0) / lengthNorm) * queryWeight
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
                snippet = bestSnippet(indexed.document.excerpt, expandedQueryTerms).take(400),
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

    private fun bestSnippet(content: String, queryTerms: Set<String>): String {
        val lines = content.lines()
        if (lines.isEmpty()) return content.take(400)
        val best = lines.mapIndexed { index, line ->
            index to KellvynProjectSearchLexicon.tokens(line).intersect(queryTerms).size
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
 * Bounded project retrieval. It excludes .git, unsupported/binary types and known
 * secret-bearing paths; allowed text excerpts are screened for credential-like content.
 * Results are data only and must enter model context as UNTRUSTED.
 */
internal class KellvynProjectContextRetriever(
    private val workspace: ProjectWorkspaceStore
) : KellvynProjectContextSearch {
    override suspend fun search(request: KellvynProjectSearchRequest): KellvynProjectSearchReport =
        withContext(Dispatchers.IO) {
            require(request.projectId.matches(Regex("[a-z0-9][a-z0-9-]{0,60}")))
            require(request.query.trim().length in 2..120)
            require(KellvynProjectSearchLexicon.tokens(request.query).isNotEmpty()) {
                "A consulta não contém termos úteis para busca."
            }
            require(request.maxFilesToScan in 1..60)
            require(request.maxResults in 1..20)

            val files = workspace.listFiles(request.projectId)
            // Filter unsafe and unsupported paths before applying the scan budget. Otherwise,
            // an alphabetically early block of binary/secret files can crowd out useful sources.
            val eligible = files.filter { path ->
                isAllowedPath(path) && KellvynAgentLimits.isSupportedTextFile(path)
            }
            val selected = KellvynProjectPathRanker.rank(
                eligible,
                request.query
            ).take(request.maxFilesToScan)
            var skipped = (files.size - selected.size).coerceAtLeast(0)
            val documents = mutableListOf<KellvynProjectSearchDocument>()
            var scanned = 0

            selected.forEach { path ->
                try {
                    val excerpt = workspace.readFileExcerpt(
                        projectId = request.projectId,
                        relativePath = path,
                        maxChars = MAX_EXCERPT_CHARS
                    )
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
                    scanned++
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
            val value = segment.lowercase(Locale.ROOT)
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
