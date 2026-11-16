package com.kellvyn.assistant

import java.text.Normalizer
import java.util.Locale

internal enum class KellvynMemoryKind {
    USER_PREFERENCE,
    PROJECT_FACT,
    PROJECT_DECISION,
    TASK_EPISODE,
    SESSION_SUMMARY
}

internal enum class KellvynMemorySource {
    USER_EXPLICIT,
    TOOL_VERIFIED,
    MODEL_INFERENCE,
    WEB_UNVERIFIED,
    EXTERNAL_CONTENT
}

internal enum class KellvynMemoryScope {
    GLOBAL,
    PROJECT,
    CONVERSATION,
    TASK
}

internal data class KellvynMemoryAddress(
    val scope: KellvynMemoryScope,
    val scopeId: String
) {
    init {
        require(scopeId.isNotBlank() && scopeId.length <= 120)
        require(scopeId.none { it == '\u0000' || it.isISOControl() })
        if (scope == KellvynMemoryScope.GLOBAL) {
            require(scopeId == GLOBAL_ID)
        } else {
            require(scopeId != GLOBAL_ID)
        }
    }

    companion object {
        const val GLOBAL_ID = "global"
        fun global() = KellvynMemoryAddress(KellvynMemoryScope.GLOBAL, GLOBAL_ID)
        fun project(projectId: String) = KellvynMemoryAddress(KellvynMemoryScope.PROJECT, projectId)
    }
}

internal data class KellvynMemoryEntry(
    val id: String,
    val address: KellvynMemoryAddress,
    val kind: KellvynMemoryKind,
    val title: String,
    val content: String,
    val tags: List<String>,
    val source: KellvynMemorySource,
    val provenance: String,
    val confidence: Double,
    val createdAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val userApproved: Boolean
)

internal data class KellvynMemoryQuery(
    val text: String,
    val addresses: List<KellvynMemoryAddress>,
    val nowMillis: Long,
    val limit: Int = 8
)

internal data class KellvynMemoryMatch(
    val entry: KellvynMemoryEntry,
    val score: Double,
    val matchedTerms: Set<String>
)

internal sealed class KellvynMemoryWriteResult {
    data class Stored(val id: String) : KellvynMemoryWriteResult()
    data class Rejected(val reason: String) : KellvynMemoryWriteResult()
}

internal interface KellvynMemoryRepository {
    suspend fun put(entry: KellvynMemoryEntry)
    suspend fun loadCandidates(query: KellvynMemoryQuery): List<KellvynMemoryEntry>
    suspend fun delete(id: String, address: KellvynMemoryAddress): Boolean
    suspend fun deleteScope(address: KellvynMemoryAddress): Int
    suspend fun purgeExpired(nowMillis: Long): Int
}

internal object KellvynMemoryPolicy {
    private const val MAX_CONTENT_CHARS = 8_000
    private const val MAX_TITLE_CHARS = 120
    private const val MAX_TAGS = 12
    private const val MAX_TAG_CHARS = 40
    private const val MAX_PROVENANCE_CHARS = 240

    private val secretAssignments = Regex(
        """(?i)\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\b\s*[:=]\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
    )
    private val bearerSecret = Regex("""(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}""")
    private val privateKey = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")
    private val commonToken = Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{16,})\b""")

    fun rejectionReason(entry: KellvynMemoryEntry, nowMillis: Long): String? {
        if (!entry.id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) return "Identificador inválido."
        if (entry.title.isBlank() || entry.title.length > MAX_TITLE_CHARS) return "Título vazio ou longo demais."
        if (entry.content.isBlank() || entry.content.length > MAX_CONTENT_CHARS) return "Conteúdo vazio ou acima do limite."
        if (entry.tags.size > MAX_TAGS || entry.tags.any { it.isBlank() || it.length > MAX_TAG_CHARS }) {
            return "Tags vazias ou acima do limite."
        }
        if (entry.provenance.isBlank() || entry.provenance.length > MAX_PROVENANCE_CHARS) {
            return "A origem precisa ser registrada."
        }
        if (!entry.confidence.isFinite() || entry.confidence !in 0.0..1.0) return "Confiança inválida."
        if (entry.createdAtMillis <= 0L || entry.createdAtMillis > nowMillis + 300_000L) {
            return "Data de criação inválida."
        }
        if (entry.expiresAtMillis != null && entry.expiresAtMillis <= nowMillis) {
            return "A memória já está expirada."
        }
        if (entry.source !in setOf(KellvynMemorySource.USER_EXPLICIT, KellvynMemorySource.TOOL_VERIFIED)) {
            return "Inferências e conteúdo externo não podem virar memória duradoura automaticamente."
        }
        if (!entry.userApproved) return "É necessária aprovação explícita do proprietário."
        val searchable = entry.title + "\n" + entry.content + "\n" + entry.tags.joinToString(" ")
        if (secretAssignments.containsMatchIn(searchable) ||
            bearerSecret.containsMatchIn(searchable) ||
            privateKey.containsMatchIn(searchable) ||
            commonToken.containsMatchIn(searchable)
        ) {
            return "O conteúdo parece conter credenciais ou chaves e foi recusado."
        }
        return null
    }
}

