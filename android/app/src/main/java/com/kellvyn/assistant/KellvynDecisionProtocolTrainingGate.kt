package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynInstructionTrainingExample
import com.kellvyn.assistant.neural.KellvynTrainingSplit

/**
 * Admission gate for decision-capable KN-004 training.
 * Free-form chat completions are not accepted: every target must parse as a real KELLVYN/1
 * decision and every referenced tool/plan must agree with the registered local contracts.
 */
internal object KellvynDecisionProtocolTrainingGate {
    data class Counts(
        val final: Int,
        val callTool: Int,
        val plan: Int
    )

    data class Report(
        val readyForDecisionTraining: Boolean,
        val trainExamples: Int,
        val validationExamples: Int,
        val testExamples: Int,
        val trainCounts: Counts,
        val validationCounts: Counts,
        val testCounts: Counts,
        val rejectedExamples: Int,
        val reasons: List<String>
    )

    fun evaluate(
        examples: List<KellvynInstructionTrainingExample>,
        tools: KellvynToolRegistry,
        minimumTrainExamples: Int = 100,
        minimumValidationExamples: Int = 20,
        minimumTestExamples: Int = 20,
        minimumTrainPerDecision: Int = 5,
        minimumValidationPerDecision: Int = 1,
        minimumTestPerDecision: Int = 1
    ): Report {
        require(minimumTrainExamples > 0 && minimumValidationExamples > 0 && minimumTestExamples > 0)
        require(minimumTrainPerDecision > 0 && minimumValidationPerDecision > 0 && minimumTestPerDecision > 0)
        val reasons = mutableListOf<String>()
        val splitCounts = mutableMapOf(
            KellvynTrainingSplit.TRAIN to Counts(0, 0, 0),
            KellvynTrainingSplit.VALIDATION to Counts(0, 0, 0),
            KellvynTrainingSplit.TEST to Counts(0, 0, 0)
        )
        val splitTotals = mutableMapOf(
            KellvynTrainingSplit.TRAIN to 0,
            KellvynTrainingSplit.VALIDATION to 0,
            KellvynTrainingSplit.TEST to 0
        )
        var rejected = 0

        examples.forEach { example ->
            splitTotals[example.split] = (splitTotals[example.split] ?: 0) + 1
            val parsed = when (
                val result = KellvynDecisionProtocol.parse(
                    example.completion,
                    nowMillis = PROTOCOL_VALIDATION_TIME_MILLIS
                )
            ) {
                is KellvynDecisionProtocol.ParseResult.Accepted -> result.decision
                is KellvynDecisionProtocol.ParseResult.Rejected -> {
                    rejected++
                    reasons += "${example.id}: completion não é uma decisão KELLVYN/1 válida."
                    return@forEach
                }
            }

            val current = splitCounts.getValue(example.split)
            when (parsed) {
                is KellvynModelDecision.Final -> {
                    splitCounts[example.split] = current.copy(final = current.final + 1)
                }
                is KellvynModelDecision.CallTool -> {
                    val tool = tools.find(parsed.name)
                    val invalid = when {
                        tool == null -> "ferramenta não registrada"
                        else -> tools.validate(tool, parsed.arguments)
                    }
                    if (invalid != null) {
                        rejected++
                        reasons += "${example.id}: chamada recusada pelo contrato local ($invalid)."
                        return@forEach
                    }
                    splitCounts[example.split] = current.copy(callTool = current.callTool + 1)
                }
                is KellvynModelDecision.Plan -> {
                    val risks = tools.descriptions().associate { spec ->
                        spec.name to KellvynStepRisk.valueOf(spec.risk.name)
                    }
                    val validation = KellvynTaskPlanner().validate(parsed.plan, risks)
                    if (validation.isNotEmpty()) {
                        rejected++
                        reasons += "${example.id}: plano recusado (${validation.joinToString(" ").take(300)})."
                        return@forEach
                    }
                    splitCounts[example.split] = current.copy(plan = current.plan + 1)
                }
                is KellvynModelDecision.Delegate -> {
                    rejected++
                    reasons += "${example.id}: decisão que transfere raciocínio não é permitida."
                }
            }
        }

        fun requireCount(split: KellvynTrainingSplit, minimum: Int, label: String) {
            val actual = splitTotals.getValue(split)
            if (actual < minimum) reasons += "$label tem $actual exemplos; mínimo exigido: $minimum."
        }
        requireCount(KellvynTrainingSplit.TRAIN, minimumTrainExamples, "TRAIN")
        requireCount(KellvynTrainingSplit.VALIDATION, minimumValidationExamples, "VALIDATION")
        requireCount(KellvynTrainingSplit.TEST, minimumTestExamples, "TEST")

        fun requireDecisionCoverage(
            split: KellvynTrainingSplit,
            counts: Counts,
            minimum: Int,
            label: String
        ) {
            if (counts.final < minimum) reasons += "$label: FINAL tem ${counts.final}; mínimo: $minimum."
            if (counts.callTool < minimum) reasons += "$label: CALL_TOOL tem ${counts.callTool}; mínimo: $minimum."
            if (counts.plan < minimum) reasons += "$label: PLAN tem ${counts.plan}; mínimo: $minimum."
        }
        requireDecisionCoverage(KellvynTrainingSplit.TRAIN, splitCounts.getValue(KellvynTrainingSplit.TRAIN), minimumTrainPerDecision, "TRAIN")
        requireDecisionCoverage(KellvynTrainingSplit.VALIDATION, splitCounts.getValue(KellvynTrainingSplit.VALIDATION), minimumValidationPerDecision, "VALIDATION")
        requireDecisionCoverage(KellvynTrainingSplit.TEST, splitCounts.getValue(KellvynTrainingSplit.TEST), minimumTestPerDecision, "TEST")

        return Report(
            readyForDecisionTraining = reasons.isEmpty(),
            trainExamples = splitTotals.getValue(KellvynTrainingSplit.TRAIN),
            validationExamples = splitTotals.getValue(KellvynTrainingSplit.VALIDATION),
            testExamples = splitTotals.getValue(KellvynTrainingSplit.TEST),
            trainCounts = splitCounts.getValue(KellvynTrainingSplit.TRAIN),
            validationCounts = splitCounts.getValue(KellvynTrainingSplit.VALIDATION),
            testCounts = splitCounts.getValue(KellvynTrainingSplit.TEST),
            rejectedExamples = rejected,
            reasons = reasons.distinct()
        )
    }

    fun requireReady(
        examples: List<KellvynInstructionTrainingExample>,
        tools: KellvynToolRegistry,
        minimumTrainExamples: Int = 100,
        minimumValidationExamples: Int = 20,
        minimumTestExamples: Int = 20,
        minimumTrainPerDecision: Int = 5,
        minimumValidationPerDecision: Int = 1,
        minimumTestPerDecision: Int = 1
    ): Report {
        val report = evaluate(
            examples = examples,
            tools = tools,
            minimumTrainExamples = minimumTrainExamples,
            minimumValidationExamples = minimumValidationExamples,
            minimumTestExamples = minimumTestExamples,
            minimumTrainPerDecision = minimumTrainPerDecision,
            minimumValidationPerDecision = minimumValidationPerDecision,
            minimumTestPerDecision = minimumTestPerDecision
        )
        require(report.readyForDecisionTraining) {
            "Corpus KN-004 não está apto para treino de decisões: ${report.reasons.take(12).joinToString(" ")}"
        }
        return report
    }

    private const val PROTOCOL_VALIDATION_TIME_MILLIS = 1_800_000_000_000L
}
