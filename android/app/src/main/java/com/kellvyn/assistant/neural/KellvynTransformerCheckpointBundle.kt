package com.kellvyn.assistant.neural

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Integrity-checked KN-003 bundle: own tokenizer, Transformer weights/optimizer and
 * non-text provenance/metrics manifest. It is versioned separately from the KN-002 bundle.
 */
internal object KellvynTransformerCheckpointBundle {
    private const val MAGIC = 0x4B564E33 // "KVN3"
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

    fun writeCheckpoint(result: KellvynTransformerTrainingResult, output: OutputStream) {
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

    fun readCheckpoint(input: InputStream): KellvynRestoredTransformerBundle {
        val bytes = readBounded(input)
        require(bytes.size in (CHECKSUM_BYTES + 24)..MAX_BUNDLE_BYTES) {
            "Pacote KN-003 vazio, truncado ou acima do limite."
        }
        val payloadSize = bytes.size - CHECKSUM_BYTES
        val payload = bytes.copyOfRange(0, payloadSize)
        val storedDigest = bytes.copyOfRange(payloadSize, bytes.size)
        val calculatedDigest = MessageDigest.getInstance("SHA-256").digest(payload)
        require(MessageDigest.isEqual(storedDigest, calculatedDigest)) {
            "A integridade do pacote KN-003 não confere."
        }

        val data = DataInputStream(ByteArrayInputStream(payload))
        require(data.readInt() == MAGIC) { "O pacote não pertence à Kellvyn KN-003." }
        require(data.readInt() == VERSION) { "Versão do pacote KN-003 não suportada." }
        val tokenizerBytes = readBlob(data, MAX_TOKENIZER_BYTES, "tokenizer")
        val modelBytes = readBlob(data, MAX_MODEL_BYTES, "modelo")
        val manifestBytes = readBlob(data, MAX_MANIFEST_BYTES, "manifesto")
        require(data.available() == 0) { "O pacote KN-003 contém campos extras." }

        val tokenizer = KellvynBpeTokenizer.readCheckpoint(ByteArrayInputStream(tokenizerBytes))
        val model = KellvynTransformerLanguageModel.readCheckpoint(ByteArrayInputStream(modelBytes))
        val manifest = decodeManifest(manifestBytes)
        val restored = KellvynRestoredTransformerBundle(tokenizer, model, manifest)
        validateBundleContract(
            KellvynTransformerTrainingResult(
                tokenizer = restored.tokenizer,
                model = restored.model,
                manifest = restored.manifest
            )
        )
        return restored
    }

    private fun validateBundleContract(result: KellvynTransformerTrainingResult) {
        require(result.tokenizer.vocabularySize == result.model.config.vocabularySize)
        val manifest = result.manifest
        require(manifest.vocabularySize == result.model.config.vocabularySize)
        require(manifest.contextLength == result.model.config.contextLength)
        require(manifest.embeddingSize == result.model.config.embeddingSize)
        require(manifest.headCount == result.model.config.headCount)
        require(manifest.feedForwardSize == result.model.config.feedForwardSize)
        require(manifest.seed == result.model.config.seed)
        require(manifest.totalTrainingSteps == result.model.trainingSteps)
        require(manifest.maxMerges == result.tokenizer.mergeCount)
        validateManifest(manifest)
    }

    private fun encodeManifest(manifest: KellvynTransformerTrainingManifest): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeLong(manifest.createdAtMillis)
            data.writeInt(manifest.maxMerges)
            data.writeInt(manifest.contextLength)
            data.writeInt(manifest.embeddingSize)
            data.writeInt(manifest.headCount)
            data.writeInt(manifest.feedForwardSize)
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

    private fun decodeManifest(bytes: ByteArray): KellvynTransformerTrainingManifest {
        require(bytes.size in 1..MAX_MANIFEST_BYTES)
        val data = DataInputStream(ByteArrayInputStream(bytes))
        val createdAtMillis = data.readLong()
        val maxMerges = data.readInt()
        val contextLength = data.readInt()
        val embeddingSize = data.readInt()
        val headCount = data.readInt()
        val feedForwardSize = data.readInt()
        val seed = data.readLong()
        val epochsPerExample = data.readInt()
        val learningRate = data.readFloat()
        val weightDecay = data.readFloat()
        val initialValidationLoss = data.readDouble()
        val finalValidationLoss = data.readDouble()
        val heldOutTestLoss = data.readDouble()
        val totalTrainingSteps = data.readLong()
        val vocabularySize = data.readInt()
        val entryCount = data.readInt()
        require(entryCount in 3..MAX_ENTRIES)

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
        require(data.available() == 0) { "O manifesto KN-003 contém dados extras." }
        return KellvynTransformerTrainingManifest(
            createdAtMillis = createdAtMillis,
            entries = entries,
            maxMerges = maxMerges,
            contextLength = contextLength,
            embeddingSize = embeddingSize,
            headCount = headCount,
            feedForwardSize = feedForwardSize,
            seed = seed,
            epochsPerExample = epochsPerExample,
            learningRate = learningRate,
            weightDecay = weightDecay,
            initialValidationLoss = initialValidationLoss,
            finalValidationLoss = finalValidationLoss,
            heldOutTestLoss = heldOutTestLoss,
            totalTrainingSteps = totalTrainingSteps,
            vocabularySize = vocabularySize
        ).also(::validateManifest)
    }

    private fun validateManifest(manifest: KellvynTransformerTrainingManifest) {
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
        })
        require(manifest.learningRate.isFinite() && manifest.learningRate in 0.000001f..0.05f)
        require(manifest.weightDecay.isFinite() && manifest.weightDecay in 0.0f..0.1f)
        require(manifest.epochsPerExample in 1..10_000)
        require(manifest.maxMerges in 0..4096)
        val config = KellvynTransformerLanguageModel.Config(
            vocabularySize = manifest.vocabularySize,
            contextLength = manifest.contextLength,
            embeddingSize = manifest.embeddingSize,
            headCount = manifest.headCount,
            feedForwardSize = manifest.feedForwardSize,
            seed = manifest.seed
        )
        require(config.embeddingSize / config.headCount % 2 == 0)
        require(manifest.totalTrainingSteps > 0L)
    }

    private fun writeString(output: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..maxBytes)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val length = input.readInt()
        require(length in 1..maxBytes)
        val bytes = ByteArray(length)
        input.readFully(bytes)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("O manifesto KN-003 contém UTF-8 inválido.")
        }
    }

    private fun writeBlob(output: DataOutputStream, bytes: ByteArray) {
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readBlob(input: DataInputStream, maxBytes: Int, label: String): ByteArray {
        val size = input.readInt()
        require(size in 1..maxBytes) { "Tamanho do componente $label inválido." }
        val result = ByteArray(size)
        input.readFully(result)
        return result
    }

    private inline fun <reified T : Enum<T>> readEnum(
        input: DataInputStream,
        values: List<T>
    ): T {
        val index = input.readInt()
        require(index in values.indices)
        return values[index]
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
                require(total <= MAX_BUNDLE_BYTES - count)
                output.write(buffer, 0, count)
                total += count
            }
        }
        return output.toByteArray()
    }
}
