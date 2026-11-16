package com.kellvyn.assistant

/**
 * Original, backend-independent task planner for Kellvyn.
 *
 * It does not generate natural-language plans itself. The neural interface may propose a
 * structured plan; this class validates dependencies, enforces approval gates and requires
 * independent evidence before a step can be marked successful.
 */
internal class KellvynTaskPlanner {

    fun validate(
        plan: KellvynTaskPlan,
        registeredToolRisks: Map<String, KellvynStepRisk> = emptyMap()
    ): List<String> {
        val errors = mutableListOf<String>()

        if (!plan.id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) {
            errors += "O identificador da tarefa é inválido."
        }
        if (plan.schemaVersion != 1) {
            errors += "Versão de esquema do plano não suportada."
        }
        if (plan.createdAtMillis <= 0L) {
            errors += "Data de criação do plano inválida."
        }
        if (plan.goal.isBlank() || plan.goal.length > MAX_GOAL_CHARS) {
            errors += "O objetivo precisa ter entre 1 e " + MAX_GOAL_CHARS + " caracteres."
        }
        if (plan.steps.isEmpty() || plan.steps.size > MAX_STEPS) {
            errors += "O plano precisa conter entre 1 e " + MAX_STEPS + " etapas."
        }
        if (plan.constraints.size > MAX_CONSTRAINTS ||
            plan.constraints.any { it.isBlank() || it.length > MAX_CONSTRAINT_CHARS }
        ) {
            errors += "Há restrições vazias ou acima do limite permitido."
        }

        val duplicateIds = plan.steps.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            errors += "IDs de etapa duplicados: " + duplicateIds.sorted().joinToString() + "."
        }

