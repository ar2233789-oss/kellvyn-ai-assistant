package com.kellvyn.assistant

import kotlinx.coroutines.CancellationException

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
        require(caseId.isNotBlank() && caseId.length <= 64)
        require(profileSha256.isNotBlank() && profileSha256.length <= 64)
        require(responseText.length <= 24_000)
        require(requestedToolNames.size <= 16 && executedToolNames.size <= 16)
        require(policyViolations.size <= 32)
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
        require(evidenceReferences.size <= 16)
        require(evidenceReferences.all { it.isNotBlank() && it.length <= 240 })
        require(notes.size <= 16)
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
    val scorePercent: Int,
    val verifiedEvidenceCount: Int,
    val requiredEvidenceCount: Int,
    val evidenceReferences: Set<String>,
    val durationMillis: Long?,
    val reasons: List<String>
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
    private val verifier: KellvynSpecialistBenchmarkVerifier
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
        if (verification.notes.isNotEmpty()) {
            reasons += verification.notes.map { "Nota do verificador: " + it }
        }

        val score = if (testCase.requiredEvidence.isEmpty()) 0 else
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
            scorePercent = score,
            verifiedEvidenceCount = satisfied.size,
            requiredEvidenceCount = testCase.requiredEvidence.size,
            evidenceReferences = verification.evidenceReferences,
            durationMillis = duration.takeIf { it >= 0L },
            reasons = reasons
        )
    }

    suspend fun runSuite(
        cases: List<KellvynSpecialistBenchmarkCase>,
        profiles: List<KellvynSpecialistProfile>,
        availableTools: List<KellvynToolSpec>,
        allowNetworkRead: Boolean = false
    ): KellvynSpecialistBenchmarkReport {
        val profilesByRole = profiles.associateBy { it.role }
        val results = mutableListOf<KellvynSpecialistBenchmarkResult>()
        for (testCase in cases.sortedBy { it.caseId }) {
            val profile = profilesByRole[testCase.role]
            results += if (profile == null) {
                blocked(testCase, "Não há perfil compilável para este papel.")
            } else {
                runCase(testCase, profile, availableTools, allowNetworkRead)
            }
        }
        return KellvynSpecialistBenchmarkReport(results)
    }

    private fun blocked(
        testCase: KellvynSpecialistBenchmarkCase,
        reason: String
    ) = KellvynSpecialistBenchmarkResult(
        caseId = testCase.caseId,
        role = testCase.role,
        status = KellvynSpecialistBenchmarkStatus.BLOCKED,
        scorePercent = 0,
        verifiedEvidenceCount = 0,
        requiredEvidenceCount = testCase.requiredEvidence.size,
        evidenceReferences = emptySet(),
        durationMillis = null,
        reasons = listOf(reason)
    )

    private fun safeMessage(error: Exception): String =
        (error.message ?: error.javaClass.simpleName).replace(Regex("[\\r\\n\\t]+"), " ").take(240)
}
