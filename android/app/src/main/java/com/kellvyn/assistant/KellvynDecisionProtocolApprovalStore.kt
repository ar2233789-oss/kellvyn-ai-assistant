package com.kellvyn.assistant

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * Authenticated approval for the actual KELLVYN/1 decision benchmark.
 *
 * The record is bound to the exact model fingerprint, exact held-out TEST fingerprint
 * and sample count. It stores decision-class accuracy, not text-token metrics.
 */
internal class KellvynDecisionProtocolApprovalStore(
    private val file: File,
    private val cipher: KellvynTaskCheckpointCipher
) {
    data class Approval(
        val modelFingerprint: String,
        val testSetFingerprint: String,
        val sampleCount: Int,
        val overallDecisionMatchRate: Double,
        val finalDecisionMatchRate: Double,
        val toolDecisionMatchRate: Double,
        val planDecisionMatchRate: Double,
        val approvedAtMillis: Long
    )

    private val lock = Any()

    fun save(
        report: KellvynDecisionProtocolBehaviorBenchmark.Report,
        approvedAtMillis: Long = System.currentTimeMillis()
    ): Approval = synchronized(lock) {
        validateReport(report)
        require(approvedAtMillis > 0L)
        val approval = Approval(
            modelFingerprint = report.modelFingerprint,
            testSetFingerprint = report.testSetFingerprint,
            sampleCount = report.sampleCount,
            overallDecisionMatchRate = report.decisionMatchRate,
            finalDecisionMatchRate = rate(report.matched.finalDecision, report.expected.finalDecision),
            toolDecisionMatchRate = rate(report.matched.callTool, report.expected.callTool),
            planDecisionMatchRate = rate(report.matched.plan, report.expected.plan),
            approvedAtMillis = approvedAtMillis
        )
        val aad = associatedData(
            approval.modelFingerprint,
            approval.testSetFingerprint,
            approval.sampleCount
        )
        val encrypted = cipher.encrypt(encodeApproval(approval), aad)
        require(encrypted.size in 1..MAX_ENCRYPTED_BYTES)

        val parent = requireNotNull(file.parentFile)
        if (!parent.exists()) {
            check(parent.mkdirs()) { "Não foi possível criar a pasta privada da aprovação KELLVYN/1." }
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
                    output.writeUTF(approval.modelFingerprint)
                    output.writeUTF(approval.testSetFingerprint)
                    output.writeInt(approval.sampleCount)
                    output.writeInt(encrypted.size)
                    output.write(encrypted)
                    output.flush()
                    raw.fd.sync()
                }
            }
            require(temporary.isFile && temporary.length() in 1..MAX_FILE_BYTES.toLong())
            if (backup.exists() && !backup.delete()) {
                error("Não foi possível preparar o backup da aprovação KELLVYN/1.")
            }
            if (file.exists() && !file.renameTo(backup)) {
                error("Não foi possível preservar a aprovação KELLVYN/1 anterior.")
            }
            if (!temporary.renameTo(file)) {
                if (backup.exists()) backup.renameTo(file)
                error("Não foi possível confirmar a aprovação KELLVYN/1.")
            }
            if (backup.exists()) backup.delete()
        } finally {
            temporary.delete()
        }
        approval
    }

    fun load(
        modelFingerprint: String,
        testSetFingerprint: String,
        expectedSampleCount: Int
    ): Approval? = synchronized(lock) {
        validateFingerprint(modelFingerprint)
        validateFingerprint(testSetFingerprint)
        require(expectedSampleCount >= KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_EXAMPLES)
        if (!file.isFile) return@synchronized null
        require(file.length() in MIN_FILE_BYTES.toLong()..MAX_FILE_BYTES.toLong()) {
            "O registro de aprovação KELLVYN/1 está vazio ou excede o limite."
        }

        val input = DataInputStream(ByteArrayInputStream(file.readBytes()))
        require(input.readInt() == MAGIC) { "Formato de aprovação KELLVYN/1 desconhecido." }
        require(input.readInt() == VERSION) { "Versão de aprovação KELLVYN/1 não suportada." }
        val storedModelFingerprint = input.readUTF()
        val storedTestFingerprint = input.readUTF()
        val storedSampleCount = input.readInt()
        val encryptedLength = input.readInt()
        require(encryptedLength in 1..MAX_ENCRYPTED_BYTES && encryptedLength == input.available()) {
            "Tamanho do registro de aprovação KELLVYN/1 inválido."
        }
        val encrypted = ByteArray(encryptedLength)
        input.readFully(encrypted)
        require(input.available() == 0)

        if (storedModelFingerprint != modelFingerprint ||
            storedTestFingerprint != testSetFingerprint ||
            storedSampleCount != expectedSampleCount
        ) {
            return@synchronized null
        }

        val plain = cipher.decrypt(
            encrypted,
            associatedData(storedModelFingerprint, storedTestFingerprint, storedSampleCount)
        )
        decodeApproval(
            modelFingerprint = storedModelFingerprint,
            testSetFingerprint = storedTestFingerprint,
            sampleCount = storedSampleCount,
            bytes = plain
        )
    }

    fun delete() = synchronized(lock) {
        val parent = requireNotNull(file.parentFile)
        listOf(file, File(parent, file.name + ".tmp"), File(parent, file.name + ".bak"))
            .forEach { candidate ->
                if (candidate.exists() && !candidate.delete()) {
                    error("Não foi possível remover uma aprovação KELLVYN/1 antiga.")
                }
            }
    }

    private fun validateReport(report: KellvynDecisionProtocolBehaviorBenchmark.Report) {
        validateFingerprint(report.modelFingerprint)
        validateFingerprint(report.testSetFingerprint)
        require(report.eligibleForConversationReview)
        require(report.sampleCount >= KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_EXAMPLES)
        require(report.expected.finalDecision > 0)
        require(report.expected.callTool > 0)
        require(report.expected.plan > 0)
        require(report.matched.finalDecision in 0..report.expected.finalDecision)
        require(report.matched.callTool in 0..report.expected.callTool)
        require(report.matched.plan in 0..report.expected.plan)
        require(report.decisionMatchRate.isFinite() &&
            report.decisionMatchRate >= KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_DECISION_MATCH_RATE &&
            report.decisionMatchRate <= 1.0
        )
        require(report.matched.finalDecision >= minimumMatches(report.expected.finalDecision))
        require(report.matched.callTool >= minimumMatches(report.expected.callTool))
        require(report.matched.plan >= minimumMatches(report.expected.plan))
        val totalMatched = report.matched.finalDecision + report.matched.callTool + report.matched.plan
        val totalExpected = report.expected.finalDecision + report.expected.callTool + report.expected.plan
        require(totalExpected == report.sampleCount && totalMatched.toDouble() / totalExpected >=
            KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_DECISION_MATCH_RATE
        )
    }

    private fun encodeApproval(approval: Approval): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PAYLOAD_VERSION)
                output.writeDouble(approval.overallDecisionMatchRate)
                output.writeDouble(approval.finalDecisionMatchRate)
                output.writeDouble(approval.toolDecisionMatchRate)
                output.writeDouble(approval.planDecisionMatchRate)
                output.writeLong(approval.approvedAtMillis)
            }
        }.toByteArray()

    private fun decodeApproval(
        modelFingerprint: String,
        testSetFingerprint: String,
        sampleCount: Int,
        bytes: ByteArray
    ): Approval {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == PAYLOAD_VERSION) { "Versão do payload de aprovação KELLVYN/1 inválida." }
        val overall = input.readDouble()
        val finalRate = input.readDouble()
        val toolRate = input.readDouble()
        val planRate = input.readDouble()
        val approvedAt = input.readLong()
        require(input.available() == 0)
        require(approvedAt > 0L)
        require(overall.isFinite() &&
            overall >= KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_DECISION_MATCH_RATE &&
            overall <= 1.0
        )
        require(listOf(finalRate, toolRate, planRate).all { it.isFinite() && it in 0.80..1.0 })
        return Approval(
            modelFingerprint = modelFingerprint,
            testSetFingerprint = testSetFingerprint,
            sampleCount = sampleCount,
            overallDecisionMatchRate = overall,
            finalDecisionMatchRate = finalRate,
            toolDecisionMatchRate = toolRate,
            planDecisionMatchRate = planRate,
            approvedAtMillis = approvedAt
        )
    }

    private fun associatedData(
        modelFingerprint: String,
        testSetFingerprint: String,
        sampleCount: Int
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeUTF(modelFingerprint)
            output.writeUTF(testSetFingerprint)
            output.writeInt(sampleCount)
        }
    }.toByteArray()

    private fun validateFingerprint(value: String) {
        require(value.matches(Regex("[a-f0-9]{64}"))) {
            "Fingerprint da aprovação KELLVYN/1 inválido."
        }
    }

    private fun rate(matches: Int, total: Int): Double =
        if (total <= 0) 0.0 else matches.toDouble() / total.toDouble()

    private fun minimumMatches(expected: Int): Int =
        ceil(expected * MINIMUM_PER_CLASS_RATE).toInt()

    private companion object {
        const val MAGIC = 0x4B4E4441 // "KNDA"
        const val VERSION = 1
        const val PAYLOAD_VERSION = 1
        const val MAX_ENCRYPTED_BYTES = 4_096
        const val MAX_FILE_BYTES = 8_192
        const val MIN_FILE_BYTES = 4 + 4 + 2 + 64 + 2 + 64 + 4 + 4 + 1
        const val MINIMUM_PER_CLASS_RATE = 0.80
    }
}