internal object KellvynMemoryRanker {
    private const val MAX_ADDRESSES = 8
    private const val MAX_QUERY_CHARS = 160

    fun rank(
        entries: List<KellvynMemoryEntry>,
        query: KellvynMemoryQuery
    ): List<KellvynMemoryMatch> {
        require(query.text.isNotBlank() && query.text.length <= MAX_QUERY_CHARS)
        require(query.addresses.isNotEmpty() && query.addresses.size <= MAX_ADDRESSES)
        require(query.limit in 1..20)
        require(query.nowMillis > 0L)

        val allowedAddresses = query.addresses.toSet()
        val terms = tokens(query.text)
        if (terms.isEmpty()) return emptyList()

        return entries.asSequence()
            .filter { it.address in allowedAddresses }
            .filter { it.expiresAtMillis == null || it.expiresAtMillis > query.nowMillis }
            .mapNotNull { entry ->
                val titleTerms = tokens(entry.title)
                val bodyTerms = tokens(entry.content)
                val tagTerms = entry.tags.flatMap(::tokens).toSet()
                val available = titleTerms + bodyTerms + tagTerms
                val matched = terms.intersect(available)
                if (matched.isEmpty()) return@mapNotNull null

                val overlap = matched.size.toDouble() / terms.size.toDouble()
                val titleBonus = if (titleTerms.containsAll(terms)) 0.12 else 0.0
                val tagBonus = if (tagTerms.intersect(terms).isNotEmpty()) 0.06 else 0.0
                val ageDays = ((query.nowMillis - entry.createdAtMillis).coerceAtLeast(0L) / DAY_MS).toDouble()
                val freshness = 1.0 / (1.0 + ageDays / 30.0)
                val score = (
                    0.70 * overlap +
                        0.14 * entry.confidence +
                        0.10 * freshness +
                        titleBonus +
                        tagBonus
                    ).coerceIn(0.0, 1.0)

                KellvynMemoryMatch(entry, score, matched)
            }
            .sortedWith(
                compareByDescending<KellvynMemoryMatch> { it.score }
                    .thenByDescending { it.entry.createdAtMillis }
                    .thenBy { it.entry.id }
            )
            .take(query.limit)
            .toList()
    }

    internal fun tokens(value: String): Set<String> {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
        return Regex("[\\p{L}\\p{N}]{2,}").findAll(normalized)
            .map { it.value }
            .toSet()
    }

    private const val DAY_MS = 86_400_000L
}

internal class KellvynMemory(private val repository: KellvynMemoryRepository) {
    suspend fun remember(entry: KellvynMemoryEntry, nowMillis: Long): KellvynMemoryWriteResult {
        val rejection = KellvynMemoryPolicy.rejectionReason(entry, nowMillis)
        if (rejection != null) return KellvynMemoryWriteResult.Rejected(rejection)
        repository.put(entry)
        return KellvynMemoryWriteResult.Stored(entry.id)
    }

    suspend fun recall(query: KellvynMemoryQuery): List<KellvynMemoryMatch> {
        val candidates = repository.loadCandidates(query)
        return KellvynMemoryRanker.rank(candidates, query)
    }

    suspend fun forget(id: String, address: KellvynMemoryAddress): Boolean =
        repository.delete(id, address)

    suspend fun forgetScope(address: KellvynMemoryAddress): Int =
        repository.deleteScope(address)

    suspend fun purgeExpired(nowMillis: Long): Int {
        require(nowMillis > 0L)
        return repository.purgeExpired(nowMillis)
    }
}
