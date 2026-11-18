package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynSpecialistEvaluationHarnessTest {
    private val benchmarks = KellvynSpecialistBenchmarkRegistry.default()
    private val profiles = KellvynSpecialistProfileRegistry.default()

    @Test
    fun passingRequiresIndependentEvidenceAndCorrectProfileHash() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.RESEARCHER).first()
        val profile = profiles.requireProfile(testCase.role)
        val observedAllowedTools = mutableListOf<String>()
        val harness = KellvynSpecialistEvaluationHarness(
            executor = KellvynSpecialistBenchmarkExecutor { selectedCase, compiled, allowed ->
                observedAllowedTools += allowed.map { it.name }
                KellvynSpecialistBenchmarkExecution(
                    caseId = selectedCase.caseId,
                    profileSha256 = compiled.profileSha256,
                    responseText = "Resposta com fontes, limites e incertezas.",
                    requestedToolNames = setOf("wikipedia_search"),
                    executedToolNames = setOf("wikipedia_search"),
                    policyViolations = emptyList(),
                    startedAtMillis = 100L,
                    completedAtMillis = 150L
                )
            },
            verifier = KellvynSpecialistBenchmarkVerifier { selectedCase, _, _ ->
                KellvynSpecialistBenchmarkVerification(
                    verifiedEvidence = selectedCase.requiredEvidence.toSet(),
                    evidenceReferences = setOf("fixture:${selectedCase.caseId}"),
                    notes = listOf("Rubrica conferida por verificador independente.")
                )
            }
        )

        val result = harness.runCase(
            testCase = testCase,
            profile = profile,
            availableTools = listOf(wikipediaTool(), writeTool()),
            allowNetworkRead = true
        )

        assertEquals(KellvynSpecialistBenchmarkStatus.PASSED, result.status)
        assertEquals(100, result.criteriaCoveragePercent)
        assertEquals(50L, result.durationMillis)
        assertTrue(result.evidenceReferences.isNotEmpty())
        assertTrue(result.notes.isNotEmpty())
        assertTrue("wikipedia_search" in observedAllowedTools)
        assertFalse("workspace_write_file" in observedAllowedTools)
    }

    @Test
    fun unverifiableCriteriaAreMarkedReviewRequiredInsteadOfPassedOrFailed() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.RESEARCHER).first()
        val pendingCriterion = testCase.requiredEvidence.first()
        val verifier = KellvynSpecialistBenchmarkVerifier { selectedCase, _, _ ->
            KellvynSpecialistBenchmarkVerification(
                verifiedEvidence = selectedCase.requiredEvidence.toSet() - pendingCriterion,
                evidenceReferences = setOf("review:" + selectedCase.caseId),
                reviewRequiredEvidence = setOf(pendingCriterion),
                notes = listOf("Critério sem oráculo automatizado; exige revisão independente.")
            )
        }

        val result = harness(verifier = verifier).runCase(
            testCase,
            profiles.requireProfile(testCase.role),
            emptyList()
        )

        assertEquals(KellvynSpecialistBenchmarkStatus.REVIEW_REQUIRED, result.status)
        assertTrue(result.criteriaCoveragePercent < 100)
        assertEquals(testCase.requiredEvidence.size - 1, result.verifiedEvidenceCount)
        assertTrue(result.evidenceReferences.isNotEmpty())
    }

    @Test
    fun unknownOrConflictingReviewCriteriaFailClosed() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.RESEARCHER).first()
        val firstCriterion = testCase.requiredEvidence.first()
        val unknownReviewVerifier = KellvynSpecialistBenchmarkVerifier { selectedCase, _, _ ->
            KellvynSpecialistBenchmarkVerification(
                verifiedEvidence = selectedCase.requiredEvidence.toSet(),
                evidenceReferences = setOf("fixture:unknown-review"),
                reviewRequiredEvidence = setOf("criterion-not-in-this-case")
            )
        }
        val unknownReviewResult = harness(verifier = unknownReviewVerifier).runCase(
            testCase,
            profiles.requireProfile(testCase.role),
            emptyList()
        )
        assertEquals(KellvynSpecialistBenchmarkStatus.FAILED, unknownReviewResult.status)
        assertTrue(unknownReviewResult.reasons.any { it.contains("critérios inexistentes") })

        val conflictingVerifier = KellvynSpecialistBenchmarkVerifier { selectedCase, _, _ ->
            KellvynSpecialistBenchmarkVerification(
                verifiedEvidence = selectedCase.requiredEvidence.toSet(),
                evidenceReferences = setOf("fixture:conflicting-review"),
                reviewRequiredEvidence = setOf(firstCriterion)
            )
        }
        val conflictingResult = harness(verifier = conflictingVerifier).runCase(
            testCase,
            profiles.requireProfile(testCase.role),
            emptyList()
        )
        assertEquals(KellvynSpecialistBenchmarkStatus.FAILED, conflictingResult.status)
        assertTrue(conflictingResult.reasons.any { it.contains("verificado e pendente") })

        val unassignedVerifier = KellvynSpecialistBenchmarkVerifier { _, _, _ ->
            KellvynSpecialistBenchmarkVerification(
                verifiedEvidence = emptySet(),
                evidenceReferences = setOf("fixture:unassigned-criterion")
            )
        }
        val unassignedResult = harness(verifier = unassignedVerifier).runCase(
            testCase,
            profiles.requireProfile(testCase.role),
            emptyList()
        )
        assertEquals(KellvynSpecialistBenchmarkStatus.FAILED, unassignedResult.status)
        assertTrue(unassignedResult.reasons.any {
            it.contains("sem prova nem encaminhamento para revisão")
        })
    }

    @Test
    fun suiteEndsAsReviewRequiredWhenAnyCaseNeedsHumanReview() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.RESEARCHER).first()
        val pendingCriterion = testCase.requiredEvidence.first()
        val recorder = RecordingTraceRecorder()
        val trace = KellvynAgentLabTraceSession.create(recorder)
        val harness = KellvynSpecialistEvaluationHarness(
            executor = KellvynSpecialistBenchmarkExecutor { selectedCase, compiled, _ ->
                KellvynSpecialistBenchmarkExecution(
                    caseId = selectedCase.caseId,
                    profileSha256 = compiled.profileSha256,
                    responseText = "Resposta executada.",
                    requestedToolNames = emptySet(),
                    executedToolNames = emptySet(),
                    policyViolations = emptyList(),
                    startedAtMillis = 100L,
                    completedAtMillis = 150L
                )
            },
            verifier = KellvynSpecialistBenchmarkVerifier { selectedCase, _, _ ->
                KellvynSpecialistBenchmarkVerification(
                    verifiedEvidence = selectedCase.requiredEvidence.toSet() - pendingCriterion,
                    evidenceReferences = setOf("review:" + selectedCase.caseId),
                    reviewRequiredEvidence = setOf(pendingCriterion)
                )
            },
            traceSessionFactory = { trace }
        )

        val report = harness.runSuite(
            cases = listOf(testCase),
            profiles = profiles.profiles(),
            availableTools = emptyList()
        )

        assertEquals(1, report.reviewRequired)
        assertEquals(0, report.passed)
        assertEquals(0, report.failed)
        assertEquals(0, report.blocked)
        assertTrue(recorder.events.any {
            it.kind == KellvynAgentLabEventKind.BENCHMARK_CASE_FINISHED &&
                it.outcome == KellvynAgentLabOutcome.REVIEW_REQUIRED
        })
        assertTrue(recorder.events.any {
            it.kind == KellvynAgentLabEventKind.EXPERIMENT_FINISHED &&
                it.outcome == KellvynAgentLabOutcome.REVIEW_REQUIRED
        })
    }

    @Test
    fun unauthorizedRequestedAndExecutedToolsFailTheCase() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.RESEARCHER).first()
        val profile = profiles.requireProfile(testCase.role)
        val harness = harness(
            requested = setOf("workspace_write_file"),
            executed = setOf("workspace_write_file")
        )

        val result = harness.runCase(
            testCase,
            profile,
            listOf(writeTool()),
            allowNetworkRead = true
        )

        assertEquals(KellvynSpecialistBenchmarkStatus.FAILED, result.status)
        assertTrue(result.reasons.any { it.contains("fora da allowlist") })
    }

    @Test
    fun profileHashMismatchCannotPassEvenWhenChecklistIsComplete() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.RESEARCHER).first()
        val result = harness(profileHashOverride = "0".repeat(64)).runCase(
            testCase,
            profiles.requireProfile(testCase.role),
            emptyList()
        )

        assertEquals(KellvynSpecialistBenchmarkStatus.FAILED, result.status)
        assertTrue(result.reasons.any { it.contains("hash do perfil") })
        assertEquals(100, result.criteriaCoveragePercent)
    }

    @Test
    fun missingIndependentEvidenceReferenceBlocksApproval() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.RESEARCHER).first()
        val result = harness(evidenceReferences = emptySet()).runCase(
            testCase,
            profiles.requireProfile(testCase.role),
            emptyList()
        )

        assertEquals(KellvynSpecialistBenchmarkStatus.FAILED, result.status)
        assertTrue(result.reasons.any { it.contains("não anexou referência") })
    }

    @Test
    fun webResearchIsOnlyPassedToResearcherWhenOwnerNetworkSwitchIsEnabled() = runBlocking {
        val testCase = benchmarks.forRole(KellvynSpecialistRole.CODE_EXPLORER).first()
        val observed = mutableListOf<String>()
        val harness = KellvynSpecialistEvaluationHarness(
            executor = KellvynSpecialistBenchmarkExecutor { selectedCase, compiled, allowed ->
                observed += allowed.map { it.name }
                KellvynSpecialistBenchmarkExecution(
                    caseId = selectedCase.caseId,
                    profileSha256 = compiled.profileSha256,
                    responseText = "Análise estrutural.",
                    requestedToolNames = emptySet(),
                    executedToolNames = emptySet(),
                    policyViolations = emptyList(),
                    startedAtMillis = 1L,
                    completedAtMillis = 2L
                )
            },
            verifier = allEvidenceVerifier()
        )

        harness.runCase(
            testCase,
            profiles.requireProfile(testCase.role),
            listOf(wikipediaTool()),
            allowNetworkRead = true
        )

        assertFalse("wikipedia_search" in observed)
    }

    @Test
    fun suiteLargerThanCatalogBoundIsBlockedWithoutRunningCases() = runBlocking {
        val recorder = RecordingTraceRecorder()
        val trace = KellvynAgentLabTraceSession.create(recorder)
        val cases = benchmarks.cases() + benchmarks.cases().first().copy(
            caseId = "oversized-benchmark-case"
        )
        var executions = 0
        val harness = KellvynSpecialistEvaluationHarness(
            executor = KellvynSpecialistBenchmarkExecutor { testCase, compiled, _ ->
                executions++
                KellvynSpecialistBenchmarkExecution(
                    caseId = testCase.caseId,
                    profileSha256 = compiled.profileSha256,
                    responseText = "não deveria executar",
                    requestedToolNames = emptySet(),
                    executedToolNames = emptySet(),
                    policyViolations = emptyList(),
                    startedAtMillis = 10L,
                    completedAtMillis = 11L
                )
            },
            verifier = allEvidenceVerifier(),
            traceSessionFactory = { trace }
        )

        val report = harness.runSuite(cases, profiles.profiles(), emptyList())

        assertEquals(29, report.total)
        assertEquals(29, report.blocked)
        assertEquals(0, executions)
        assertEquals(1, recorder.events.count {
            it.kind == KellvynAgentLabEventKind.EXPERIMENT_FINISHED &&
                it.outcome == KellvynAgentLabOutcome.BLOCKED
        })
        assertEquals(2, recorder.events.size)
    }

    @Test
    fun suiteRunsSequentiallyAndRecordsEachCaseInOneExperimentTrace() = runBlocking {
        val order = mutableListOf<String>()
        val recorder = RecordingTraceRecorder()
        val trace = KellvynAgentLabTraceSession.create(recorder)
        val harness = KellvynSpecialistEvaluationHarness(
            executor = KellvynSpecialistBenchmarkExecutor { selectedCase, compiled, _ ->
                order += selectedCase.caseId
                KellvynSpecialistBenchmarkExecution(
                    caseId = selectedCase.caseId,
                    profileSha256 = compiled.profileSha256,
                    responseText = "Resposta de teste.",
                    requestedToolNames = emptySet(),
                    executedToolNames = emptySet(),
                    policyViolations = emptyList(),
                    startedAtMillis = 10L,
                    completedAtMillis = 11L
                )
            },
            verifier = allEvidenceVerifier(),
            traceSessionFactory = { trace }
        )

        val report = harness.runSuite(
            cases = benchmarks.cases(),
            profiles = profiles.profiles(),
            availableTools = emptyList()
        )

        assertEquals(28, report.total)
        assertEquals(28, report.passed)
        assertEquals(0, report.reviewRequired)
        assertEquals(0, report.failed)
        assertEquals(0, report.blocked)
        assertEquals(order.sorted(), order)
        assertEquals(1, recorder.events.count {
            it.kind == KellvynAgentLabEventKind.EXPERIMENT_STARTED
        })
        val suiteFingerprint = recorder.events.single {
            it.kind == KellvynAgentLabEventKind.EXPERIMENT_STARTED
        }.detailCode.orEmpty()
        assertTrue(suiteFingerprint.startsWith("SUITE_"))
        assertEquals(70, suiteFingerprint.length)
        assertTrue(suiteFingerprint.removePrefix("SUITE_").matches(Regex("[a-f0-9]{64}")))
        assertEquals(28, recorder.events.count {
            it.kind == KellvynAgentLabEventKind.BENCHMARK_CASE_STARTED
        })
        assertEquals(28, recorder.events.count {
            it.kind == KellvynAgentLabEventKind.BENCHMARK_CASE_FINISHED
        })
        assertEquals(1, recorder.events.count {
            it.kind == KellvynAgentLabEventKind.EXPERIMENT_FINISHED &&
                it.outcome == KellvynAgentLabOutcome.SUCCEEDED
        })
        assertTrue(recorder.events.filter {
            it.kind == KellvynAgentLabEventKind.BENCHMARK_CASE_FINISHED
        }.all { it.profileSha256?.matches(Regex("[a-f0-9]{64}")) == true })
    }

    private class RecordingTraceRecorder : KellvynAgentLabTraceRecorder {
        val events = mutableListOf<KellvynAgentLabEvent>()

        override suspend fun record(event: KellvynAgentLabEvent) {
            events += event
        }
    }

    private fun harness(
        requested: Set<String> = emptySet(),
        executed: Set<String> = emptySet(),
        profileHashOverride: String? = null,
        evidenceReferences: Set<String> = setOf("fixture:independent-evidence"),
        verifier: KellvynSpecialistBenchmarkVerifier? = null
    ) = KellvynSpecialistEvaluationHarness(
        executor = KellvynSpecialistBenchmarkExecutor { selectedCase, compiled, _ ->
            KellvynSpecialistBenchmarkExecution(
                caseId = selectedCase.caseId,
                profileSha256 = profileHashOverride ?: compiled.profileSha256,
                responseText = "Resposta com evidência.",
                requestedToolNames = requested,
                executedToolNames = executed,
                policyViolations = emptyList(),
                startedAtMillis = 10L,
                completedAtMillis = 20L
            )
        },
        verifier = verifier ?: KellvynSpecialistBenchmarkVerifier { selectedCase, _, _ ->
            KellvynSpecialistBenchmarkVerification(
                verifiedEvidence = selectedCase.requiredEvidence.toSet(),
                evidenceReferences = evidenceReferences
            )
        }
    )

    private fun allEvidenceVerifier() = KellvynSpecialistBenchmarkVerifier { selectedCase, _, _ ->
        KellvynSpecialistBenchmarkVerification(
            verifiedEvidence = selectedCase.requiredEvidence.toSet(),
            evidenceReferences = setOf("fixture:${selectedCase.caseId}")
        )
    }

    private fun wikipediaTool() = KellvynToolSpec(
        name = "wikipedia_search",
        description = "Pesquisa externa somente leitura.",
        risk = KellvynToolRisk.NETWORK_READ,
        capabilities = setOf(KellvynStudioCapability.WEB_RESEARCH)
    )

    private fun writeTool() = KellvynToolSpec(
        name = "workspace_write_file",
        description = "Escrita no workspace.",
        risk = KellvynToolRisk.REVERSIBLE_WRITE,
        capabilities = setOf(KellvynStudioCapability.CODE_EDITING)
    )
}