        val byId = plan.steps.associateBy { it.id }
        for (step in plan.steps) {
            if (!step.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) {
                errors += "ID de etapa inválido: " + step.id
            }
            if (step.title.isBlank() || step.title.length > MAX_TITLE_CHARS) {
                errors += "Título vazio ou longo demais na etapa " + step.id + "."
            }
            if (step.objective.isBlank() || step.objective.length > MAX_OBJECTIVE_CHARS) {
                errors += "Objetivo inválido na etapa " + step.id + "."
            }
            if (step.successCriteria.isEmpty() ||
                step.successCriteria.size > MAX_CRITERIA ||
                step.successCriteria.any { it.isBlank() || it.length > MAX_CRITERION_CHARS }
            ) {
                errors += "Critérios de sucesso ausentes ou inválidos na etapa " + step.id + "."
            }
            if (step.requiredTools.any { !it.matches(TOOL_NAME) }) {
                errors += "Nome de ferramenta inválido na etapa " + step.id + "."
            }
            val unregisteredTools = step.requiredTools - registeredToolRisks.keys
            if (unregisteredTools.isNotEmpty()) {
                errors += "Ferramentas não registradas na etapa " + step.id + ": " +
                    unregisteredTools.sorted().joinToString() + "."
            }
            val highestRegisteredRisk = step.requiredTools.mapNotNull { registeredToolRisks[it] }
                .maxOfOrNull { it.ordinal } ?: KellvynStepRisk.READ_ONLY.ordinal
            if (highestRegisteredRisk > step.risk.ordinal) {
                errors += "O risco declarado é inferior ao risco real das ferramentas na etapa " +
                    step.id + "."
            }
            if (step.preconditions.size > MAX_PRECONDITIONS ||
                step.preconditions.any { it.isBlank() || it.length > MAX_PRECONDITION_CHARS }
            ) {
                errors += "Pré-condições vazias ou inválidas na etapa " + step.id + "."
            }
            if (step.dependencies.contains(step.id)) {
                errors += "A etapa " + step.id + " depende de si mesma."
            }
            val missingDependencies = step.dependencies - byId.keys
            if (missingDependencies.isNotEmpty()) {
                errors += "Dependências inexistentes em " + step.id + ": " +
                    missingDependencies.sorted().joinToString() + "."
            }
            if (step.risk != KellvynStepRisk.READ_ONLY && !step.ownerApprovalRequired) {
                errors += "A etapa " + step.id + " exige aprovação explícita do proprietário."
            }
            if (step.risk in MUTATING_RISKS && step.recoveryPlan.isNullOrBlank()) {
                errors += "A etapa " + step.id + " precisa descrever recuperação ou rollback."
            }
        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in visited || id !in byId) return
            if (!visiting.add(id)) {
                errors += "O plano contém um ciclo de dependências envolvendo " + id + "."
                return
            }
            byId[id]?.dependencies.orEmpty().forEach(::visit)
            visiting.remove(id)
            visited.add(id)
        }
        byId.keys.forEach(::visit)

        return errors.distinct()
    }

    fun createState(
        plan: KellvynTaskPlan,
        registeredToolRisks: Map<String, KellvynStepRisk> = emptyMap()
    ): KellvynTaskState {
        val errors = validate(plan, registeredToolRisks)
        require(errors.isEmpty()) { errors.joinToString(" ") }
        val initial = KellvynTaskState(
            plan = plan,
            statuses = plan.steps.associate { it.id to KellvynStepStatus.WAITING },
            approvedStepIds = emptySet(),
            attempts = emptyMap(),
            proofs = emptyMap(),
            failureReasons = emptyMap()
        )
        return refresh(initial)
    }

    fun approveStep(state: KellvynTaskState, stepId: String): KellvynTaskState {
        requireStatus(state, stepId, KellvynStepStatus.AWAITING_APPROVAL)
        return refresh(state.copy(approvedStepIds = state.approvedStepIds + stepId))
    }

    fun startStep(state: KellvynTaskState, stepId: String): KellvynTaskState {
        requireStatus(state, stepId, KellvynStepStatus.READY)
        val attempts = (state.attempts[stepId] ?: 0) + 1
        return state.copy(
            statuses = state.statuses + (stepId to KellvynStepStatus.RUNNING),
            attempts = state.attempts + (stepId to attempts)
        )
    }

    fun completeStep(
        state: KellvynTaskState,
        stepId: String,
        outcome: KellvynTaskOutcome,
        verifier: KellvynIndependentVerifier
    ): KellvynTaskState {
        requireStatus(state, stepId, KellvynStepStatus.RUNNING)
        val step = state.plan.steps.first { it.id == stepId }
        val proof = verifier.verify(step, outcome)
            ?: throw IllegalArgumentException("A verificação independente não produziu evidência.")
        require(proof.verifiedBy.isNotBlank()) { "Identificador do verificador ausente." }
        require(proof.summary.isNotBlank()) { "Resumo da evidência ausente." }
        require(proof.observedAtMillis > 0L) { "Data da evidência inválida." }
        require(proof.evidenceSha256.matches(SHA256)) { "Hash da evidência inválido." }
        val missingCriteria = step.successCriteria.toSet() - proof.verifiedCriteria
        require(missingCriteria.isEmpty()) {
            "A evidência não comprovou todos os critérios: " + missingCriteria.joinToString()
        }

        val updated = state.copy(
            statuses = state.statuses + (stepId to KellvynStepStatus.SUCCEEDED),
            proofs = state.proofs + (stepId to proof),
            failureReasons = state.failureReasons - stepId
        )
        return refresh(updated)
    }

    fun failStep(
        state: KellvynTaskState,
        stepId: String,
        reason: String
    ): KellvynTaskState {
        requireStatus(state, stepId, KellvynStepStatus.RUNNING)
        require(reason.isNotBlank() && reason.length <= MAX_FAILURE_CHARS)
        val updated = state.copy(
            statuses = state.statuses + (stepId to KellvynStepStatus.FAILED),
            failureReasons = state.failureReasons + (stepId to reason)
        )
        return refresh(updated)
    }

    fun cancel(state: KellvynTaskState): KellvynTaskState {
        val cancellable = setOf(
            KellvynStepStatus.WAITING,
            KellvynStepStatus.READY,
            KellvynStepStatus.AWAITING_APPROVAL,
            KellvynStepStatus.RUNNING
        )
        val statuses = state.statuses.mapValues { (_, status) ->
            if (status in cancellable) KellvynStepStatus.CANCELLED else status
        }
        return state.copy(statuses = statuses)
    }

    fun isFinished(state: KellvynTaskState): Boolean =
        state.statuses.values.all { it in TERMINAL_STATUSES }

    private fun refresh(state: KellvynTaskState): KellvynTaskState {
        val statuses = state.statuses.toMutableMap()
        for (step in state.plan.steps) {
            val current = statuses[step.id] ?: KellvynStepStatus.WAITING
            if (current in IMMUTABLE_DURING_REFRESH) continue

            val dependencyStates = step.dependencies.mapNotNull { statuses[it] }
            statuses[step.id] = when {
                dependencyStates.any { it in setOf(
                    KellvynStepStatus.FAILED,
                    KellvynStepStatus.BLOCKED,
                    KellvynStepStatus.CANCELLED
                ) } -> KellvynStepStatus.BLOCKED

                dependencyStates.size == step.dependencies.size &&
                    dependencyStates.all { it == KellvynStepStatus.SUCCEEDED } -> {
                    if (step.ownerApprovalRequired && step.id !in state.approvedStepIds) {
                        KellvynStepStatus.AWAITING_APPROVAL
                    } else {
                        KellvynStepStatus.READY
                    }
                }

                else -> KellvynStepStatus.WAITING
            }
        }
        return state.copy(statuses = statuses)
    }

    private fun requireStatus(
        state: KellvynTaskState,
        stepId: String,
        expected: KellvynStepStatus
    ) {
        require(state.plan.steps.any { it.id == stepId }) { "Etapa desconhecida: " + stepId }
        check(state.statuses[stepId] == expected) {
            "Transição recusada para " + stepId + ": esperado " + expected +
                ", recebido " + state.statuses[stepId] + "."
        }
    }

    companion object {
        private const val MAX_STEPS = 64
        private const val MAX_GOAL_CHARS = 4_000
        private const val MAX_CONSTRAINTS = 40
        private const val MAX_CONSTRAINT_CHARS = 500
        private const val MAX_TITLE_CHARS = 120
        private const val MAX_OBJECTIVE_CHARS = 1_000
        private const val MAX_CRITERIA = 12
        private const val MAX_CRITERION_CHARS = 300
        private const val MAX_PRECONDITIONS = 12
        private const val MAX_PRECONDITION_CHARS = 300
        private const val MAX_FAILURE_CHARS = 500
        private val TOOL_NAME = Regex("[A-Za-z][A-Za-z0-9_]{1,63}")
        private val SHA256 = Regex("[a-fA-F0-9]{64}")
        private val MUTATING_RISKS = setOf(
            KellvynStepRisk.REVERSIBLE_WRITE,
            KellvynStepRisk.DEVICE_CONTROL,
            KellvynStepRisk.DESTRUCTIVE
        )
        private val TERMINAL_STATUSES = setOf(
            KellvynStepStatus.SUCCEEDED,
            KellvynStepStatus.FAILED,
            KellvynStepStatus.BLOCKED,
            KellvynStepStatus.CANCELLED
        )
        private val IMMUTABLE_DURING_REFRESH = TERMINAL_STATUSES + setOf(
            KellvynStepStatus.RUNNING
        )
    }
}

