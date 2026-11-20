package com.kellvyn.assistant.neural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Versioned KN-004 bundle containing its tokenizer, multi-block weights/optimizer state
 * and provenance/quality manifest. KN-003 bundle readers are intentionally untouched.
 */
internal object KellvynMultiBlockCheckpointBundle {
    private const val MAGIC = 0x4B564D34 // "KVM4"
    private const val VERSION = 1
    private const val CHECKSUM_BYTES = 32
    private const val MAX_BUNDLE_BYTES = 136 * 1024 * 1024
    private const val MAX_MODEL_BYTES = 128 * 1024 * 1024
    private const val MAX_TOKENIZER_BYTES = 64 * 1024
    private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRIES = 1_000
    private const val MAX_ID_BYTES = 80
    private const val MAX_LICENSE_BYTES = 120
    private const val MAX_REFERENCE_BYTES = 500
    private const val MAX_EVIDENCE_BYTES = 500
    private const val HASH_HEX_LENGTH = 64

    fun writeCheckpoint(result: KellvynMultiBlockTrainingResult, output: OutputStream) {
        validateBundleContract(result)
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
            writeBlob(data, tokenizerBytes)
            writeBlob(data, modelBytes)
            writeBlob(data, manifestBytes)
        }
        val payload = payloadBuffer.toByteArray()
        require(payload.size + CHECKSUM_BYTES <= MAX_BUNDLE_BYTES)
        output.write(payload)
        output.write(MessageDigest.getInstance("SHA-256").digest(payload))
        output.flush()
    }

    fun readCheckpoint(input: InputStream): KellvynRestoredMultiBlockBundle =
        readCheckpoint(input, inferenceOnly = false)

    /**
     * Restores the same tokenizer, weights and provenance while omitting AdamW moments.
     * Use this path for the live chat runtime to avoid keeping training-only arrays in RAM.
     */
    fun readInferenceCheckpoint(input: InputStream): KellvynRestoredMultiBlockBundle =
        readCheckpoint(input, inferenceOnly = true)

    private fun readCheckpoint(
        input: InputStream,
        inferenceOnly: Boolean
    ): KellvynRestoredMultiBlockBundle {
        val bytes = readBounded(input)
        require(bytes.size in (CHECKSUM_BYTES + 24)..MAX_BUNDLE_BYTES) {
            "Pacote KN-004 vazio, truncado ou acima do limite."
        }
        val payloadSize = bytes.size - CHECKSUM_BYTES
        val payload = bytes.copyOfRange(0, payloadSize)
        val storedDigest = bytes.copyOfRange(payloadSize, bytes.size)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        require(MessageDigest.isEqual(storedDigest, digest)) {
            "A integridade do pacote KN-004 não confere."
        }

        val data = DataInputStream(ByteArrayInputStream(payload))
        require(data.readInt() == MAGIC) { "O pacote não pertence ao KN-004." }
        require(data.readInt() == VERSION) { "Versão do pacote KN-004 não suportada." }
        val tokenizerBytes = readBlob(data, MAX_TOKENIZER_BYTES, "tokenizer")
        val modelBytes = readBlob(data, MAX_MODEL_BYTES, "modelo")
        val manifestBytes = readBlob(data, MAX_MANIFEST_BYTES, "manifesto")
        require(data.available() == 0) { "O pacote KN-004 contém campos extras." }

        val tokenizer = KellvynBpeTokenizer.readCheckpoint(ByteArrayInputStream(tokenizerBytes))
        val model = if (inferenceOnly) {
            KellvynMultiBlockTransformer.readInferenceCheckpoint(ByteArrayInputStream(modelBytes))
        } else {
            KellvynMultiBlockTransformer.readCheckpoint(ByteArrayInputStream(modelBytes))
        }
        val manifest = decodeManifest(manifestBytes)
        val restored = KellvynRestoredMultiBlockBundle(tokenizer, model, manifest)
        validateBundleContract(
            KellvynMultiBlockTrainingResult(
                restored.tokenizer, restored.model, restored.manifest
            )
        )
        return restored
    }

    private fun validateBundleContract(result: KellvynMultiBlockTrainingResult) {
        val config = result.model.config
        val manifest = result.manifest
        require(result.tokenizer.vocabularySize == config.vocabularySize)
        require(manifest.vocabularySize == config.vocabularySize)
        require(manifest.contextLength == config.contextLength)
        require(manifest.embeddingSize == config.embeddingSize)
        require(manifest.headCount == config.headCount)
        require(manifest.feedForwardSize == config.feedForwardSize)
        require(manifest.depth == config.depth)
        require(manifest.seed == config.seed)
        require(manifest.totalTrainingSteps == result.model.trainingSteps)
        require(manifest.maxMerges == result.tokenizer.mergeCount)
        validateManifest(manifest)
    }

    private fun encodeManifest(manifest: KellvynMultiBlockTrainingManifest): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeLong(manifest.createdAtMillis)
            data.writeInt(manifest.maxMerges)
            data.writeInt(manifest.contextLength)
            data.writeInt(manifest.embeddingSize)
            data.writeInt(manifest.headCount)
            data.writeInt(manifest.feedForwardSize)
            data.writeInt(manifest.depth)
            data.writeLong(manifest.seed)
            data.writeInt(manifest.epochsPerExample)
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

    private fun decodeManifest(bytes: ByteArray): KellvynMultiBlockTrainingManifest {
        require(bytes.size in 1..MAX_MANIFEST_BYTES)
        val data = DataInputStream(ByteArrayInputStream(bytes))
        val createdAtMillis = data.readLong()
        val maxMerges = data.readInt()
        val contextLength = data.readInt()
        val embeddingSize = data.readInt()
        val headCount = data.readInt()
        val feedForwardSize = data.readInt()
        val depth = data.readInt()
        val seed = data.readLong()
        val epochs = data.readInt()
        val learningRate = data.readFloat()
        val weightDecay = data.readFloat()
        val initialLoss = data.readDouble()
        val finalLoss = data.readDouble()
        val testLoss = data.readDouble()
        val steps = data.readLong()
        val vocabularySize = data.readInt()
        val count = data.readInt()
        require(count in 1..MAX_ENTRIES)
        val entries = List(count) {
            val id = readString(data, MAX_ID_BYTES)
            val splitOrdinal = data.readInt()
            val permissionOrdinal = data.readInt()
            val split = KellvynTrainingSplit.values().getOrNull(splitOrdinal)
                ?: throw IllegalArgumentException("Partição inválida no manifesto KN-004.")
            val permission = KellvynTrainingPermission.values().getOrNull(permissionOrdinal)
                ?: throw IllegalArgumentException("Permissão inválida no manifesto KN-004.")
            KellvynTrainingManifestEntry(
                id = id,
                split = split,
                permission = permission,
                licenseId = readString(data, MAX_LICENSE_BYTES),
                sourceReference = readString(data, MAX_REFERENCE_BYTES),
                rightsEvidence = readString(data, MAX_EVIDENCE_BYTES),
                contentSha256 = readString(data, HASH_HEX_LENGTH)
            )
        }
        require(data.available() == 0) { "Manifesto KN-004 contém bytes extras." }
        return KellvynMultiBlockTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = maxMerges,
            contextLength = contextLength,
            embeddingSize = embeddingSize,
            headCount = headCount,
            feedForwardSize = feedForwardSize,
            depth = depth,
            seed = seed,
            epochsPerExample = epochs,
            learningRate = learningRate,
            weightDecay = weightDecay,
            initialValidationLoss = initialLoss,
            finalValidationLoss = finalLoss,
            heldOutTestLoss = testLoss,
            totalTrainingSteps = steps,
            vocabularySize = vocabularySize
        )
    }

    private fun validateManifest(manifest: KellvynMultiBlockTrainingManifest) {
        require(manifest.entries.size in 1..MAX_ENTRIES)
        require(manifest.entries.map { it.id }.distinct().size == manifest.entries.size)
        require(manifest.entries.all { entry ->
            entry.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) &&
                entry.licenseId.isNotBlank() && entry.licenseId.length <= MAX_LICENSE_BYTES &&
                entry.sourceReference.isNotBlank() && entry.sourceReference.length <= MAX_REFERENCE_BYTES &&
                entry.rightsEvidence.isNotBlank() && entry.rightsEvidence.length <= MAX_EVIDENCE_BYTES &&
                entry.contentSha256.matches(Regex("[a-f0-9]{64}"))
        })
    }

    private fun writeBlob(data: DataOutputStream, bytes: ByteArray) {
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readBlob(data: DataInputStream, maximum: Int, label: String): ByteArray {
        val size = data.readInt()
        require(size in 1..maximum) { "Bloco $label do pacote KN-004 inválido." }
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return bytes
    }

    private fun writeString(data: DataOutputStream, value: String, maximum: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..maximum)
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readString(data: DataInputStream, maximum: Int): String {
        val size = data.readInt()
        require(size in 1..maximum)
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val value = input.read()
                if (value < 0) break
                require(total < MAX_BUNDLE_BYTES)
                output.write(value)
                total++
            } else {
                require(total <= MAX_BUNDLE_BYTES - count) {
                    "Pacote KN-004 acima do limite."
                }
                output.write(buffer, 0, count)
                total += count
            }
        }
        return output.toByteArray()
    }
}
