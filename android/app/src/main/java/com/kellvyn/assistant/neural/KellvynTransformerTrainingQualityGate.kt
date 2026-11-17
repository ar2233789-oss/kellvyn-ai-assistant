package com.kellvyn.assistant.neural

import kotlin.math.ln
import kotlin.math.max

/**
 * Conservative training-quality gate for KN-003.
 *
 * Passing this gate means only that a checkpoint improved on validation data and did not
 * show an extreme held-out loss gap. It is NOT proof of natural conversation, reasoning,
 * coding ability, protocol compliance, or production readiness. Those need separate
 * behavioral benchmarks and device validation.
 */
internal object KellvynTransformerTrainingQualityGate {
    data class Report(
        val eligibleForProtocolBenchmark: Boolean,
        val relativeValidationImprovement: Double,
        val generalizationGap: Double,
        val uniformBaselineLoss: Double,
        val reasons: List<String>
    )

    fun evaluate(
        manifest: KellvynTransformerTrainingManifest,
        minimumRelativeImprovement: Double = 0.05,
        maximumGeneralizationGap: Double = 0.50
    ): Report {
        require(minimumRelativeImprovement in 0.0..0.5)
        require(maximumGeneralizationGap in 0.0..5.0)

        val reasons = mutableListOf<String>()
        val baseline = ln(manifest.vocabularySize.toDouble())
        val initial = manifest.initialValidationLoss
        val validation = manifest.finalValidationLoss
        val heldOut = manifest.heldOutTestLoss

        if (!initial.isFinite() || initial <= 0.0) {
            reasons += "A loss de validação inicial não é positiva e finita."
        }
        if (!validation.isFinite() || validation <= 0.0) {
            reasons += "A loss de validação final não é positiva e finita."
        }
        if (!heldOut.isFinite() || heldOut <= 0.0) {
            reasons += "A loss do conjunto de teste não é positiva e finita."
        }
        if (manifest.contextLength < MIN_PROTOCOL_CONTEXT) {
            reasons += "A janela do modelo é pequena demais para testar decisões KELLVYN/1."
        }
        if (manifest.totalTrainingSteps <= 0L) {
            reasons += "O checkpoint não registra passos de treinamento."
        }
        if (manifest.entries.none { it.split == KellvynTrainingSplit.TRAIN } ||
            manifest.entries.none { it.split == KellvynTrainingSplit.VALIDATION } ||
            manifest.entries.none { it.split == KellvynTrainingSplit.TEST }
        ) {
            reasons += "O manifesto não contém TRAIN, VALIDATION e TEST."
        }
        if (manifest.entries.map { it.id }.distinct().size != manifest.entries.size) {
            reasons += "O manifesto contém IDs de amostra duplicados."
        }
        if (manifest.entries.any {
                it.licenseId.isBlank() || it.sourceReference.isBlank() ||
                    it.rightsEvidence.isBlank() ||
                    !it.contentSha256.matches(Regex("[a-f0-9]{64}"))
            }
        ) {
            reasons += "A proveniência de pelo menos uma amostra está incompleta."
        }

        val improvement = if (initial.isFinite() && initial > 0.0 &&
            validation.isFinite() && validation > 0.0
        ) {
            (initial - validation) / initial
        } else {
            Double.NEGATIVE_INFINITY
        }
        val gap = if (validation.isFinite() && heldOut.isFinite()) heldOut - validation
            else Double.POSITIVE_INFINITY

        if (improvement < minimumRelativeImprovement) {
            reasons += "A validação não melhorou pelo mínimo relativo exigido."
        }
        if (validation >= baseline * MAX_BASELINE_FRACTION) {
            reasons += "A loss de validação continua próxima demais da previsão aleatória uniforme."
        }
        if (gap > maximumGeneralizationGap) {
            reasons += "A diferença entre validação e teste sugere generalização insuficiente."
        }
        if (heldOut > baseline * MAX_BASELINE_FRACTION) {
            reasons += "A loss no conjunto de teste ainda está próxima demais da previsão aleatória."
        }

        return Report(
            eligibleForProtocolBenchmark = reasons.isEmpty(),
            relativeValidationImprovement = improvement,
            generalizationGap = gap,
            uniformBaselineLoss = baseline,
            reasons = reasons
        )
    }

    private const val MIN_PROTOCOL_CONTEXT = 128
    private const val MAX_BASELINE_FRACTION = 0.90
}
