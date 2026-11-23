package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynProjectContextRetrieverTest {
    @Test
    fun ranksRelevantFilesAndUsesPathMatches() {
        val documents = listOf(
            KellvynProjectSearchDocument(
                path = "docs/agent-memory.md",
                excerpt = "A memória do agente precisa guardar decisões e origem.",
                truncated = false,
                sizeBytes = 200
            ),
            KellvynProjectSearchDocument(
                path = "src/Theme.kt",
                excerpt = "A cor principal do aplicativo é verde.",
                truncated = false,
                sizeBytes = 100
            )
        )

        val hits = KellvynProjectSearchRanker.rank(
            documents,
            query = "memoria agente",
            limit = 5
        )

        assertEquals(1, hits.size)
        assertEquals("docs/agent-memory.md", hits.single().path)
        assertTrue(hits.single().matchedTerms.contains("memoria"))
        assertTrue(hits.single().score > 0.7)
    }

    @Test
    fun bm25UsesTermFrequencyInsteadOfOnlyBinaryTermOverlap() {
        val documents = listOf(
            KellvynProjectSearchDocument(
                path = "docs/single.md",
                excerpt = "cache attention",
                truncated = false,
                sizeBytes = 20
            ),
            KellvynProjectSearchDocument(
                path = "docs/repeated.md",
                excerpt = "cache attention cache cache cache attention attention attention",
                truncated = false,
                sizeBytes = 80
            )
        )

        val hits = KellvynProjectSearchRanker.rank(
            documents,
            query = "cache attention",
            limit = 2
        )

        assertEquals(2, hits.size)
        assertEquals("docs/repeated.md", hits.first().path)
        assertTrue(hits.first().score > hits.last().score)
    }

    @Test
    fun candidatePathsPrioritizeFilenameMatchesAndRemainDeterministic() {
        val paths = listOf(
            "bulk/decoy-01.md",
            "bulk/decoy-02.md",
            "docs/checkpoint-recovery.md",
            "src/AgentRuntime.kt"
        )

        val ranked = KellvynProjectPathRanker.rank(paths, "checkpoint recovery")
        val repeated = KellvynProjectPathRanker.rank(paths.reversed(), "checkpoint recovery")

        assertEquals("docs/checkpoint-recovery.md", ranked.first())
        assertEquals(ranked, repeated)
    }

    @Test
    fun searchNormalizesPortugueseAccents() {
        val documents = listOf(
            KellvynProjectSearchDocument(
                path = "docs/validacao.md",
                excerpt = "A verificação independente impede falsas conclusões.",
                truncated = false,
                sizeBytes = 120
            )
        )

        val hits = KellvynProjectSearchRanker.rank(
            documents,
            query = "verificacao independente",
            limit = 3
        )

        assertEquals(1, hits.size)
        assertEquals("docs/validacao.md", hits.single().path)
    }

    @Test
    fun portugueseSearchFindsEnglishNamedCamelCaseCode() {
        val documents = listOf(
            KellvynProjectSearchDocument(
                path = "src/KellvynProjectContextRetriever.kt",
                excerpt = """
                    class KellvynProjectContextRetriever {
                        fun searchWorkspace(projectId: String) = Unit
                    }
                """.trimIndent(),
                truncated = false,
                sizeBytes = 180
            ),
            KellvynProjectSearchDocument(
                path = "docs/recipe.md",
                excerpt = "Leite condensado, ovos e açúcar.",
                truncated = false,
                sizeBytes = 60
            )
        )

        val hits = KellvynProjectSearchRanker.rank(
            documents,
            query = "buscar arquivos do projeto",
            limit = 5
        )

        assertEquals(1, hits.size)
        assertEquals("src/KellvynProjectContextRetriever.kt", hits.single().path)
        assertTrue(hits.single().matchedTerms.contains("buscar"))
        assertTrue(hits.single().matchedTerms.contains("arquivos"))
        assertTrue(hits.single().matchedTerms.contains("projeto"))
    }

    @Test
    fun englishQueryFindsPortugueseDocumentationWithConservativeExpansion() {
        val documents = listOf(
            KellvynProjectSearchDocument(
                path = "docs/erros.md",
                excerpt = "A falha ocorre durante o cancelamento da corrotina.",
                truncated = false,
                sizeBytes = 100
            )
        )

        val hits = KellvynProjectSearchRanker.rank(
            documents,
            query = "exception cancellation coroutine",
            limit = 3
        )

        assertEquals(1, hits.size)
        assertTrue(hits.single().matchedTerms.contains("exception"))
        assertTrue(hits.single().matchedTerms.contains("cancellation"))
        assertTrue(hits.single().matchedTerms.contains("coroutine"))
    }

    @Test
    fun unrelatedFilesAreNotReturned() {
        val documents = listOf(
            KellvynProjectSearchDocument(
                path = "docs/receita.md",
                excerpt = "Leite condensado, ovos e açúcar.",
                truncated = false,
                sizeBytes = 60
            )
        )

        val hits = KellvynProjectSearchRanker.rank(
            documents,
            query = "arquitetura neural agente",
            limit = 3
        )

        assertTrue(hits.isEmpty())
    }

    @Test
    fun snippetContainsLineNumbersAndIsBounded() {
        val content = (1..80).joinToString("\n") { line ->
            if (line == 41) "A memória local usa criptografia." else "Linha de contexto $line."
        }
        val hits = KellvynProjectSearchRanker.rank(
            listOf(
                KellvynProjectSearchDocument(
                    path = "src/Memory.kt",
                    excerpt = content,
                    truncated = true,
                    sizeBytes = 20_000
                )
            ),
            query = "memoria criptografia",
            limit = 1
        )

        assertEquals(1, hits.size)
        assertTrue(hits.single().snippet.contains("41:"))
        assertTrue(hits.single().snippet.length <= 400)
        assertTrue(hits.single().truncated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun resultLimitCannotGrowWithoutBound() {
        KellvynProjectSearchRanker.rank(emptyList(), "agent", 21)
    }
}
