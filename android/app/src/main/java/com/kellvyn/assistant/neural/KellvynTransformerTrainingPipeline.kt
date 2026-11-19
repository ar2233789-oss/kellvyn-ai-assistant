package com.kellvyn.assistant.neural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.random.Random

internal data class KellvynTransformerTrainingConfig(
    val contextLength: Int = 256,
    val embeddingSize: Int = 32,
    val headCount: Int = 4,
    val feedForwardSize: Int = 64,
    val seed: Long = 20261117L,
    val maxMerges: Int = 64,
    val epochsPerExample: Int = 1,
    val learningRate: Float = 0.003f,
    val weightDecay: Float = 0.01f,
    val maxTotalTrainingSteps: Long = 100_000L
) {
    init {
        require(contextLength in 1..2048)
        require(embeddingSize in 8..128)
        require(headCount in 1..8)
        require(embeddingSize % headCount == 0)
        require((embeddingSize / headCount) % 2 == 0)
        require(feedForwardSize in 4..512)
        require(maxMerges in 0..4096)
        require(epochsPerExample in 1..10_000)
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f)
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f)
        require(maxTotalTrainingSteps in 1..1_000_000L)
    }
}

internal data class KellvynTransformerFineTuneConfig(
    val epochsPerExample: Int = 1,
    val learningRate: Float = 0.0005f,
    val weightDecay: Float = 0.01f,
    val maxTotalTrainingSteps: Long = 100_000L
) {
    init {
        require(epochsPerExample in 1..10_000)
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f)
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f)
        require(maxTotalTrainingSteps in 1..1_000_000L)
    }
}

internal data class KellvynTransformerTrainingManifest(
    val createdAtMillis: Long,
    val entries: List<KellvynTrainingManifestEntry>,
    val maxMerges: Int,
    val contextLength: Int,
    val embeddingSize: Int,
    val headCount: Int,
    val feedForwardSize: Int,
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
        require(initialValidationLoss.isFinite() && finalValidationLoss.isFinite())
        require(heldOutTestLoss.isFinite())
        require(totalTrainingSteps > 0L)
        require(vocabularySize >= 256)
    }
}

internal data class KellvynTransformerTrainingResult(
    val tokenizer: KellvynBpeTokenizer,
    val model: KellvynTransformerLanguageModel,
    val manifest: KellvynTransformerTrainingManifest
)

internal data class KellvynRestoredTransformerBundle(
    val tokenizer: KellvynBpeTokenizer,
    val model: KellvynTransformerLanguageModel,
    val manifest: KellvynTransformerTrainingManifest
)

/**
 * Local-only, provenance-aware supervised training for KN-003.
 * Tokenizer merges are learned from TRAIN prompts/completions only. VALIDATION and TEST
 * are used exclusively for evaluation. Only completion tokens contribute to gradients.
 */
internal object KellvynTransformerTrainingPipeline {
    private const val MAX_EXAMPLES = 1_000
    private const val MAX_EXAMPLE_BYTES = 65_536
    private const val MAX_TOTAL_BYTES = 500_000L
    private const val MAX_STEPS_PER_EXAMPLE = 50_000L
    private const val MAX_TOKENIZED_EXAMPLE = 100_000L

    private val SECRET_ASSIGNMENT = Regex(
        """(?i)\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\b\s*[:=]\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
    )
    private val BEARER_TOKEN = Regex("""(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}""")
    private val PRIVATE_KEY = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")
    private val KNOWN_TOKEN = Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{16,})\b""")

