package com.kellvyn.assistant.neural

import com.kellvyn.assistant.KellvynDecisionProtocolBehaviorBenchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Installs only a KN-004 checkpoint that clears the numeric quality gate.
 *
 * Corpus scanning and checkpoint persistence use Dispatchers.IO. CPU-heavy training is delegated
 * to an injected executor; KellvynRuntime routes it through the resource broker and Dispatchers.Default.
 * The previous installed bundle is untouched when training is cancelled or the quality gate rejects it.
 * This controller does not fetch data, use a cloud model, create agents, or change the UI.
 */
internal class KellvynNeuralTrainingController(
    private val modelStore: KellvynNeuralModelStore,
    private val trainModel: suspend (
        List<KellvynInstructionTrainingExample>,
        KellvynMultiBlockTrainingConfig,
        Long,
        () -> Unit
    ) -> KellvynMultiBlockTrainingResult = { examples, config, createdAtMillis, cancellationCheck ->
        withContext(Dispatchers.Default) {
            KellvynMultiBlockTrainingPipeline.train(
                examples = examples,
                config = config,
                createdAtMillis = createdAtMillis,
                cancellationCheck = cancellationCheck
            )
        }
    }
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
        minimumTestExamples: Int = KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_EXAMPLES
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
        expectedAudit: KellvynJsonlCorpusReader.Report,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        samplingSeed: Long = config.seed,
        maxExamples: Int = 1_000,
        maxRetainedUtf8Bytes: Long = 480_000L,
        maxSourceUtf8Bytes: Long = 512L * 1024L * 1024L,
        maxLines: Long = 10_000_000L,
        minimumTrainExamples: Int = 100,
        minimumValidationExamples: Int = 20,
        minimumTestExamples: Int = KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_EXAMPLES,
        prepareExamples: ((List<KellvynInstructionTrainingExample>) -> List<KellvynInstructionTrainingExample>)? = null
    ): CorpusOutcome = withContext(Dispatchers.IO) {
        val trainingContext = currentCoroutineContext()
        require(expectedAudit.sourceSha256.matches(Regex("[a-f0-9]{64}"))) {
            "O SHA-256 da auditoria anterior está inválido."
        }
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
        require(selection.report == expectedAudit) {
            "O arquivo ou parâmetros JSONL mudaram desde a auditoria; faça uma nova auditoria antes de treinar."
        }
        if (!selection.report.readyForTraining) {
            return@withContext CorpusOutcome(
                audit = selection.report,
                training = null
            )
        }
        val preparedExamples = prepareExamples?.invoke(selection.examples) ?: selection.examples
        require(preparedExamples.size == selection.examples.size) {
            "A preparação do corpus não pode remover nem acrescentar exemplos."
        }
        require(preparedExamples.zip(selection.examples).all { (prepared, original) ->
            prepared.id == original.id &&
                prepared.completion == original.completion &&
                prepared.split == original.split &&
                prepared.provenance == original.provenance &&
                prepared.prompt.isNotBlank()
        }) {
            "A preparação do corpus só pode transformar o prompt e preservar alvos, partições e proveniência."
        }
        val outcome = trainAndInstall(
            examples = preparedExamples,
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
        val trained = trainModel(
            examples,
            config,
            createdAtMillis
        ) { trainingContext.ensureActive() }
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
        modelStore.saveMultiBlockIfQualityApproved(trained)
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
