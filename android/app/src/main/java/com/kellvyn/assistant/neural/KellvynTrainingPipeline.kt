package com.kellvyn.assistant.neural

import java.security.MessageDigest

internal enum class KellvynTrainingSplit { TRAIN, VALIDATION, TEST }

internal enum class KellvynTrainingPermission {
    OWNER_CREATED,
    OWNER_AUTHORIZED,
    PUBLIC_DOMAIN,
    PERMISSIVE_LICENSE,
    EXPLICIT_LICENSE
}

/**
 * Provenance travels with every text sample. A URL by itself is not a training license:
 * permission and a human-readable rights note are mandatory for non-owner-created sources.
 */
internal data class KellvynTrainingProvenance(
    val permission: KellvynTrainingPermission,
    val licenseId: String,
    val sourceReference: String,
    val rightsEvidence: String
) {
    init {
        require(licenseId.isNotBlank() && licenseId.length <= 120)
        require(sourceReference.isNotBlank() && sourceReference.length <= 500)
        require(rightsEvidence.isNotBlank() && rightsEvidence.length <= 500)
        if (permission == KellvynTrainingPermission.OWNER_CREATED) {
            require(
                licenseId.equals("owner-created", ignoreCase = true) ||
                    licenseId.equals("proprietary-owner", ignoreCase = true)
            )
        }
    }
}

internal data class KellvynTrainingDocument(
    val id: String,
    val text: String,
    val split: KellvynTrainingSplit,
    val provenance: KellvynTrainingProvenance
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(text.isNotBlank())
    }
}

internal data class KellvynInstructionTrainingExample(
    val id: String,
    val prompt: String,
    val completion: String,
    val split: KellvynTrainingSplit,
    val provenance: KellvynTrainingProvenance
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(prompt.isNotBlank() && completion.isNotBlank())
    }
}

internal data class KellvynTrainingManifestEntry(
    val id: String,
    val split: KellvynTrainingSplit,
    val permission: KellvynTrainingPermission,
    val licenseId: String,
    val sourceReference: String,
    val rightsEvidence: String,
    val contentSha256: String
)