    fun train(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynTransformerTrainingConfig = KellvynTransformerTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        cancellationCheck: () -> Unit = {}
    ): KellvynTransformerTrainingResult {
        cancellationCheck()
        require(createdAtMillis > 0L)
        require(examples.size in 3..MAX_EXAMPLES) {
            "Forneça exemplos supervisionados de treino, validação e teste."
        }
        require(examples.map { it.id }.distinct().size == examples.size) {
            "Há IDs de exemplos Transformer duplicados."
        }

        var totalBytes = 0L
        val contentHashes = mutableSetOf<String>()
        val promptPartitions = mutableMapOf<String, KellvynTrainingSplit>()
        for (example in examples) {
            cancellationCheck()
            val sampleBytes = example.prompt.toByteArray(Charsets.UTF_8).size.toLong() +
                example.completion.toByteArray(Charsets.UTF_8).size.toLong()
            require(sampleBytes in 2..MAX_EXAMPLE_BYTES.toLong()) {
                "O exemplo Transformer excede o limite individual de bytes."
            }
            require(
                !containsSecretMaterial(example.prompt) &&
                    !containsSecretMaterial(example.completion)
            ) {
                "O exemplo Transformer parece conter credenciais ou chave privada; foi recusado."
            }
            totalBytes += sampleBytes
            require(totalBytes <= MAX_TOTAL_BYTES) {
                "O corpus Transformer excede o limite experimental de bytes."
            }

            val promptHash = sha256(example.prompt)
            val previousSplit = promptPartitions.putIfAbsent(promptHash, example.split)
            require(previousSplit == null || previousSplit == example.split) {
                "O mesmo prompt Transformer apareceu em partições diferentes: " + example.id + "."
            }
            val contentHash = sha256(example.prompt + "\u0000" + example.completion)
            require(contentHashes.add(contentHash)) {
                "Exemplo Transformer duplicado: " + example.id + "."
            }
        }

        // Canonical ordering makes tokenizer training and the epoch schedule independent
        // of the order in which callers supplied the dataset.
        val train = examples.filter { it.split == KellvynTrainingSplit.TRAIN }.sortedBy { it.id }
        val validation = examples.filter { it.split == KellvynTrainingSplit.VALIDATION }.sortedBy { it.id }
        val test = examples.filter { it.split == KellvynTrainingSplit.TEST }.sortedBy { it.id }
        require(train.isNotEmpty() && validation.isNotEmpty() && test.isNotEmpty()) {
            "As três partições são obrigatórias: TRAIN, VALIDATION e TEST."
        }

        val tokenizer = KellvynBpeTokenizer.train(
            corpus = train.flatMap { listOf(it.prompt, it.completion) },
            maxMerges = config.maxMerges,
            cancellationCheck = cancellationCheck
        )

        data class EncodedExample(
            val prompt: IntArray,
            val completion: IntArray
        )

        fun encode(partition: List<KellvynInstructionTrainingExample>): List<EncodedExample> =
            partition.map { example ->
                cancellationCheck()
                val prompt = tokenizer.encode(example.prompt)
                val completionTextTokens = tokenizer.encode(example.completion)
                val endOfSequenceTokenId = requireNotNull(tokenizer.endOfSequenceTokenId) {
                    "O tokenizer KN-003 não reservou o token de encerramento."
                }
                val completion = completionTextTokens + intArrayOf(endOfSequenceTokenId)
                require(prompt.isNotEmpty() && completionTextTokens.isNotEmpty()) {
                    "O exemplo Transformer precisa conter prompt e resposta tokenizáveis."
                }
                require(prompt.size.toLong() + completion.size.toLong() <= MAX_TOKENIZED_EXAMPLE) {
                    "O exemplo Transformer excedeu o limite tokenizado."
                }
                EncodedExample(prompt, completion)
            }

        val trainEncoded = encode(train)
        val validationEncoded = encode(validation)
        val testEncoded = encode(test)
        val plannedSteps = trainEncoded.sumOf {
            it.completion.size.toLong() * config.epochsPerExample.toLong()
        }
        require(plannedSteps in 1..config.maxTotalTrainingSteps) {
            "O treinamento KN-003 excederia o orçamento de passos."
        }
        require(trainEncoded.all {
            it.completion.size.toLong() * config.epochsPerExample.toLong() <= MAX_STEPS_PER_EXAMPLE
        }) {
            "Uma completion excederia o limite de passos por exemplo."
        }

        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            headCount = config.headCount,
            feedForwardSize = config.feedForwardSize,
            seed = config.seed
        )

        fun meanCompletionLoss(partition: List<EncodedExample>): Double {
            var weightedLoss = 0.0
            var tokenCount = 0L
            for (example in partition) {
                cancellationCheck()
                weightedLoss += model.evaluateCompletionLoss(
                    example.prompt,
                    example.completion
                ) * example.completion.size.toDouble()
                tokenCount += example.completion.size.toLong()
            }
            require(tokenCount > 0L)
            return weightedLoss / tokenCount.toDouble()
        }

        val initialValidationLoss = meanCompletionLoss(validationEncoded)

