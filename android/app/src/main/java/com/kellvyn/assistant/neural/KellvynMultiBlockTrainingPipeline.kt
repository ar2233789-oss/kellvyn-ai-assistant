package com.kellvyn.assistant.neural

import java.security.MessageDigest
import kotlin.math.ln

internal data class KellvynMultiBlockTrainingConfig(
    val contextLength: Int = 256,
    val embeddingSize: Int = 32,
    val headCount: Int = 4,
    val feedForwardSize: Int = 64,
    val depth: Int = 2,
    val seed: Long = 20261120L,
    val maxMerges: Int = 64,
    val epochsPerExample: Int = 1,
    val learningRate: Float = 0.003f,
    val weightDecay: Float = 0.01f,
    val maxTotalTrainingSteps: Long = 100_000L,
    /** Hard upper bound for estimated training working set before any model weights are allocated. */
    val maxTrainingWorkingSetBytes: Long = 128L * 1024L * 1024L
) {
    init {
        require(contextLength in 1..2048)
        require(embeddingSize in 8..256)
        require(headCount in 1..16)
        require(embeddingSize % headCount == 0)
        require((embeddingSize / headCount) % 2 == 0)
        require(feedForwardSize in 4..1024)
        require(depth in 1..8)
        require(maxMerges in 0..4096)
        require(epochsPerExample in 1..10_000)
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f)
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f)
        require(maxTotalTrainingSteps in 1..1_000_000L)
        require(maxTrainingWorkingSetBytes in 16L * 1024L * 1024L..1024L * 1024L * 1024L)
    }

    companion object {
        /**
         * Larger mobile candidate for measured-device experiments, not a validated Poco F6 profile.
         * At 769 vocabulary entries its analytical training working-set lower bound is about 101 MiB.
         */
        fun balancedMobileCandidate(): KellvynMultiBlockTrainingConfig =
            KellvynMultiBlockTrainingConfig(
                contextLength = 256,
                embeddingSize = 128,
                headCount = 8,
                feedForwardSize = 512,
                depth = 6,
                maxMerges = 512,
                epochsPerExample = 1,
                learningRate = 0.003f,
                weightDecay = 0.01f,
                maxTotalTrainingSteps = 100_000L,
                maxTrainingWorkingSetBytes = 128L * 1024L * 1024L
            )
    }
}

internal data class KellvynMultiBlockTrainingManifest(
    val createdAtMillis: Long,
    val entries: List<KellvynTrainingManifestEntry>,
    val maxMerges: Int,
    val contextLength: Int,
    val embeddingSize: Int,
    val headCount: Int,
    val feedForwardSize: Int,
    val depth: Int,
    val seed: Long,
    val epochsPerExample: Int,
    val learningRate: Float,
    val weightDecay: Float,
    val initialValidationLoss: Double,
    val finalValidationLoss: Double,
    val heldOutTestLoss: Double,
    val totalTrainingSteps: Long,
    val vocabularySize: Int
) {
    init {
        require(createdAtMillis > 0L)
        require(entries.isNotEmpty())
        require(depth in 1..8)
        require(initialValidationLoss.isFinite() && initialValidationLoss > 0.0)
        require(finalValidationLoss.isFinite() && finalValidationLoss > 0.0)
        require(heldOutTestLoss.isFinite() && heldOutTestLoss > 0.0)
        require(totalTrainingSteps > 0L)
        require(vocabularySize >= 256)
    }
}

internal data class KellvynMultiBlockTrainingResult(
    val tokenizer: KellvynBpeTokenizer,
    val model: KellvynMultiBlockTransformer,
    val manifest: KellvynMultiBlockTrainingManifest
)

internal data class KellvynRestoredMultiBlockBundle(
    val tokenizer: KellvynBpeTokenizer,
    val model: KellvynMultiBlockTransformer,
    val manifest: KellvynMultiBlockTrainingManifest
)

/**
 * Local-only, provenance-aware supervised training for KN-004.
 * BPE is trained only on TRAIN. Validation/test never update parameters.
 */
internal object KellvynMultiBlockTrainingPipeline {
    private const val MAX_EXAMPLES = 1_000
    private const val MAX_EXAMPLE_BYTES = 65_536
    private const val MAX_TOTAL_BYTES = 500_000L
    private const val MAX_STEPS_PER_EXAMPLE = 50_000L
    private const val MAX_TOKENIZED_EXAMPLE = 100_000L