internal data class KellvynTrainingManifest(
    val createdAtMillis: Long,
    val entries: List<KellvynTrainingManifestEntry>,
    val maxMerges: Int,
    val contextLength: Int,
    val embeddingSize: Int,
    val seed: Long,
    val epochsPerDocument: Int,
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

internal data class KellvynTrainingConfig(
    val contextLength: Int = 64,
    val embeddingSize: Int = 32,
    val seed: Long = 20261116L,
    val maxMerges: Int = 64,
    val epochsPerDocument: Int = 1,
    val learningRate: Float = 0.003f,
    val weightDecay: Float = 0.01f,
    val maxTotalTrainingSteps: Long = 100_000L
) {
    init {
        require(contextLength in 1..256)
        require(embeddingSize in 4..128)
        require(maxMerges in 0..4096)
        require(epochsPerDocument in 1..10_000)
        require(learningRate.isFinite() && learningRate in 0.000001f..0.05f)
        require(weightDecay.isFinite() && weightDecay in 0.0f..0.1f)
        require(maxTotalTrainingSteps in 1..1_000_000L)
    }
}

internal data class KellvynTrainingResult(
    val tokenizer: KellvynBpeTokenizer,
    val model: KellvynAttentionLanguageModel,
    val manifest: KellvynTrainingManifest
)

/**
 * Reproducible, local-only training orchestration.
 *
 * BPE is learned from TRAIN samples only. Validation is measured before and after training;
 * TEST is evaluated once at the end. No source is fetched by this class. The caller must
 * supply explicit text and provenance for each sample.
 */
internal object KellvynTrainingPipeline {
    private const val MAX_DOCUMENTS = 1_000
    private const val MAX_DOCUMENT_BYTES = 65_536
    private const val MAX_TOTAL_CORPUS_BYTES = 500_000L
    private const val MAX_STEPS_PER_MODEL_CALL = 50_000L
    private val SECRET_ASSIGNMENT = Regex(
        """(?i)\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|client[_ -]?secret)\b\s*[:=]\s*['"]?[A-Za-z0-9._~+/=-]{8,}"""
    )
    private val BEARER_TOKEN = Regex("""(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]{12,}""")
    private val PRIVATE_KEY = Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----""")
    private val KNOWN_TOKEN = Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{16,})\b""")

    /**
     * Supervised training for instruction/decision examples. Only completion tokens
     * contribute to the objective; prompts condition the response but are not targets.
     */
    fun trainInstructions(
        examples: List<KellvynInstructionTrainingExample>,
        config: KellvynTrainingConfig = KellvynTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        cancellationCheck: () -> Unit = {}
    ): KellvynTrainingResult {
        cancellationCheck()
        require(createdAtMillis > 0L)
        require(examples.size in 3..MAX_DOCUMENTS) {
            "Forneça exemplos supervisionados de treino, validação e teste."
        }
        require(examples.map { it.id }.distinct().size == examples.size) {
            "Há IDs de exemplos supervisionados duplicados."
        }

        var totalBytes = 0L
        val hashes = mutableSetOf<String>()
        for (example in examples) {
            cancellationCheck()
            val sampleBytes = example.prompt.toByteArray(Charsets.UTF_8).size.toLong() +
                example.completion.toByteArray(Charsets.UTF_8).size.toLong()
            require(sampleBytes in 2..MAX_DOCUMENT_BYTES.toLong()) {
                "O exemplo " + example.id + " excede o limite individual de bytes."
            }
            require(!containsSecretMaterial(example.prompt) && !containsSecretMaterial(example.completion)) {
                "O exemplo " + example.id + " parece conter credenciais ou chave privada; foi recusado."
            }
            totalBytes += sampleBytes
            require(totalBytes <= MAX_TOTAL_CORPUS_BYTES) {
                "O corpus supervisionado excede o limite experimental de bytes."
            }
            val contentHash = sha256(example.prompt + "\u0000" + example.completion)
            require(hashes.add(contentHash)) {
                "Exemplo supervisionado duplicado entre partições: " + example.id + "."
            }
        }

        val train = examples.filter { it.split == KellvynTrainingSplit.TRAIN }
        val validation = examples.filter { it.split == KellvynTrainingSplit.VALIDATION }
        val test = examples.filter { it.split == KellvynTrainingSplit.TEST }
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
                val promptTokens = tokenizer.encode(example.prompt)
                val completionTokens = tokenizer.encode(example.completion)
                require(promptTokens.isNotEmpty() && completionTokens.isNotEmpty()) {
                    "O exemplo " + example.id + " não produziu tokens para prompt e resposta."
                }
                require(promptTokens.size.toLong() + completionTokens.size.toLong() <= 100_000L) {
                    "O exemplo " + example.id + " excede o limite tokenizado."
                }
                EncodedExample(promptTokens, completionTokens)
            }

        val trainEncoded = encode(train)
        val validationEncoded = encode(validation)
        val testEncoded = encode(test)
        val plannedSteps = trainEncoded.sumOf { it.completion.size.toLong() * config.epochsPerDocument }
        require(plannedSteps in 1..config.maxTotalTrainingSteps) {
            "O treino supervisionado excederia o orçamento total de passos."
        }
        require(trainEncoded.all {
            it.completion.size.toLong() * config.epochsPerDocument <= MAX_STEPS_PER_MODEL_CALL
        }) {
            "Uma resposta supervisionada ultrapassaria o limite de passos por chamada."
        }

        val model = KellvynAttentionLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            seed = config.seed
        )

        fun meanCompletionLoss(partition: List<EncodedExample>): Double {
            var total = 0.0
            var tokenCount = 0L
            for (example in partition) {
                cancellationCheck()
                total += model.evaluateCompletionLoss(example.prompt, example.completion) *
                    example.completion.size.toDouble()
                tokenCount += example.completion.size.toLong()
            }
            require(tokenCount > 0L)
            return total / tokenCount.toDouble()
        }

        val initialValidationLoss = meanCompletionLoss(validationEncoded)
        for (example in trainEncoded) {
            cancellationCheck()
            model.trainCompletion(
                promptTokenIds = example.prompt,
                completionTokenIds = example.completion,
                epochs = config.epochsPerDocument,
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
        val manifest = KellvynTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = tokenizer.mergeCount,
            contextLength = model.config.contextLength,
            embeddingSize = model.config.embeddingSize,
            seed = model.config.seed,
            epochsPerDocument = config.epochsPerDocument,
            learningRate = config.learningRate,
            weightDecay = config.weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = model.trainingSteps,
            vocabularySize = model.config.vocabularySize
        )
        return KellvynTrainingResult(tokenizer, model, manifest)
    }

    fun train(
        documents: List<KellvynTrainingDocument>,
        config: KellvynTrainingConfig = KellvynTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis(),
        cancellationCheck: () -> Unit = {}
    ): KellvynTrainingResult {
        cancellationCheck()
        require(createdAtMillis > 0L)
        require(documents.size in 3..MAX_DOCUMENTS) {
            "Forneça pelo menos um documento de treino, validação e teste."
        }
        require(documents.map { it.id }.distinct().size == documents.size) {
            "Há IDs de documentos duplicados."
        }

        var totalBytes = 0L
        val hashes = mutableSetOf<String>()
        for (document in documents) {
            cancellationCheck()
            val size = document.text.toByteArray(Charsets.UTF_8).size
            require(size in 1..MAX_DOCUMENT_BYTES) {
                "O documento ${document.id} excede o limite individual de bytes."
            }
            require(!containsSecretMaterial(document.text)) {
                "O documento ${document.id} parece conter credenciais ou chave privada; foi recusado."
            }
            totalBytes += size.toLong()
            require(totalBytes <= MAX_TOTAL_CORPUS_BYTES) {
                "O corpus excede o limite experimental de bytes."
            }
            val hash = sha256(document.text)
            require(hashes.add(hash)) {
                "Conteúdo duplicado entre documentos: evite vazamento entre treino, validação e teste."
            }
        }

        val trainDocs = documents.filter { it.split == KellvynTrainingSplit.TRAIN }
        val validationDocs = documents.filter { it.split == KellvynTrainingSplit.VALIDATION }
        val testDocs = documents.filter { it.split == KellvynTrainingSplit.TEST }
        require(trainDocs.isNotEmpty() && validationDocs.isNotEmpty() && testDocs.isNotEmpty()) {
            "As três partições são obrigatórias: TRAIN, VALIDATION e TEST."
        }

        val tokenizer = KellvynBpeTokenizer.train(
            corpus = trainDocs.map { it.text },
            maxMerges = config.maxMerges,
            cancellationCheck = cancellationCheck
        )
        val trainingTokens = trainDocs.map { document ->
            cancellationCheck()
            document.id to tokenizer.encode(document.text)
        }
        val validationTokens = validationDocs.map { document ->
            cancellationCheck()
            document.id to tokenizer.encode(document.text)
        }
        val testTokens = testDocs.map { document ->
            cancellationCheck()
            document.id to tokenizer.encode(document.text)
        }

        fun checkSequence(id: String, tokens: IntArray) {
            require(tokens.size in 2..100_000) {
                "O documento $id precisa produzir entre 2 e 100000 tokens."
            }
        }
        trainingTokens.forEach { (id, tokens) -> checkSequence(id, tokens) }
        validationTokens.forEach { (id, tokens) -> checkSequence(id, tokens) }
        testTokens.forEach { (id, tokens) -> checkSequence(id, tokens) }

        val plannedSteps = trainingTokens.sumOf { (_, tokens) ->
            (tokens.size - 1L) * config.epochsPerDocument.toLong()
        }
        require(plannedSteps in 1..config.maxTotalTrainingSteps) {
            "O treino excederia o orçamento total configurado de passos."
        }
        require(trainingTokens.all { (_, tokens) ->
            (tokens.size - 1L) * config.epochsPerDocument.toLong() <= MAX_STEPS_PER_MODEL_CALL
        }) {
            "Um documento ultrapassaria o limite de passos de uma chamada ao modelo."
        }

        val model = KellvynAttentionLanguageModel.createForTokenizer(
            tokenizer = tokenizer,
            contextLength = config.contextLength,
            embeddingSize = config.embeddingSize,
            seed = config.seed
        )

        fun meanLoss(sequences: List<Pair<String, IntArray>>): Double {
            var total = 0.0
            var examples = 0L
            for ((_, tokens) in sequences) {
                cancellationCheck()
                val count = tokens.size - 1
                total += model.evaluateLoss(tokens) * count.toDouble()
                examples += count.toLong()
            }
            require(examples > 0L)
            return total / examples.toDouble()
        }

        val initialValidationLoss = meanLoss(validationTokens)
        for ((_, tokens) in trainingTokens) {
            cancellationCheck()
            model.trainTokens(
                tokenIds = tokens,
                epochs = config.epochsPerDocument,
                learningRate = config.learningRate,
                weightDecay = config.weightDecay,
                cancellationCheck = cancellationCheck
            )
        }
        cancellationCheck()
        val finalValidationLoss = meanLoss(validationTokens)
        val heldOutTestLoss = meanLoss(testTokens)
        cancellationCheck()

        val entries = documents.map { document ->
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

        val manifest = KellvynTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = tokenizer.mergeCount,
            contextLength = model.config.contextLength,
            embeddingSize = model.config.embeddingSize,
            seed = model.config.seed,
            epochsPerDocument = config.epochsPerDocument,
            learningRate = config.learningRate,
            weightDecay = config.weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = model.trainingSteps,
            vocabularySize = model.config.vocabularySize
        )
        return KellvynTrainingResult(tokenizer, model, manifest)
    }

    private fun containsSecretMaterial(text: String): Boolean =
        SECRET_ASSIGNMENT.containsMatchIn(text) ||
            BEARER_TOKEN.containsMatchIn(text) ||
            PRIVATE_KEY.containsMatchIn(text) ||
            KNOWN_TOKEN.containsMatchIn(text)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
