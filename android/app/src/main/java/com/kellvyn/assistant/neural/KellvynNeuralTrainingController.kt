package com.kellvyn.assistant.neural

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream

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
        val detail: String,
        /** Original TEST examples are returned in memory for the next benchmark stage; never persisted here. */
        val heldOutTestExamples: List<KellvynInstructionTrainingExample> = emptyList()
    )

    data class CorpusOutcome(
        val audit: KellvynJsonlCorpusReader.Report,
        val training: Outcome?
    )

    /**
     * Performs a read-only local corpus audit. This method never allocates a model,
     * trains weights, changes the installed checkpoint, stores the source or closes input.
     * The caller may review the report and reopen the same file for an explicit training call.
     */
    suspend fun auditJsonl(
        input: InputStream,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        samplingSeed: Long = config.seed,
        maxExamples: Int = 1_000,
        maxRetainedUtf8Bytes: Long = 480_000L,
        maxSourceUtf8Bytes: Long = 512L * 1024L * 1024L,
        maxLines: Long = 10_000_000L,
        minimumTrainExamples: Int = 100,
        minimumValidationExamples: Int = 20,
        minimumTestExamples: Int = KellvynNeuralBehaviorBenchmark.MINIMUM_EXAMPLES
    ): KellvynJsonlCorpusReader.Report = withContext(Dispatchers.IO) {
        val auditContext = currentCoroutineContext()
        KellvynJsonlCorpusReader.read(
            input = input,
            config = config,
            samplingSeed = samplingSeed,
            maxExamples = maxExamples,
            maxRetainedUtf8Bytes = maxRetainedUtf8Bytes,
            maxSourceUtf8Bytes = maxSourceUtf8Bytes,
            maxLines = maxLines,
            minimumTrainExamples = minimumTrainExamples,
            minimumValidationExamples = minimumValidationExamples,
            minimumTestExamples = minimumTestExamples,
            cancellationCheck = { auditContext.ensureActive() }
        ).report
    }

    /**
     * Audits a caller-owned local JSONL stream, then trains only its bounded safe sample.
     * The input stream is consumed but never closed here. No network access or corpus persistence
     * is performed; a rejected preflight returns without allocating the neural model.
     */
    suspend fun trainAndInstallFromJsonl(
        input: InputStream,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        samplingSeed: Long = config.seed,
        maxExamples: Int = 1_000,
        maxRetainedUtf8Bytes: Long = 480_000L,
        maxSourceUtf8Bytes: Long = 512L * 1024L * 1024L,
        maxLines: Long = 10_000_000L,
        minimumTrainExamples: Int = 100,
        minimumValidationExamples: Int = 20,
        minimumTestExamples: Int = KellvynNeuralBehaviorBenchmark.MINIMUM_EXAMPLES
    ): CorpusOutcome = withContext(Dispatchers.IO) {
        val trainingContext = currentCoroutineContext()
        val selection = KellvynJsonlCorpusReader.read(
            input = input,
            config = config,
            samplingSeed = samplingSeed,
            maxExamples = maxExamples,
            maxRetainedUtf8Bytes = maxRetainedUtf8Bytes,
            maxSourceUtf8Bytes = maxSourceUtf8Bytes,
            maxLines = maxLines,
            minimumTrainExamples = minimumTrainExamples,
            minimumValidationExamples = minimumValidationExamples,
            minimumTestExamples = minimumTestExamples,
            cancellationCheck = { trainingContext.ensureActive() }
        )
        trainingContext.ensureActive()
        if (!selection.report.readyForTraining) {
            return@withContext CorpusOutcome(
                audit = selection.report,
                training = null
            )
        }
        val outcome = trainAndInstall(
            examples = selection.examples,
            config = config,
            createdAtMillis = createdAtMillis
        )
        CorpusOutcome(audit = selection.report, training = outcome)
    }

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
            detail = "Checkpoint KN-004 instalado. Próxima etapa: benchmark comportamental.",
            heldOutTestExamples = examples.filter { it.split == KellvynTrainingSplit.TEST }
        )
    }
}
