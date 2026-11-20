package com.kellvyn.assistant.neural

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Installs only a KN-004 checkpoint that clears the numeric quality gate.
 *
 * The CPU-heavy training pipeline always runs on Dispatchers.IO. The previous installed
 * bundle is untouched when training is cancelled or the quality gate rejects the result.
 * This controller does not fetch data, use a cloud model, create agents, or change the UI.
 */
internal class KellvynNeuralTrainingController(
    private val modelStore: KellvynNeuralModelStore
) {
    data class Outcome(
        val installed: Boolean,
        val qualityGate: KellvynMultiBlockTrainingQualityGate.Report,
        val trainingSteps: Long,
        val parameterFingerprint: String?,
        val detail: String
    )

    suspend fun trainAndInstall(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): Outcome = withContext(Dispatchers.IO) {
        val trainingContext = currentCoroutineContext()
        val trained = KellvynMultiBlockTrainingPipeline.train(
            examples = examples,
            config = config,
            createdAtMillis = createdAtMillis,
            cancellationCheck = { trainingContext.ensureActive() }
        )
        trainingContext.ensureActive()

        val quality = KellvynMultiBlockTrainingQualityGate.evaluate(trained.manifest)
        if (!quality.eligibleForProtocolBenchmark) {
            return@withContext Outcome(
                installed = false,
                qualityGate = quality,
                trainingSteps = trained.model.trainingSteps,
                parameterFingerprint = trained.model.parameterFingerprint(),
                detail = "Treino concluído, mas o checkpoint não foi instalado: " +
                    quality.reasons.joinToString(" ")
            )
        }

        // Cancellation is checked immediately before the atomic store commit.
        trainingContext.ensureActive()
        modelStore.saveMultiBlock(trained)
        Outcome(
            installed = true,
            qualityGate = quality,
            trainingSteps = trained.model.trainingSteps,
            parameterFingerprint = trained.model.parameterFingerprint(),
            detail = "Checkpoint KN-004 instalado. Próxima etapa: benchmark comportamental."
        )
    }
}
