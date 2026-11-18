package com.kellvyn.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal enum class KellvynSpecialistBenchmarkStatus {
    PASSED,
    FAILED,
    BLOCKED
}

/**
 * Raw observations from the specialist execution path. This object intentionally does not
 * contain a self-reported score; only the independent verifier can mark evidence as satisfied.
 */
internal data class KellvynSpecialistBenchmarkExecution(
    val caseId: String,
    val profileSha256: String,
    val responseText: String,
    val requestedToolNames: Set<String>,
    val executedToolNames: Set<String>,
    val policyViolations: List<String>,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val failureReason: String? = null
) {
    init {
        require(caseId.matches(Regex("[a-z0-9][a-z0-9-]{2,63}")))
        require(profileSha256.matches(Regex("[0-9a-f]{64}")))
        require(responseText.length <= 24_000)
        require(requestedToolNames.size <= 16 && executedToolNames.size <= 16)
        require((requestedToolNames + executedToolNames).all {
            it.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,63}"))
        })
        require(policyViolations.size <= 32)
        require(policyViolations.all { it.isNotBlank() && it.length <= 300 })
        require(startedAtMillis >= 0L && completedAtMillis >= 0L)
        require(failureReason == null || failureReason.length <= 500)
    }
}

/** Evidence references and checklist decisions must come from a verifier separate from the agent. */
internal data class KellvynSpecialistBenchmarkVerification(
    val verifiedEvidence: Set<String>,
    val evidenceReferences: Set<String>,
    val notes: List<String> = emptyList()
) {
    init {
        require(verifiedEvidence.size <= 16)
        require(verifiedEvidence.all { it.isNotBlank() && it.length <= 180 })
        require(evidenceReferences.size <= 16)
        require(evidenceReferences.all { it.isNotBlank() && it.length <= 240 })
        require(notes.size <= 16)
        require(notes.all { it.isNotBlank() && it.length <= 300 })
    }
}

internal fun interface KellvynSpecialistBenchmarkExecutor {
    suspend fun execute(
        testCase: KellvynSpecialistBenchmarkCase,
        profile: KellvynCompiledSpecialistProfile,
        allowedTools: List<KellvynToolSpec>
    ): KellvynSpecialistBenchmarkExecution
}

internal fun interface KellvynSpecialistBenchmarkVerifier {
    suspend fun verify(
        testCase: KellvynSpecialistBenchmarkCase,
        profile: KellvynCompiledSpecialistProfile,
        execution: KellvynSpecialistBenchmarkExecution
    ): KellvynSpecialistBenchmarkVerification
}

internal data class KellvynSpecialistBenchmarkResult(
    val caseId: String,
    val role: KellvynSpecialistRole,
    val status: KellvynSpecialistBenchmarkStatus,
    val criteriaCoveragePercent: Int,
    val verifiedEvidenceCount: Int,
    val requiredEvidenceCount: Int,
    val evidenceReferences: Set<String>,
    val durationMillis: Long?,
    val reasons: List<String>,
    val notes: List<String> = emptyList()
)

internal data class KellvynSpecialistBenchmarkReport(
    val results: List<KellvynSpecialistBenchmarkResult>
) {
    val passed: Int get() = results.count { it.status == KellvynSpecialistBenchmarkStatus.PASSED }
    val failed: Int get() = results.count { it.status == KellvynSpecialistBenchmarkStatus.FAILED }
    val blocked: Int get() = results.count { it.status == KellvynSpecialistBenchmarkStatus.BLOCKED }
    val total: Int get() = results.size
}

/**
 * Sequential, fail-closed benchmark runner.
 *
 * The specialist receives only tools allowed by the central role/risk matrix. Execution output
 * is untrusted. A separate verifier must return each required criterion and at least one
 * evidence reference; the model cannot approve itself by writing "passed" in its response.
 */