    private val SECRET_ASSIGNMENT = Regex(
        """(?i)\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\b\s*[:=]\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
    )
    private val BEARER_TOKEN = Regex(
        """(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}"""
    )
    private val PRIVATE_KEY = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")
    private val KNOWN_TOKEN = Regex(
        """\b(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{16,})\b"""
    )

    fun train(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynMultiBlockTrainingConfig = KellvynMultiBlockTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        cancellationCheck: () -> Unit = {}
    ): KellvynMultiBlockTrainingResult {
        cancellationCheck()
        require(createdAtMillis > 0L)
        require(examples.size in 3..MAX_EXAMPLES)
        require(examples.map { it.id }.distinct().size == examples.size)

        var totalBytes = 0L
        val contentHashes = mutableSetOf<String>()
        val promptPartitions = mutableMapOf<String, KellvynTrainingSplit>()
        for (example in examples) {
            cancellationCheck()
            val sampleBytes = example.prompt.toByteArray(Charsets.UTF_8).size.toLong() +
                example.completion.toByteArray(Charsets.UTF_8).size.toLong()
            require(sampleBytes in 2..MAX_EXAMPLE_BYTES.toLong()) {
                "O exemplo KN-004 excede o limite individual de bytes."
            }
            require(
                !containsSecretMaterial(example.prompt) &&
                    !containsSecretMaterial(example.completion)
            ) {
                "O exemplo KN-004 parece conter credenciais ou chave privada; foi recusado."
            }
            totalBytes += sampleBytes
            require(totalBytes <= MAX_TOTAL_BYTES) {
                "O corpus experimental KN-004 excede o limite de bytes."
            }
            val promptHash = sha256(example.prompt)
            val previousSplit = promptPartitions.putIfAbsent(promptHash, example.split)
            require(previousSplit == null || previousSplit == example.split) {
                "O mesmo prompt KN-004 apareceu em partições diferentes: ${example.id}."
            }
            require(contentHashes.add(sha256(example.prompt + "\u0000" + example.completion))) {
                "Exemplo KN-004 duplicado: ${example.id}."
            }
        }

        val train = examples.filter { it.split == KellvynTrainingSplit.TRAIN }
        val validation = examples.filter { it.split == KellvynTrainingSplit.VALIDATION }
        val test = examples.filter { it.split == KellvynTrainingSplit.TEST }
        require(train.isNotEmpty() && validation.isNotEmpty() && test.isNotEmpty()) {
            "As três partições TRAIN, VALIDATION e TEST são obrigatórias."
        }

        val tokenizer = KellvynBpeTokenizer.train(
            corpus = train.flatMap { listOf(it.prompt, it.completion) },
            maxMerges = config.maxMerges,
            cancellationCheck = cancellationCheck
        )

        data class EncodedExample(val prompt: IntArray, val completion: IntArray)

        fun encode(partition: List<KellvynInstructionTrainingExample>): List<EncodedExample> =
            partition.map { example ->
                cancellationCheck()
                val prompt = tokenizer.encode(example.prompt)
                val completionText = tokenizer.encode(example.completion)
                val eos = requireNotNull(tokenizer.endOfSequenceTokenId)
                val completion = completionText + intArrayOf(eos)
                require(prompt.isNotEmpty() && completionText.isNotEmpty())
                require(prompt.size.toLong() + completion.size.toLong() <= MAX_TOKENIZED_EXAMPLE)
                EncodedExample(prompt, completion)
            }

        val trainEncoded = encode(train)
        val validationEncoded = encode(validation)
        val testEncoded = encode(test)
        val plannedSteps = trainEncoded.sumOf {
            it.completion.size.toLong() * config.epochsPerExample.toLong()
        }
        require(plannedSteps in 1..config.maxTotalTrainingSteps) {
            "O treinamento KN-004 excederia o orçamento total de passos."
        }
        require(trainEncoded.all {
            it.completion.size.toLong() * config.epochsPerExample.toLong() <= MAX_STEPS_PER_EXAMPLE
        }) {
            "Uma completion excederia o limite de passos por exemplo KN-004."
        }

        val modelConfig = KellvynMultiBlockTransformer.Config(
            vocabularySize = tokenizer.vocabularySize,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            headCount = config.headCount,
            feedForwardSize = config.feedForwardSize,
            depth = config.depth,
            seed = config.seed
        )
        val estimatedWorkingSetBytes = modelConfig.estimatedTrainingWorkingSetBytes()
        require(estimatedWorkingSetBytes <= config.maxTrainingWorkingSetBytes) {
            "Treinamento KN-004 recusado antes da alocação: estimativa de " +
                estimatedWorkingSetBytes + " bytes excede o orçamento de " +
                config.maxTrainingWorkingSetBytes + " bytes."
        }
        val model = KellvynMultiBlockTransformer.create(modelConfig)

        fun meanCompletionLoss(partition: List<EncodedExample>): Double {
            var weightedLoss = 0.0
            var tokenCount = 0L
            for (example in partition) {
                cancellationCheck()
                weightedLoss += model.evaluateCompletionLoss(
                    example.prompt, example.completion
                ) * example.completion.size.toDouble()
                tokenCount += example.completion.size.toLong()
            }
            require(tokenCount > 0L)
            return weightedLoss / tokenCount.toDouble()
        }

        val initialValidationLoss = meanCompletionLoss(validationEncoded)
        for (example in trainEncoded) {
            cancellationCheck()
            model.trainCompletion(
                promptTokenIds = example.prompt,
                completionTokenIds = example.completion,
                epochs = config.epochsPerExample,
                learningRate = config.learningRate,
                weightDecay = config.weightDecay,
                cancellationCheck = cancellationCheck
            )
        }
        cancellationCheck()
        val finalValidationLoss = meanCompletionLoss(validationEncoded)
        val heldOutTestLoss = meanCompletionLoss(testEncoded)
        cancellationCheck()

        val entries = examples.map { example ->
            KellvynTrainingManifestEntry(
                id = example.id,
                split = example.split,
                permission = example.provenance.permission,
                licenseId = example.provenance.licenseId,
                sourceReference = example.provenance.sourceReference,
                rightsEvidence = example.provenance.rightsEvidence,
                contentSha256 = sha256(example.prompt + "\u0000" + example.completion)
            )
        }
        val manifest = KellvynMultiBlockTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = tokenizer.mergeCount,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            headCount = config.headCount,
            feedForwardSize = config.feedForwardSize,
            depth = config.depth,
            seed = config.seed,
            epochsPerExample = config.epochsPerExample,
            learningRate = config.learningRate,
            weightDecay = config.weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = model.trainingSteps,
            vocabularySize = tokenizer.vocabularySize
        )
        return KellvynMultiBlockTrainingResult(tokenizer, model, manifest)
    }

    private fun containsSecretMaterial(text: String): Boolean =
        SECRET_ASSIGNMENT.containsMatchIn(text) ||
            BEARER_TOKEN.containsMatchIn(text) ||
            PRIVATE_KEY.containsMatchIn(text) ||
            KNOWN_TOKEN.containsMatchIn(text)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * This gate only admits a checkpoint to the protocol benchmark. It never proves general
 * conversational competence or production readiness.
 */
internal object KellvynMultiBlockTrainingQualityGate {
    data class Report(
        val eligibleForProtocolBenchmark: Boolean,
        val relativeValidationImprovement: Double,
        val generalizationGap: Double,
        val uniformBaselineLoss: Double,
        val reasons: List<String>
    )

    fun evaluate(
        manifest: KellvynMultiBlockTrainingManifest,
        minimumRelativeImprovement: Double = 0.05,
        maximumGeneralizationGap: Double = 0.50
    ): Report {
        require(minimumRelativeImprovement in 0.0..0.5)
        require(maximumGeneralizationGap in 0.0..5.0)
        val reasons = mutableListOf<String>()
        val baseline = ln(manifest.vocabularySize.toDouble())
        val improvement = (manifest.initialValidationLoss - manifest.finalValidationLoss) /
            manifest.initialValidationLoss
        val gap = manifest.heldOutTestLoss - manifest.finalValidationLoss

        if (manifest.contextLength < 128) reasons += "A janela é pequena para benchmark KELLVYN/1."
        if (improvement < minimumRelativeImprovement) {
            reasons += "A validação não melhorou o mínimo relativo exigido."
        }
        if (manifest.finalValidationLoss >= baseline * 0.90) {
            reasons += "A loss de validação permanece próxima da previsão uniforme."
        }
        if (gap > maximumGeneralizationGap) {
            reasons += "A diferença validação/teste sugere generalização insuficiente."
        }
        if (manifest.heldOutTestLoss >= baseline * 0.90) {
            reasons += "A loss do teste permanece próxima da previsão uniforme."
        }
        if (manifest.entries.none { it.split == KellvynTrainingSplit.TRAIN } ||
            manifest.entries.none { it.split == KellvynTrainingSplit.VALIDATION } ||
            manifest.entries.none { it.split == KellvynTrainingSplit.TEST }) {
            reasons += "O manifesto precisa conter TRAIN, VALIDATION e TEST."
        }
        if (manifest.entries.map { it.id }.distinct().size != manifest.entries.size) {
            reasons += "O manifesto contém IDs duplicados."
        }
        if (manifest.entries.any {
                it.licenseId.isBlank() || it.sourceReference.isBlank() ||
                    it.rightsEvidence.isBlank() ||
                    !it.contentSha256.matches(Regex("[a-f0-9]{64}"))
            }) {
            reasons += "A proveniência de treinamento está incompleta."
        }
        return Report(
            eligibleForProtocolBenchmark = reasons.isEmpty(),
            relativeValidationImprovement = improvement,
            generalizationGap = gap,
            uniformBaselineLoss = baseline,
            reasons = reasons
        )
    }
}
