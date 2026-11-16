package com.kellvyn.assistant.neural

import java.security.MessageDigest
import kotlin.math.max

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
    private const val MAX_DOCUMENTS = 10_000
    private const val MAX_DOCUMENT_BYTES = 65_536
    private const val MAX_TOTAL_CORPUS_BYTES = 500_000L
    private const val MAX_STEPS_PER_MODEL_CALL = 50_000L

    fun train(
        documents: List<KellvynTrainingDocument>,
        config: KellvynTrainingConfig = KellvynTrainingConfig(),
        createdAtMillis: Long = System.currentTimeMillis()
    ): KellvynTrainingResult {
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
            val size = document.text.toByteArray(Charsets.UTF_8).size
            require(size in 1..MAX_DOCUMENT_BYTES) {
                "O documento ${document.id} excede o limite individual de bytes."
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
            maxMerges = config.maxMerges
        )
        val trainingTokens = trainDocs.map { document ->
            document.id to tokenizer.encode(document.text)
        }
        val validationTokens = validationDocs.map { document ->
            document.id to tokenizer.encode(document.text)
        }
        val testTokens = testDocs.map { document ->
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
                val count = tokens.size - 1
                total += model.evaluateLoss(tokens) * count.toDouble()
                examples += count.toLong()
            }
            require(examples > 0L)
            return total / examples.toDouble()
        }

        val initialValidationLoss = meanLoss(validationTokens)
        for ((_, tokens) in trainingTokens) {
            model.trainTokens(
                tokenIds = tokens,
                epochs = config.epochsPerDocument,
                learningRate = config.learningRate,
                weightDecay = config.weightDecay
            )
        }
        val finalValidationLoss = meanLoss(validationTokens)
        val heldOutTestLoss = meanLoss(testTokens)

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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
