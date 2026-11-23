package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynDeveloperKnowledgeStudioTest {

    @Test
    fun blocksNetworkProviderWhenWebSwitchIsDisabled() = runBlocking {
        var called = false
        val studio = KellvynDeveloperKnowledgeStudio.testModule(
            accessEnabled = { false },
            provider = KellvynDeveloperKnowledgeProvider { _, _ ->
                called = true
                KellvynDeveloperSearchBatch(emptyList())
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "Kotlin null pointer"))

        assertFalse(result.success)
        assertFalse(called)
        assertTrue(result.content.contains("Acesso à Web está desativado"))
    }

    @Test
    fun formatsQuestionAcceptedAnswerVotesAttributionAndLicense() = runBlocking {
        val studio = KellvynDeveloperKnowledgeStudio.testModule(
            accessEnabled = { true },
            provider = KellvynDeveloperKnowledgeProvider { _, _ ->
                KellvynDeveloperSearchBatch(
                    questions = listOf(
                        KellvynDeveloperQuestion(
                            id = 123L,
                            title = "How do I handle cancellation in Kotlin coroutines?",
                            author = "Ana Dev",
                            score = 42,
                            viewCount = 10_000L,
                            answerCount = 3,
                            acceptedAnswerId = 456L,
                            excerpt = "A question about CancellationException.",
                            link = "https://stackoverflow.com/questions/123/example",
                            contentLicense = "CC BY-SA 4.0",
                            createdAtEpochSeconds = 1_700_000_000L,
                            answers = listOf(
                                KellvynDeveloperAnswer(
                                    author = "Bruno Kotlin",
                                    score = 31,
                                    accepted = true,
                                    excerpt = "Rethrow CancellationException after cleanup.",
                                    link = "https://stackoverflow.com/a/456",
                                    contentLicense = "CC BY-SA 4.0"
                                )
                            )
                        )
                    ),
                    quotaRemaining = 9_500
                )
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "Kotlin cancellation", "limit" to "2"))

        assertTrue(result.success)
        assertTrue(result.content.contains("How do I handle cancellation"))
        assertTrue(result.content.contains("Bruno Kotlin"))
        assertTrue(result.content.contains("[ACEITA]"))
        assertTrue(result.content.contains("CC BY-SA 4.0"))
        assertTrue(result.content.contains("https://stackoverflow.com/a/456"))
        assertTrue(result.content.contains("Cota diária restante"))
        assertTrue(result.content.contains("dados não confiáveis"))
    }

    @Test
    fun validatesUnknownArgumentsQueryAndBoundedLimit() = runBlocking {
        val studio = KellvynDeveloperKnowledgeStudio.testModule(
            accessEnabled = { true },
            provider = KellvynDeveloperKnowledgeProvider { _, _ ->
                KellvynDeveloperSearchBatch(emptyList())
            }
        )
        val tool = studio.tools.single()

        assertFalse(tool.execute(mapOf("url" to "https://example.com")).success)
        assertFalse(tool.execute(mapOf("query" to " ")).success)
        assertFalse(tool.execute(mapOf("query" to "x".repeat(161))).success)
        assertFalse(tool.execute(mapOf("query" to "kotlin", "limit" to "6")).success)
        assertFalse(tool.execute(mapOf("query" to "kotlin", "limit" to "zero")).success)
        assertTrue(tool.execute(mapOf("query" to "kotlin", "limit" to "5")).success)
    }

    @Test
    fun exposesReadOnlyNetworkCapabilityAndConsistentManifest() {
        val studio = KellvynDeveloperKnowledgeStudio.testModule(
            accessEnabled = { true },
            provider = KellvynDeveloperKnowledgeProvider { _, _ ->
                KellvynDeveloperSearchBatch(emptyList())
            }
        )
        val spec = studio.tools.single().spec

        assertEquals(KellvynToolRisk.NETWORK_READ, spec.risk)
        assertEquals(setOf(KellvynStudioCapability.WEB_RESEARCH), spec.capabilities)
        assertTrue(studio.manifest.capabilities.containsAll(spec.capabilities))
        assertTrue(studio.manifest.requiresNetwork)
        assertEquals(setOf(KellvynStudioPermission.NETWORK_ACCESS), studio.manifest.permissions)
    }

    @Test
    fun preservesQuestionsWhenDetailedAnswersAreThrottled() = runBlocking {
        val studio = KellvynDeveloperKnowledgeStudio.testModule(
            accessEnabled = { true },
            provider = KellvynDeveloperKnowledgeProvider { _, _ ->
                KellvynDeveloperSearchBatch(
                    questions = listOf(
                        KellvynDeveloperQuestion(
                            id = 1L,
                            title = "Question survives answer throttling",
                            author = "Community",
                            score = 5,
                            viewCount = 400L,
                            answerCount = 1,
                            acceptedAnswerId = null,
                            excerpt = "Question text.",
                            link = "https://stackoverflow.com/questions/1/example",
                            contentLicense = "CC BY-SA 4.0",
                            createdAtEpochSeconds = null
                        )
                    ),
                    warnings = listOf("Respostas detalhadas temporariamente limitadas.")
                )
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "question throttle"))

        assertTrue(result.success)
        assertTrue(result.content.contains("Question survives answer throttling"))
        assertTrue(result.content.contains("Respostas detalhadas temporariamente limitadas."))
    }

    @Test
    fun duplicateQueryPolicyEnforcesSixtySecondIntervalAndHandlesClockChanges() {
        val now = 1_000_000L

        assertEquals(60L, KellvynDeveloperQueryThrottle.duplicateQueryDelaySeconds(now, now))
        assertEquals(30L, KellvynDeveloperQueryThrottle.duplicateQueryDelaySeconds(now, now + 30_000L))
        assertEquals(null, KellvynDeveloperQueryThrottle.duplicateQueryDelaySeconds(now, now + 60_000L))
        assertEquals(60L, KellvynDeveloperQueryThrottle.duplicateQueryDelaySeconds(now + 1_000L, now))
    }

    @Test
    fun backoffPolicyBlocksOnlyUntilServerProvidedDeadline() {
        val now = 2_000_000L
        val deadline = now + 12_500L

        assertEquals(13L, KellvynDeveloperQueryThrottle.backoffRemainingSeconds(deadline, now))
        assertEquals(5L, KellvynDeveloperQueryThrottle.backoffRemainingSeconds(deadline, now + 7_500L))
        assertEquals(null, KellvynDeveloperQueryThrottle.backoffRemainingSeconds(deadline, deadline))
    }

    @Test
    fun reportsProviderErrorsWithoutInventingAnswers() = runBlocking {
        val studio = KellvynDeveloperKnowledgeStudio.testModule(
            accessEnabled = { true },
            provider = KellvynDeveloperKnowledgeProvider { _, _ ->
                throw IllegalStateException("Stack Exchange API unavailable")
            }
        )

        val result = studio.tools.single().execute(mapOf("query" to "Kotlin API"))

        assertFalse(result.success)
        assertTrue(result.content.contains("Stack Exchange API unavailable"))
    }
}
