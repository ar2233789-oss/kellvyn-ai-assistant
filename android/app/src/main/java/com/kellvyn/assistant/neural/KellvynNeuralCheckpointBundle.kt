package com.kellvyn.assistant.neural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal data class KellvynRestoredNeuralBundle(
    val tokenizer: KellvynBpeTokenizer,
    val model: KellvynAttentionLanguageModel,
    val manifest: KellvynTrainingManifest
)

/**
 * One integrity-checked artifact containing the tokenizer, neural parameters/optimizer,
 * and non-text provenance/metrics manifest from a single training run.
 */
internal object KellvynNeuralCheckpointBundle {
    private const val MAGIC = 0x4B56424E // "KVBN"
    private const val VERSION = 1
    private const val CHECKSUM_BYTES = 32
    private const val MAX_BUNDLE_BYTES = 70 * 1024 * 1024
    private const val MAX_MODEL_BYTES = 64 * 1024 * 1024
    private const val MAX_TOKENIZER_BYTES = 64 * 1024
    private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRIES = 1_000
    private const val MAX_ID_BYTES = 80
    private const val MAX_LICENSE_BYTES = 120
    private const val MAX_REFERENCE_BYTES = 500
    private const val MAX_EVIDENCE_BYTES = 500
    private const val HASH_HEX_LENGTH = 64

