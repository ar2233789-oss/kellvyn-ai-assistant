package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynAcademicResearchStudioTest {

    @Test
    fun webAccessGateBlocksProviderBeforeAnyNetworkWork() = runBlocking {
        var called = false
        val studio = KellvynAcademicResearchStudio.testModule(
            accessEnabled = { false },
            searchProvider = KellvynAcademicSearchProvider { _, _ ->
                called = true
                KellvynAcademicSearchBatch(emptyList())
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "transformers"))

        assertFalse(result.success)
        assertFalse(called)
        assertTrue(result.content.contains("Acesso à Web está desativado"))
    }

    @Test
    fun returnsCitableMetadataAndMergesDuplicateDoiRecords() = runBlocking {
        val workOpenAlex = KellvynAcademicWork(
            source = "OpenAlex",
            title = "Efficient Local Language Models",
            doi = "10.1234/example.2026",
            year = 2026,
            authors = listOf("Ana Silva"),
            venue = "Journal of Local AI",
            abstractExcerpt = "A short abstract excerpt.",
            openAccess = true,
            sourceRecordUrl = "https://doi.org/10.1234/example.2026"
        )
        val workCrossref = workOpenAlex.copy(
            source = "Crossref",
            authors = emptyList(),
            venue = null,
            abstractExcerpt = null,
            openAccess = null
        )
        val studio = KellvynAcademicResearchStudio.testModule(
            accessEnabled = { true },
            searchProvider = KellvynAcademicSearchProvider { _, _ ->
                KellvynAcademicSearchBatch(listOf(workOpenAlex, workCrossref))
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "local language models"))

        assertTrue(result.success)
        assertTrue(result.content.contains("Efficient Local Language Models"))
        assertTrue(result.content.contains("10.1234/example.2026"))
        assertTrue(result.content.contains("OpenAlex+Crossref"))
        assertTrue(result.content.contains("Ana Silva"))
        assertTrue(result.content.contains("A short abstract excerpt."))
    }

    @Test
    fun validatesUnknownArgumentsQueryLengthAndResultLimit() = runBlocking {
        val studio = KellvynAcademicResearchStudio.testModule(
            accessEnabled = { true },
            searchProvider = KellvynAcademicSearchProvider { _, _ ->
                KellvynAcademicSearchBatch(emptyList())
            }
        )
        val tool = studio.tools.single()

        assertFalse(tool.execute(mapOf("url" to "https://example.com")).success)
        assertFalse(tool.execute(mapOf("query" to " ")).success)
        assertFalse(tool.execute(mapOf("query" to "x".repeat(181))).success)
        assertFalse(tool.execute(mapOf("query" to "model", "limit" to "6")).success)
        assertFalse(tool.execute(mapOf("query" to "model", "limit" to "zero")).success)
        assertTrue(tool.execute(mapOf("query" to "model", "limit" to "5")).success)
    }

    @Test
    fun exposesOnlyReadOnlyWebResearchCapability() {
        val studio = KellvynAcademicResearchStudio.testModule(
            accessEnabled = { true },
            searchProvider = KellvynAcademicSearchProvider { _, _ ->
                KellvynAcademicSearchBatch(emptyList())
            }
        )
        val tool = studio.tools.single()

        assertEquals(KellvynToolRisk.NETWORK_READ, tool.spec.risk)
        assertEquals(setOf(KellvynStudioCapability.WEB_RESEARCH), tool.spec.capabilities)
        assertTrue(studio.manifest.capabilities.containsAll(tool.spec.capabilities))
        assertTrue(studio.manifest.requiresNetwork)
        assertEquals(setOf(KellvynStudioPermission.NETWORK_ACCESS), studio.manifest.permissions)
    }

    @Test
    fun reportsPartialSourceFailureWithoutDiscardingSuccessfulResults() = runBlocking {
        val studio = KellvynAcademicResearchStudio.testModule(
            accessEnabled = { true },
            searchProvider = KellvynAcademicSearchProvider { _, _ ->
                KellvynAcademicSearchBatch(
                    works = listOf(
                        KellvynAcademicWork(
                            source = "Crossref",
                            title = "Verified Metadata",
                            doi = "10.2345/metadata",
                            year = 2025,
                            authors = emptyList(),
                            venue = null,
                            abstractExcerpt = null,
                            openAccess = null,
                            sourceRecordUrl = "https://doi.org/10.2345/metadata"
                        )
                    ),
                    warnings = listOf("OpenAlex: HTTP 429")
                )
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "verified metadata"))

        assertTrue(result.success)
        assertTrue(result.content.contains("Verified Metadata"))
        assertTrue(result.content.contains("OpenAlex: HTTP 429"))
    }

    @Test
    fun providerFailureIsReturnedAsFailureRatherThanFabricatedEmptyResult() = runBlocking {
        val studio = KellvynAcademicResearchStudio.testModule(
            accessEnabled = { true },
            searchProvider = KellvynAcademicSearchProvider { _, _ ->
                throw IllegalStateException("both sources unavailable")
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "any research"))

        assertFalse(result.success)
        assertTrue(result.content.contains("both sources unavailable"))
    }
}