internal class KellvynSpecialistEvaluationHarness(
    private val executor: KellvynSpecialistBenchmarkExecutor,
    private val verifier: KellvynSpecialistBenchmarkVerifier,
    private val traceSessionFactory: () -> KellvynAgentLabTraceSession = {
        KellvynAgentLabTraceSession.create()
    }
) {
    suspend fun runCase(
        testCase: KellvynSpecialistBenchmarkCase,
        profile: KellvynSpecialistProfile,
        availableTools: List<KellvynToolSpec>,
        allowNetworkRead: Boolean = false
    ): KellvynSpecialistBenchmarkResult {
        if (profile.role != testCase.role) {
            return blocked(testCase, "O perfil compilado não corresponde ao papel do caso.")
        }

        val compiledProfile = KellvynSpecialistProfileCompiler().compile(profile)
            ?: return blocked(testCase, "O perfil não passou pelo Profile Compiler.")

        if (!compiledProfile.authorizedCapabilities.containsAll(testCase.requiredCapabilities)) {
            return blocked(testCase, "O perfil não possui todas as capabilities exigidas pelo caso.")
        }

        val permittedNames = KellvynSpecialistToolPolicy.permittedToolNames(
            role = testCase.role,
            specs = availableTools,
            allowNetworkRead = allowNetworkRead
        )
        val allowedTools = availableTools.filter { it.name in permittedNames }

        val execution = try {
            executor.execute(testCase, compiledProfile, allowedTools)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return blocked(testCase, "Falha no executor: " + safeMessage(error))
        }

        val reasons = mutableListOf<String>()
        val duration = execution.completedAtMillis - execution.startedAtMillis
        if (execution.caseId != testCase.caseId) reasons += "A execução retornou outro caseId."
        if (execution.profileSha256 != compiledProfile.profileSha256) reasons += "O hash do perfil executado diverge do perfil compilado."
        if (execution.responseText.isBlank()) reasons += "O especialista não retornou resposta."
        if (execution.failureReason != null) reasons += "Execução incompleta: " + execution.failureReason
        if (duration < 0L) reasons += "A duração observada é inválida."
        if (execution.policyViolations.isNotEmpty()) {
            reasons += execution.policyViolations.map { "Violação de política: " + it }
        }

        val unauthorizedRequests = execution.requestedToolNames - permittedNames
        if (unauthorizedRequests.isNotEmpty()) {
            reasons += "Ferramentas solicitadas fora da allowlist: " + unauthorizedRequests.sorted().joinToString()
        }
        val unauthorizedExecutions = execution.executedToolNames - permittedNames
        if (unauthorizedExecutions.isNotEmpty()) {
            reasons += "Ferramentas executadas fora da allowlist: " + unauthorizedExecutions.sorted().joinToString()
        }
        val unrequestedExecutions = execution.executedToolNames - execution.requestedToolNames
        if (unrequestedExecutions.isNotEmpty()) {
            reasons += "Ferramentas executadas sem chamada registrada: " + unrequestedExecutions.sorted().joinToString()
        }

        val verification = try {
            verifier.verify(testCase, compiledProfile, execution)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return blocked(testCase, "A verificação independente falhou: " + safeMessage(error))
        }

        val satisfied = verification.verifiedEvidence.intersect(testCase.requiredEvidence.toSet())
        val missingEvidence = testCase.requiredEvidence.toSet() - satisfied
        if (missingEvidence.isNotEmpty()) {
            reasons += "Critérios não comprovados: " + missingEvidence.sorted().joinToString("; ")
        }
        if (verification.evidenceReferences.isEmpty()) {
            reasons += "A verificação independente não anexou referência de evidência."
        }
        val criteriaCoverage = if (testCase.requiredEvidence.isEmpty()) 0 else
            (satisfied.size * 100) / testCase.requiredEvidence.size
        val status = if (reasons.isEmpty()) {
            KellvynSpecialistBenchmarkStatus.PASSED
        } else {
            KellvynSpecialistBenchmarkStatus.FAILED
        }

        return KellvynSpecialistBenchmarkResult(
            caseId = testCase.caseId,
            role = testCase.role,
            status = status,
            criteriaCoveragePercent = criteriaCoverage,
            verifiedEvidenceCount = satisfied.size,
            requiredEvidenceCount = testCase.requiredEvidence.size,
            evidenceReferences = verification.evidenceReferences,
            durationMillis = duration.takeIf { it >= 0L },
            reasons = reasons,
            notes = verification.notes
        )
    }

    suspend fun runSuite(
        cases: List<KellvynSpecialistBenchmarkCase>,
        profiles: List<KellvynSpecialistProfile>,
        availableTools: List<KellvynToolSpec>,
        allowNetworkRead: Boolean = false
    ): KellvynSpecialistBenchmarkReport {
        val trace = traceSessionFactory()
        trace.record(
            kind = KellvynAgentLabEventKind.EXPERIMENT_STARTED,
            outcome = KellvynAgentLabOutcome.STARTED,
            detailCode = "SPECIALIST_BENCHMARK"
        )
        return try {
            val duplicateCaseIds = cases.groupingBy { it.caseId }
                .eachCount().filterValues { it > 1 }.keys
            val report = if (duplicateCaseIds.isNotEmpty()) {
                KellvynSpecialistBenchmarkReport(
                    cases.map { blocked(it, "O conjunto contém IDs de caso duplicados.") }
                )
            } else {
                val profileCompilation = KellvynSpecialistProfileCompiler().compileAll(profiles)
                if (!profileCompilation.success) {
                    val reason = "O conjunto de perfis não passou pelo Profile Compiler: " +
                        profileCompilation.errors.joinToString(" ").take(500)
                    KellvynSpecialistBenchmarkReport(cases.map { blocked(it, reason) })
                } else {
                    val profilesByRole = profiles.associateBy { it.role }
                    val compiledByRole = profileCompilation.profiles.associateBy { it.role }
                    val results = mutableListOf<KellvynSpecialistBenchmarkResult>()
                    cases.sortedBy { it.caseId }.forEachIndexed { index, testCase ->
                        val profile = profilesByRole[testCase.role]
                        val compiledProfile = compiledByRole[testCase.role]
                        val caseTrace = trace.forActor(
                            "benchmark:${testCase.role.name.lowercase()}"
                        )
                        val caseCode = safeCode(testCase.caseId)
                        caseTrace.record(
                            kind = KellvynAgentLabEventKind.BENCHMARK_CASE_STARTED,
                            step = index + 1,
                            specialistRole = testCase.role,
                            profileSha256 = compiledProfile?.profileSha256,
                            outcome = KellvynAgentLabOutcome.STARTED,
                            detailCode = caseCode
                        )
                        val result = if (profile == null) {
                            blocked(testCase, "Não há perfil compilável para este papel.")
                        } else {
                            runCase(testCase, profile, availableTools, allowNetworkRead)
                        }
                        caseTrace.record(
                            kind = KellvynAgentLabEventKind.BENCHMARK_CASE_FINISHED,
                            step = index + 1,
                            specialistRole = testCase.role,
                            profileSha256 = compiledProfile?.profileSha256,
                            durationMillis = result.durationMillis,
                            outcome = when (result.status) {
                                KellvynSpecialistBenchmarkStatus.PASSED ->
                                    KellvynAgentLabOutcome.SUCCEEDED
                                KellvynSpecialistBenchmarkStatus.FAILED ->
                                    KellvynAgentLabOutcome.FAILED
                                KellvynSpecialistBenchmarkStatus.BLOCKED ->
                                    KellvynAgentLabOutcome.BLOCKED
                            },
                            detailCode = caseCode
                        )
                        results += result
                    }
                    KellvynSpecialistBenchmarkReport(results)
                }
            }
            trace.record(
                kind = KellvynAgentLabEventKind.EXPERIMENT_FINISHED,
                outcome = when {
                    report.failed > 0 -> KellvynAgentLabOutcome.FAILED
                    report.blocked > 0 -> KellvynAgentLabOutcome.BLOCKED
                    else -> KellvynAgentLabOutcome.SUCCEEDED
                },
                detailCode = "CASES_${report.total}"
            )
            report
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                trace.record(
                    kind = KellvynAgentLabEventKind.EXPERIMENT_FINISHED,
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "CANCELLED"
                )
            }
            throw cancelled
        } catch (error: Exception) {
            withContext(NonCancellable) {
                trace.record(
                    kind = KellvynAgentLabEventKind.EXPERIMENT_FINISHED,
                    outcome = KellvynAgentLabOutcome.FAILED,
                    detailCode = "BENCHMARK_ERROR"
                )
            }
            throw error
        }
    }

    private fun safeCode(value: String): String =
        value.uppercase()
            .replace(Regex("[^A-Z0-9_.:-]"), "_")
            .take(80)
            .ifBlank { "CASE" }

    private fun blocked(
        testCase: KellvynSpecialistBenchmarkCase,
        reason: String
    ) = KellvynSpecialistBenchmarkResult(
        caseId = testCase.caseId,
        role = testCase.role,
        status = KellvynSpecialistBenchmarkStatus.BLOCKED,
        criteriaCoveragePercent = 0,
        verifiedEvidenceCount = 0,
        requiredEvidenceCount = testCase.requiredEvidence.size,
        evidenceReferences = emptySet(),
        durationMillis = null,
        reasons = listOf(reason),
        notes = emptyList()
    )

    private fun safeMessage(error: Exception): String =
        (error.message ?: error.javaClass.simpleName).replace(Regex("[\\r\\n\\t]+"), " ").take(240)
}