    fun writeCheckpoint(result: KellvynTrainingResult, output: OutputStream) {
        require(result.tokenizer.vocabularySize == result.model.config.vocabularySize)
        validateManifest(result.manifest)
        require(result.manifest.vocabularySize == result.model.config.vocabularySize)
        require(result.manifest.contextLength == result.model.config.contextLength)
        require(result.manifest.embeddingSize == result.model.config.embeddingSize)
        require(result.manifest.seed == result.model.config.seed)
        require(result.manifest.totalTrainingSteps == result.model.trainingSteps)
        require(result.manifest.maxMerges == result.tokenizer.mergeCount)

        val tokenizerBytes = ByteArrayOutputStream().also {
            result.tokenizer.writeCheckpoint(it)
        }.toByteArray()
        val modelBytes = ByteArrayOutputStream().also {
            result.model.writeCheckpoint(it)
        }.toByteArray()
        val manifestBytes = encodeManifest(result.manifest)

        require(tokenizerBytes.size in 1..MAX_TOKENIZER_BYTES)
        require(modelBytes.size in 1..MAX_MODEL_BYTES)
        require(manifestBytes.size in 1..MAX_MANIFEST_BYTES)

        val payloadBuffer = ByteArrayOutputStream()
        DataOutputStream(payloadBuffer).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(tokenizerBytes.size)
            data.write(tokenizerBytes)
            data.writeInt(modelBytes.size)
            data.write(modelBytes)
            data.writeInt(manifestBytes.size)
            data.write(manifestBytes)
        }
        val payload = payloadBuffer.toByteArray()
        require(payload.size + CHECKSUM_BYTES <= MAX_BUNDLE_BYTES) {
            "O pacote neural excedeu o limite de tamanho."
        }
        val checksum = MessageDigest.getInstance("SHA-256").digest(payload)
        output.write(payload)
        output.write(checksum)
        output.flush()
    }

    fun readCheckpoint(input: InputStream): KellvynRestoredNeuralBundle {
        val bytes = readBounded(input)
        require(bytes.size in (CHECKSUM_BYTES + 24)..MAX_BUNDLE_BYTES) {
            "Pacote neural vazio, truncado ou acima do limite."
        }

        val payloadLength = bytes.size - CHECKSUM_BYTES
        val payload = bytes.copyOfRange(0, payloadLength)
        val storedChecksum = bytes.copyOfRange(payloadLength, bytes.size)
        val calculatedChecksum = MessageDigest.getInstance("SHA-256").digest(payload)
        require(MessageDigest.isEqual(storedChecksum, calculatedChecksum)) {
            "A integridade do pacote neural não confere."
        }

        val data = DataInputStream(ByteArrayInputStream(payload))
        require(data.readInt() == MAGIC) { "O pacote não pertence à Kellvyn." }
        require(data.readInt() == VERSION) { "Versão do pacote neural não suportada." }

        val tokenizerBytes = readBlob(data, MAX_TOKENIZER_BYTES, "tokenizer")
        val modelBytes = readBlob(data, MAX_MODEL_BYTES, "modelo")
        val manifestBytes = readBlob(data, MAX_MANIFEST_BYTES, "manifesto")
        require(data.available() == 0) { "O pacote neural contém campos extras." }

        val tokenizer = KellvynBpeTokenizer.readCheckpoint(ByteArrayInputStream(tokenizerBytes))
        val model = KellvynAttentionLanguageModel.readCheckpoint(ByteArrayInputStream(modelBytes))
        val manifest = decodeManifest(manifestBytes)

        require(tokenizer.vocabularySize == model.config.vocabularySize) {
            "O tokenizer e o modelo usam vocabulários diferentes."
        }
        require(manifest.vocabularySize == model.config.vocabularySize)
        require(manifest.contextLength == model.config.contextLength)
        require(manifest.embeddingSize == model.config.embeddingSize)
        require(manifest.seed == model.config.seed)
        require(manifest.totalTrainingSteps == model.trainingSteps)
        require(manifest.maxMerges == tokenizer.mergeCount)
        validateManifest(manifest)

        return KellvynRestoredNeuralBundle(tokenizer, model, manifest)
    }

    private fun encodeManifest(manifest: KellvynTrainingManifest): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeLong(manifest.createdAtMillis)
            data.writeInt(manifest.maxMerges)
            data.writeInt(manifest.contextLength)
            data.writeInt(manifest.embeddingSize)
            data.writeLong(manifest.seed)
            data.writeInt(manifest.epochsPerDocument)
            data.writeFloat(manifest.learningRate)
            data.writeFloat(manifest.weightDecay)
            data.writeDouble(manifest.initialValidationLoss)
            data.writeDouble(manifest.finalValidationLoss)
            data.writeDouble(manifest.heldOutTestLoss)
            data.writeLong(manifest.totalTrainingSteps)
            data.writeInt(manifest.vocabularySize)
            data.writeInt(manifest.entries.size)
            manifest.entries.forEach { entry ->
                writeString(data, entry.id, MAX_ID_BYTES)
                data.writeInt(entry.split.ordinal)
                data.writeInt(entry.permission.ordinal)
                writeString(data, entry.licenseId, MAX_LICENSE_BYTES)
                writeString(data, entry.sourceReference, MAX_REFERENCE_BYTES)
                writeString(data, entry.rightsEvidence, MAX_EVIDENCE_BYTES)
                writeString(data, entry.contentSha256, HASH_HEX_LENGTH)
            }
        }
        return buffer.toByteArray()
    }

    private fun decodeManifest(bytes: ByteArray): KellvynTrainingManifest {
        require(bytes.size in 1..MAX_MANIFEST_BYTES)
        val data = DataInputStream(ByteArrayInputStream(bytes))
        val createdAtMillis = data.readLong()
        val maxMerges = data.readInt()
        val contextLength = data.readInt()
        val embeddingSize = data.readInt()
        val seed = data.readLong()
        val epochsPerDocument = data.readInt()
        val learningRate = data.readFloat()
        val weightDecay = data.readFloat()
        val initialValidationLoss = data.readDouble()
        val finalValidationLoss = data.readDouble()
        val heldOutTestLoss = data.readDouble()
        val totalTrainingSteps = data.readLong()
        val vocabularySize = data.readInt()
        val entryCount = data.readInt()
        require(entryCount in 3..MAX_ENTRIES) { "Quantidade de fontes no manifesto inválida." }

        val entries = List(entryCount) {
            KellvynTrainingManifestEntry(
                id = readString(data, MAX_ID_BYTES),
                split = readEnum(data, KellvynTrainingSplit.entries),
                permission = readEnum(data, KellvynTrainingPermission.entries),
                licenseId = readString(data, MAX_LICENSE_BYTES),
                sourceReference = readString(data, MAX_REFERENCE_BYTES),
                rightsEvidence = readString(data, MAX_EVIDENCE_BYTES),
                contentSha256 = readString(data, HASH_HEX_LENGTH)
            )
        }
        require(data.available() == 0) { "O manifesto contém dados extras." }

        return KellvynTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = maxMerges,
            contextLength = contextLength,
            embeddingSize = embeddingSize,
            seed = seed,
            epochsPerDocument = epochsPerDocument,
            learningRate = learningRate,
            weightDecay = weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = totalTrainingSteps,
            vocabularySize = vocabularySize
        )
    }

    private fun validateManifest(manifest: KellvynTrainingManifest) {
        require(manifest.entries.size in 3..MAX_ENTRIES)
        require(manifest.entries.map { it.id }.distinct().size == manifest.entries.size)
        require(manifest.entries.map { it.contentSha256 }.distinct().size == manifest.entries.size)
        require(manifest.entries.all {
            it.id.isNotBlank() &&
                it.licenseId.isNotBlank() &&
                it.sourceReference.isNotBlank() &&
                it.rightsEvidence.isNotBlank() &&
                it.contentSha256.matches(Regex("[a-f0-9]{$HASH_HEX_LENGTH}"))
        })
        require(KellvynTrainingSplit.entries.all { split ->
            manifest.entries.any { it.split == split }
        }) { "O manifesto precisa conter treino, validação e teste." }
        require(manifest.learningRate.isFinite() && manifest.learningRate in 0.000001f..0.05f)
        require(manifest.weightDecay.isFinite() && manifest.weightDecay in 0.0f..0.1f)
        require(manifest.epochsPerDocument in 1..10_000)
        require(manifest.maxMerges in 0..4096)
        require(manifest.contextLength in 1..256)
        require(manifest.embeddingSize in 4..128)
    }

    private fun writeString(output: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..maxBytes)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val length = input.readInt()
        require(length in 1..maxBytes)
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private inline fun <reified T : Enum<T>> readEnum(
        input: DataInputStream,
        values: List<T>
    ): T {
        val index = input.readInt()
        require(index in values.indices)
        return values[index]
    }

    private fun readBlob(input: DataInputStream, maxBytes: Int, label: String): ByteArray {
        val length = input.readInt()
        require(length in 1..maxBytes) { "Tamanho do componente $label inválido." }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return bytes
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                require(total < MAX_BUNDLE_BYTES)
                output.write(single)
                total++
            } else {
                require(total <= MAX_BUNDLE_BYTES - count) {
                    "O pacote neural excedeu o limite de leitura."
                }
                output.write(buffer, 0, count)
                total += count
            }
        }
        return output.toByteArray()
    }
}