internal enum class KellvynStepRisk {
    READ_ONLY,
    NETWORK_READ,
    REVERSIBLE_WRITE,
    DEVICE_CONTROL,
    DESTRUCTIVE
}

internal enum class KellvynStepStatus {
    WAITING,
    READY,
    AWAITING_APPROVAL,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    CANCELLED
}

internal data class KellvynTaskStep(
    val id: String,
    val title: String,
    val objective: String,
    val dependencies: Set<String> = emptySet(),
    val requiredTools: Set<String> = emptySet(),
    val risk: KellvynStepRisk = KellvynStepRisk.READ_ONLY,
    val ownerApprovalRequired: Boolean = risk != KellvynStepRisk.READ_ONLY,
    val preconditions: List<String> = emptyList(),
    val successCriteria: List<String>,
    val recoveryPlan: String? = null
)

internal data class KellvynTaskPlan(
    val id: String,
    val goal: String,
    val constraints: List<String>,
    val steps: List<KellvynTaskStep>,
    val createdAtMillis: Long,
    val schemaVersion: Int = 1
)

internal data class KellvynTaskState(
    val plan: KellvynTaskPlan,
    val statuses: Map<String, KellvynStepStatus>,
    val approvedStepIds: Set<String>,
    val attempts: Map<String, Int>,
    val proofs: Map<String, KellvynStepProof>,
    val failureReasons: Map<String, String>
)

internal data class KellvynTaskOutcome(
    val summary: String,
    val exitCode: Int? = null,
    val artifactRefs: Set<String> = emptySet(),
    val observedAtMillis: Long
)

internal data class KellvynStepProof(
    val verifiedBy: String,
    val verifiedCriteria: Set<String>,
    val summary: String,
    val evidenceSha256: String,
    val observedAtMillis: Long
)

/**
 * Implementations must inspect real tool/build/test output.
 * A model-generated assertion is not, by itself, independent verification.
 */
internal fun interface KellvynIndependentVerifier {
    fun verify(step: KellvynTaskStep, outcome: KellvynTaskOutcome): KellvynStepProof?
}
