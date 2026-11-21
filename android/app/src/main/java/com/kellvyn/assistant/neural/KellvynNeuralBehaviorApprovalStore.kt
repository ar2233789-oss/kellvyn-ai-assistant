package com.kellvyn.assistant.neural

import com.kellvyn.assistant.KellvynTaskCheckpointCipher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Persists a successful held-out behavior gate, encrypted and authenticated.
 *
 * Approval is bound to both the immutable model-weight fingerprint and the fingerprint of
 * every TEST record in the checkpoint manifest. A new checkpoint or changed TEST manifest
 * therefore cannot inherit an earlier approval.
 */
internal class KellvynNeuralBehaviorApprovalStore(
    private val file: File,
    private val cipher: KellvynTaskCheckpointCipher
) {
    private val lock = Any()

    fun save(
        modelFingerprint: String,
        testSetFingerprint: String,
        report: KellvynNeuralBehaviorBenchmark.Report
    ) = synchronized(lock) {
        validateFingerprint(modelFingerprint)
        validateFingerprint(testSetFingerprint)
        require(report.eligibleForIntegrationReview)
        require(report.modelFingerprint == modelFingerprint)
        require(report.sampleCount >= KellvynNeuralBehaviorBenchmark.MINIMUM_EXAMPLES)
        require(report.exactMatchRate >= KellvynNeuralBehaviorBenchmark.MINIMUM_EXACT_MATCH_RATE)
        require(report.meanTokenF1 >= KellvynNeuralBehaviorBenchmark.MINIMUM_TOKEN_F1)
        require(report.exactMatchRate.isFinite() && report.exactMatchRate <= 1.0)
        require(report.meanTokenF1.isFinite() && report.meanTokenF1 <= 1.0)

        val associatedData = associatedData(modelFingerprint, testSetFingerprint)
        val encrypted = cipher.encrypt(encodeReport(report), associatedData)
        require(encrypted.size in 1..MAX_ENCRYPTED_BYTES)

        val parent = requireNotNull(file.parentFile)
        if (!parent.exists()) {
            check(parent.mkdirs()) { "Não foi possível criar a pasta privada da aprovação comportamental." }
        }
        check(parent.isDirectory)
        check(file.canonicalFile.parentFile == parent.canonicalFile)

        val temporary = File(parent, file.name + ".tmp")
        val backup = File(parent, file.name + ".bak")
        try {
            FileOutputStream(temporary).use { raw ->
                DataOutputStream(raw).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    output.writeUTF(modelFingerprint)
                    output.writeUTF(testSetFingerprint)
                    output.writeInt(encrypted.size)
                    output.write(encrypted)
                    output.flush()
                    raw.fd.sync()
                }
            }
            require(temporary.isFile && temporary.length() in 1..MAX_FILE_BYTES.toLong())

            if (backup.exists() && !backup.delete()) {
                error("Não foi possível preparar o backup da aprovação anterior.")
            }
            if (file.exists() && !file.renameTo(backup)) {
                error("Não foi possível preservar a aprovação anterior.")
            }
            if (!temporary.renameTo(file)) {
                if (backup.exists()) backup.renameTo(file)
                error("Não foi possível confirmar a aprovação comportamental.")
            }
            if (backup.exists()) backup.delete()
        } finally {
            temporary.delete()
        }
    }

    fun load(
        modelFingerprint: String,
        testSetFingerprint: String
    ): KellvynNeuralBehaviorBenchmark.Report? = synchronized(lock) {
        validateFingerprint(modelFingerprint)
        validateFingerprint(testSetFingerprint)
        if (!file.isFile) return@synchronized null
        require(file.length() in MIN_FILE_BYTES.toLong()..MAX_FILE_BYTES.toLong()) {
            "O registro de aprovação comportamental está vazio ou acima do limite."
        }

        val bytes = file.readBytes()
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC) { "Formato de aprovação comportamental desconhecido." }
        require(input.readInt() == VERSION) { "Versão de aprovação comportamental não suportada." }
        val storedModelFingerprint = input.readUTF()
        val storedTestFingerprint = input.readUTF()
        val encryptedLength = input.readInt()
        require(encryptedLength in 1..MAX_ENCRYPTED_BYTES && encryptedLength == input.available()) {
            "Tamanho do registro de aprovação comportamental inválido."
        }
        val encrypted = ByteArray(encryptedLength)
        input.readFully(encrypted)
        require(input.available() == 0)

        if (storedModelFingerprint != modelFingerprint ||
            storedTestFingerprint != testSetFingerprint
        ) {
            return@synchronized null
        }

        val plain = cipher.decrypt(
            encrypted,
            associatedData(storedModelFingerprint, storedTestFingerprint)
        )
        decodeReport(storedModelFingerprint, plain)
    }

    fun delete() = synchronized(lock) {
        val parent = requireNotNull(file.parentFile)
        listOf(file, File(parent, file.name + ".tmp"), File(parent, file.name + ".bak"))
            .forEach { candidate ->
                if (candidate.exists() && !candidate.delete()) {
                    error("Não foi possível remover um registro antigo de aprovação comportamental.")
                }
            }
    }

    private fun encodeReport(
        report: KellvynNeuralBehaviorBenchmark.Report
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(PAYLOAD_VERSION)
            output.writeInt(report.sampleCount)
            output.writeDouble(report.exactMatchRate)
            output.writeDouble(report.meanTokenF1)
        }
    }.toByteArray()

    private fun decodeReport(
        modelFingerprint: String,
        bytes: ByteArray
    ): KellvynNeuralBehaviorBenchmark.Report {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == PAYLOAD_VERSION) {
            "Versão do relatório de aprovação comportamental inválida."
        }
        val sampleCount = input.readInt()
        val exactMatchRate = input.readDouble()
        val meanTokenF1 = input.readDouble()
        require(input.available() == 0)
        require(sampleCount >= KellvynNeuralBehaviorBenchmark.MINIMUM_EXAMPLES)
        require(exactMatchRate.isFinite() &&
            exactMatchRate in KellvynNeuralBehaviorBenchmark.MINIMUM_EXACT_MATCH_RATE..1.0
        )
        require(meanTokenF1.isFinite() &&
            meanTokenF1 in KellvynNeuralBehaviorBenchmark.MINIMUM_TOKEN_F1..1.0
        )
        return KellvynNeuralBehaviorBenchmark.Report(
            eligibleForIntegrationReview = true,
            modelFingerprint = modelFingerprint,
            sampleCount = sampleCount,
            exactMatchRate = exactMatchRate,
            meanTokenF1 = meanTokenF1,
            reasons = emptyList()
        )
    }

    private fun associatedData(
        modelFingerprint: String,
        testSetFingerprint: String
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeUTF(modelFingerprint)
            output.writeUTF(testSetFingerprint)
        }
    }.toByteArray()

    private fun validateFingerprint(value: String) {
        require(value.matches(Regex("[a-f0-9]{64}"))) {
            "Fingerprint da aprovação comportamental inválido."
        }
    }

    private companion object {
        const val MAGIC = 0x4B4E4241 // "KNBA"
        const val VERSION = 1
        const val PAYLOAD_VERSION = 1
        const val MAX_ENCRYPTED_BYTES = 4_096
        const val MAX_FILE_BYTES = 8_192
        const val MIN_FILE_BYTES = 4 + 4 + 2 + 64 + 2 + 64 + 4 + 1
    }
}
