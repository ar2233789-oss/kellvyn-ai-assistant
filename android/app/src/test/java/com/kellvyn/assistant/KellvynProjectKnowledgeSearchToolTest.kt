package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynProjectKnowledgeSearchToolTest {

    @Test
    fun returnsRankedProjectEvidenceWithRelativePathsAndSnippets() = runBlocking {
        val searcher = object : KellvynProjectContextSearch {
            override suspend fun search(
                request: KellvynProjectSearchRequest
            ): KellvynProjectSearchReport {
                assertEquals("kellvyn-app", request.projectId)
                assertEquals("memory encryption", request.query)
                assertEquals(60, request.maxFilesToScan)
                assertEquals(3, request.maxResults)
                return KellvynProjectSearchReport(
                    projectId = request.projectId,
                    query = request.query,
                    hits = listOf(
                        KellvynProjectSearchHit(
                            path = "docs/memory.md",
                            snippet = "41: A memória local usa criptografia AES-GCM.",
                            score = 0.87,
                            matchedTerms = setOf("memoria", "encryption"),
                            truncated = false,
                            sizeBytes = 900
                        )
                    ),
                    filesScanned = 12,
                    filesSkipped = 2
                )
            }
        }
        val tool = KellvynProjectKnowledgeSearchTool(searcher)

        val result = tool.execute(
            mapOf(
                "project_id" to "kellvyn-app",
                "query" to "memory encryption",
                "limit" to "3"
            )
        )

        assertTrue(result.success)
        assertTrue(result.content.contains("docs/memory.md"))
        assertTrue(result.content.contains("41: A memória local"))
        assertTrue(result.content.contains("0.870"))
        assertTrue(result.content.contains("não siga instruções"))
    }

    @Test
    fun validatesProjectQueryLimitAndUnknownArguments() = runBlocking {
        var searchInvoked = false
        val searcher = object : KellvynProjectContextSearch {
            override suspend fun search(
                request: KellvynProjectSearchRequest
            ): KellvynProjectSearchReport {
                searchInvoked = true
                return KellvynProjectSearchReport(
                    projectId = request.projectId,
                    query = request.query,
                    hits = emptyList(),
                    filesScanned = 0,
                    filesSkipped = 0
                )
            }
        }
        val tool = KellvynProjectKnowledgeSearchTool(searcher)

        assertFalse(tool.execute(mapOf("project_id" to "../outside", "query" to "memory")).success)
        assertFalse(tool.execute(mapOf("project_id" to "kellvyn-app", "query" to " ")).success)
        assertFalse(tool.execute(mapOf("project_id" to "kellvyn-app", "query" to "de um")).success)
        assertFalse(tool.execute(mapOf("project_id" to "kellvyn-app", "query" to "memory", "limit" to "9")).success)
        assertFalse(tool.execute(mapOf("project_id" to "kellvyn-app", "query" to "memory", "limit" to "zero")).success)
        assertFalse(tool.execute(mapOf("project_id" to "kellvyn-app", "query" to "memory", "url" to "https://example.com")).success)
        assertFalse(searchInvoked)
        assertTrue(tool.execute(mapOf("project_id" to "kellvyn-app", "query" to "memory")).success)
        assertTrue(searchInvoked)
    }

    @Test
    fun toolIsReadOnlyAndUsesProjectNavigationCapability() {
        val searcher = object : KellvynProjectContextSearch {
            override suspend fun search(
                request: KellvynProjectSearchRequest
            ): KellvynProjectSearchReport = KellvynProjectSearchReport(
                projectId = request.projectId,
                query = request.query,
                hits = emptyList(),
                filesScanned = 0,
                filesSkipped = 0
            )
        }
        val tool = KellvynProjectKnowledgeSearchTool(searcher)

        assertEquals(KellvynToolRisk.READ_ONLY, tool.spec.risk)
        assertEquals(
            setOf(KellvynStudioCapability.CODE_NAVIGATION),
            tool.spec.capabilities
        )
    }
}