        // Train one pass over the complete dataset per epoch. Training all epochs of
        // one example before moving to the next causes severe order bias/forgetting.
        // A seeded shuffle gives reproducible SGD while varying example order per epoch.
        repeat(config.epochsPerExample) { epoch ->
            cancellationCheck()
            val epochExamples = trainEncoded.shuffled(Random(config.seed + epoch.toLong()))
            for (example in epochExamples) {
                cancellationCheck()
                model.trainCompletionEpoch(
                    promptTokenIds = example.prompt,
                    completionTokenIds = example.completion,
                    learningRate = config.learningRate,
                    weightDecay = config.weightDecay,
                    cancellationCheck = cancellationCheck
                )
            }
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
        val manifest = KellvynTransformerTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = tokenizer.mergeCount,
            contextLength = model.config.contextLength,
            embeddingSize = model.config.embeddingSize,
            headCount = model.config.headCount,
            feedForwardSize = model.config.feedForwardSize,
            seed = model.config.seed,
            epochsPerExample = config.epochsPerExample,
            learningRate = config.learningRate,
            weightDecay = config.weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = model.trainingSteps,
            vocabularySize = model.config.vocabularySize
        )
        return KellvynTransformerTrainingResult(tokenizer, model, manifest)
    }

    /**
     * Unsupervised next-token pretraining from authorized local documents.
     *
     * The tokenizer is learned from TRAIN documents only. VALIDATION and TEST remain
     * held out. Each epoch shuffles documents deterministically and updates the same
     * KN-003 weights one document at a time.
     */
    fun pretrainDocuments(
        documents: List<KellvynTrainingDocument>,
        config: KellvynTransformerTrainingConfig = KellvynTransformerTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        cancellationCheck: () -> Unit = {}
    ): KellvynTransformerTrainingResult {
        cancellationCheck()
        require(createdAtMillis > 0L)
        require(documents.size in 3..MAX_EXAMPLES) {
            "Forneça documentos de treino, validação e teste."
        }
        require(documents.map { it.id }.distinct().size == documents.size) {
            "Há IDs de documentos Transformer duplicados."
        }

        val orderedDocuments = documents.sortedBy { it.id }
        var totalBytes = 0L
        val contentHashes = mutableSetOf<String>()
        for (document in orderedDocuments) {
            cancellationCheck()
            val byteCount = document.text.toByteArray(Charsets.UTF_8).size
            require(byteCount in 1..MAX_EXAMPLE_BYTES) {
                "O documento Transformer ${document.id} excede o limite individual."
            }
            require(!containsSecretMaterial(document.text)) {
                "O documento Transformer ${document.id} parece conter credenciais ou chave privada."
            }
            totalBytes += byteCount.toLong()
            require(totalBytes <= MAX_TOTAL_BYTES) {
                "O corpus Transformer excede o limite experimental de bytes."
            }
            require(contentHashes.add(sha256(document.text))) {
                "Documento duplicado entre partições Transformer; isso causaria vazamento."
            }
        }

        val trainDocuments = orderedDocuments.filter {
            it.split == KellvynTrainingSplit.TRAIN
        }
        val validationDocuments = orderedDocuments.filter {
            it.split == KellvynTrainingSplit.VALIDATION
        }
        val testDocuments = orderedDocuments.filter {
            it.split == KellvynTrainingSplit.TEST
        }
        require(
            trainDocuments.isNotEmpty() &&
                validationDocuments.isNotEmpty() &&
                testDocuments.isNotEmpty()
        ) {
            "As três partições são obrigatórias: TRAIN, VALIDATION e TEST."
        }

        val tokenizer = KellvynBpeTokenizer.train(
            corpus = trainDocuments.map { it.text },
            maxMerges = config.maxMerges,
            cancellationCheck = cancellationCheck
        )

        fun encode(partition: List<KellvynTrainingDocument>): List<Pair<String, IntArray>> =
            partition.map { document ->
                cancellationCheck()
                val tokens = tokenizer.encode(document.text)
                require(tokens.size in 2..MAX_TOKENIZED_EXAMPLE) {
                    "O documento Transformer ${document.id} precisa produzir entre 2 e 100000 tokens."
                }
                document.id to tokens
            }

        val trainEncoded = encode(trainDocuments)
        val validationEncoded = encode(validationDocuments)
        val testEncoded = encode(testDocuments)
        val plannedSteps = trainEncoded.sumOf { (_, tokens) ->
            (tokens.size - 1L) * config.epochsPerExample.toLong()
        }
        require(plannedSteps in 1..config.maxTotalTrainingSteps) {
            "O pré-treinamento Transformer excederia o orçamento total de passos."
        }
        require(trainEncoded.all { (_, tokens) ->
            (tokens.size - 1L) * config.epochsPerExample.toLong() <= MAX_STEPS_PER_EXAMPLE
        }) {
            "Um documento Transformer excederia o limite de passos por chamada."
        }

        val model = KellvynTransformerLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            headCount = config.headCount,
            feedForwardSize = config.feedForwardSize,
            seed = config.seed
        )

        fun meanLoss(partition: List<Pair<String, IntArray>>): Double {
            var weightedLoss = 0.0
            var tokenCount = 0L
            for ((_, tokens) in partition) {
                cancellationCheck()
                val count = tokens.size - 1
                weightedLoss += model.evaluateLoss(tokens) * count.toDouble()
                tokenCount += count.toLong()
            }
            require(tokenCount > 0L)
            return weightedLoss / tokenCount.toDouble()
        }

        val initialValidationLoss = meanLoss(validationEncoded)
        repeat(config.epochsPerExample) { epoch ->
            cancellationCheck()
            val epochDocuments = trainEncoded.shuffled(Random(config.seed + epoch.toLong()))
            for ((_, tokens) in epochDocuments) {
                cancellationCheck()
                model.trainTokensEpoch(
                    tokenIds = tokens,
                    learningRate = config.learningRate,
                    weightDecay = config.weightDecay,
                    cancellationCheck = cancellationCheck
                )
            }
        }
        cancellationCheck()
        val finalValidationLoss = meanLoss(validationEncoded)
        val heldOutTestLoss = meanLoss(testEncoded)
        cancellationCheck()

        val entries = orderedDocuments.map { document ->
            KellvynTrainingManifestEntry(
                id = document.id,
                split = document.split,
                permission = document.provenance.permission,
                licenseId = document.provenance.licenseId,
                sourceReference = document.provenance.sourceReference,
                rightsEvidence = document.provenance.rightsEvidence,
                contentSha256 = sha256(document.text)
            )
        }
        val manifest = KellvynTransformerTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = tokenizer.mergeCount,
            contextLength = model.config.contextLength,
            embeddingSize = model.config.embeddingSize,
            headCount = model.config.headCount,
            feedForwardSize = model.config.feedForwardSize,
            seed = model.config.seed,
            epochsPerExample = config.epochsPerExample,
            learningRate = config.learningRate,
            weightDecay = config.weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = model.trainingSteps,
            vocabularySize = model.config.vocabularySize
        )
        return KellvynTransformerTrainingResult(tokenizer, model, manifest)
    }

    /**
     * Supervised fine-tuning that continues from an existing trained KN-003 bundle.
     *
     * The base model is cloned through its own checkpoint format before mutation. If
     * validation fails or training is cancelled, the caller's stored pretraining bundle
     * remains untouched. The pretraining tokenizer and vocabulary are reused unchanged.
     */
    fun fineTuneInstructions(
        base: KellvynRestoredTransformerBundle,
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynTransformerFineTuneConfig = KellvynTransformerFineTuneConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        cancellationCheck: () -> Unit = {}
    ): KellvynTransformerTrainingResult {
        cancellationCheck()
        require(createdAtMillis > 0L)
        require(base.model.trainingSteps > 0L) {
            "O ajuste fino exige um checkpoint KN-003 previamente treinado."
        }
        val endOfSequenceTokenId = requireNotNull(base.tokenizer.endOfSequenceTokenId) {
            "O tokenizer base não possui token EOS; não é possível ajustar respostas supervisionadas."
        }
        require(examples.size in 3..MAX_EXAMPLES) {
            "Forneça exemplos supervisionados de treino, validação e teste."
        }
        require(examples.map { it.id }.distinct().size == examples.size) {
            "Há IDs de exemplos de ajuste fino duplicados."
        }
        val baseIds = base.manifest.entries.map { it.id }.toSet()
        require(examples.none { it.id in baseIds }) {
            "Um ID de exemplo de ajuste fino já existe no manifesto do pré-treinamento."
        }

        val orderedExamples = examples.sortedBy { it.id }
        var totalBytes = 0L
        val contentHashes = mutableSetOf<String>()
        val promptPartitions = mutableMapOf<String, KellvynTrainingSplit>()
        for (example in orderedExamples) {
            cancellationCheck()
            val sampleBytes =
                example.prompt.toByteArray(Charsets.UTF_8).size.toLong() +
                    example.completion.toByteArray(Charsets.UTF_8).size.toLong()
            require(sampleBytes in 2..MAX_EXAMPLE_BYTES.toLong()) {
                "O exemplo de ajuste fino ${example.id} excede o limite individual."
            }
            require(
                !containsSecretMaterial(example.prompt) &&
                    !containsSecretMaterial(example.completion)
            ) {
                "O exemplo de ajuste fino ${example.id} parece conter credenciais ou chave privada."
            }
            totalBytes += sampleBytes
            require(totalBytes <= MAX_TOTAL_BYTES) {
                "O corpus de ajuste fino excede o limite experimental de bytes."
            }
            val promptHash = sha256(example.prompt)
            val previousSplit = promptPartitions.putIfAbsent(promptHash, example.split)
            require(previousSplit == null || previousSplit == example.split) {
                "O mesmo prompt de ajuste fino apareceu em partições diferentes: ${example.id}."
            }
            require(contentHashes.add(sha256(example.prompt + "\u0000" + example.completion))) {
                "Exemplo de ajuste fino duplicado: ${example.id}."
            }
        }

        val train = orderedExamples.filter { it.split == KellvynTrainingSplit.TRAIN }
        val validation = orderedExamples.filter { it.split == KellvynTrainingSplit.VALIDATION }
        val test = orderedExamples.filter { it.split == KellvynTrainingSplit.TEST }
        require(train.isNotEmpty() && validation.isNotEmpty() && test.isNotEmpty()) {
            "As três partições são obrigatórias: TRAIN, VALIDATION e TEST."
        }

        data class EncodedExample(val prompt: IntArray, val completion: IntArray)

        fun encode(partition: List<KellvynInstructionTrainingExample>): List<EncodedExample> =
            partition.map { example ->
                cancellationCheck()
                val prompt = base.tokenizer.encode(example.prompt)
                val response = base.tokenizer.encode(example.completion)
                require(prompt.isNotEmpty() && response.isNotEmpty()) {
                    "O exemplo de ajuste fino ${example.id} precisa de prompt e resposta tokenizáveis."
                }
                val completion = response + intArrayOf(endOfSequenceTokenId)
                require(prompt.size.toLong() + completion.size.toLong() <= MAX_TOKENIZED_EXAMPLE) {
                    "O exemplo de ajuste fino ${example.id} excede o limite tokenizado."
                }
                EncodedExample(prompt, completion)
            }

        val trainEncoded = encode(train)
        val validationEncoded = encode(validation)
        val testEncoded = encode(test)
        val plannedSteps = trainEncoded.sumOf {
            it.completion.size.toLong() * config.epochsPerExample.toLong()
        }
        require(plannedSteps in 1..config.maxTotalTrainingSteps) {
            "O ajuste fino excederia o orçamento total de passos."
        }
        require(trainEncoded.all {
            it.completion.size.toLong() * config.epochsPerExample.toLong() <= MAX_STEPS_PER_EXAMPLE
        }) {
            "Uma resposta de ajuste fino excederia o limite de passos por exemplo."
        }

        val checkpoint = ByteArrayOutputStream().also(base.model::writeCheckpoint).toByteArray()
        val model = KellvynTransformerLanguageModel.readCheckpoint(
            ByteArrayInputStream(checkpoint)
        )

        fun meanCompletionLoss(partition: List<EncodedExample>): Double {
            var weightedLoss = 0.0
            var tokenCount = 0L
            for (example in partition) {
                cancellationCheck()
                weightedLoss += model.evaluateCompletionLoss(
                    example.prompt,
                    example.completion
                ) * example.completion.size.toDouble()
                tokenCount += example.completion.size.toLong()
            }
            require(tokenCount > 0L)
            return weightedLoss / tokenCount.toDouble()
        }

        val initialValidationLoss = meanCompletionLoss(validationEncoded)
        repeat(config.epochsPerExample) { epoch ->
            cancellationCheck()
            val epochExamples = trainEncoded.shuffled(Random(model.config.seed + epoch.toLong()))
            for (example in epochExamples) {
                cancellationCheck()
                model.trainCompletionEpoch(
                    promptTokenIds = example.prompt,
                    completionTokenIds = example.completion,
                    learningRate = config.learningRate,
                    weightDecay = config.weightDecay,
                    cancellationCheck = cancellationCheck
                )
            }
        }
        cancellationCheck()
        val finalValidationLoss = meanCompletionLoss(validationEncoded)
        val heldOutTestLoss = meanCompletionLoss(testEncoded)
        cancellationCheck()

        val newEntries = orderedExamples.map { example ->
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
        val combinedEntries = base.manifest.entries + newEntries
        require(combinedEntries.map { it.id }.distinct().size == combinedEntries.size) {
            "O manifesto combinado do ajuste fino contém IDs duplicados."
        }
        val manifest = KellvynTransformerTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = combinedEntries,
            maxMerges = base.tokenizer.mergeCount,
            contextLength = model.config.contextLength,
            embeddingSize = model.config.embeddingSize,
            headCount = model.config.headCount,
            feedForwardSize = model.config.feedForwardSize,
            seed = model.config.seed,
            epochsPerExample = config.epochsPerExample,
            learningRate = config.learningRate,
            weightDecay = config.weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = model.trainingSteps,
            vocabularySize = model.config.vocabularySize
        )
        return KellvynTransformerTrainingResult(base.tokenizer, model, manifest)
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
