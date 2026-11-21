package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynInstructionTrainingExample
import com.kellvyn.assistant.neural.KellvynTrainingSplit
import com.kellvyn.assistant.neural.KellvynTrainingManifestEntry
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Held-out verification for the exact KELLVYN/1 decisions used by the live brain.
 * It runs only against TEST examples whose prompts were prepared by the canonical formatter.
 */
internal object KellvynDecisionProtocolBehaviorBenchmark {
    const val MINIMUM_EXAMPLES = 20
    const val MINIMUM_DECISION_MATCH_RATE = 0.80

    data class Counts(val final: Int, val callTool: Int, val plan: Int)

    data class Report(
        val eligibleForConversationReview: Boolean,
        val modelFingerprint: String,
        val testSetFingerprint: String,
        val sampleCount: Int,
        val decisionMatchRate: Double,
        val matched: Counts,
        val expected: Counts,
        val reasons: List<String>
    ) {
        fun asEncryptedApprovalReport(): com.kellvyn.assistant.neural.KellvynNeuralBehaviorBenchmark.Report =
            com.kellvyn.assistant.neural.KellvynNeuralBehaviorBenchmark.Report(
                eligibleForIntegrationReview = eligibleForConversationReview,
                modelFingerprint = modelFingerprint,
                sampleCount = sampleCount,
                exactMatchRate = decisionMatchRate,
                meanTokenF1 = decisionMatchRate,
                reasons = reasons
            )
    }

    suspend fun evaluate(
        modelFingerprint: String,
        examples: List<KellvynInstructionTrainingExample>,
        generate: suspend (KellvynInstructionTrainingExample) -> KellvynModelDecision
    ): Report {
        require(modelFingerprint.matches(Regex("[a-f0-9]{64}"))) {
            "Fingerprint do modelo inválido para o benchmark de decisões."
        }
        require(examples.all { it.split == KellvynTrainingSplit.TEST }) {
            "O benchmark de decisões aceita somente exemplos TEST."
        }
        require(examples.map { it.id }.distinct().size == examples.size) {
            "O benchmark de decisões contém IDs duplicados."
        }
        require(examples.map { normalize(it.prompt) }.distinct().size == examples.size) {
            "O benchmark de decisões contém prompts repetidos."
        }
        val fingerprint = testSetFingerprint(examples)
        val reasons = mutableListOf<String>()
        if (examples.size < MINIMUM_EXAMPLES) {
            reasons += "O benchmark exige pelo menos $MINIMUM_EXAMPLES exemplos TEST."
        }
        if (examples.any {
                it.prompt.isBlank() || it.completion.isBlank() ||
                    it.provenance.licenseId.isBlank() ||
                    it.provenance.sourceReference.isBlank() ||
                    it.provenance.rightsEvidence.isBlank()
            }) {
            reasons += "Há exemplos TEST vazios ou sem proveniência."
        }
        if (reasons.isNotEmpty()) {
            return Report(false, modelFingerprint, fingerprint, examples.size, 0.0,
                Counts(0, 0, 0), expectedCounts(examples), reasons)
        }

        var matches = 0
        var finalMatches = 0
        var toolMatches = 0
        var planMatches = 0
        val expected = expectedCounts(examples)
        for (example in examples) {
            val expectedDecision = when (
                val parsed = KellvynDecisionProtocol.parse(
                    example.completion,
                    nowMillis = PROTOCOL_VALIDATION_TIME_MILLIS
                )
            ) {
                is KellvynDecisionProtocol.ParseResult.Accepted -> parsed.decision
                is KellvynDecisionProtocol.ParseResult.Rejected -> {
                    reasons += "${example.id}: alvo TEST deixou de ser uma decisão KELLVYN/1 válida."
                    continue
                }
            }
            val actualDecision = try {
                generate(example)
            } catch (error: Exception) {
                reasons += "${example.id}: inferência de decisão falhou (${safeMessage(error)})."
                continue
            }
            if (equivalent(expectedDecision, actualDecision)) {
                matches++
                when (expectedDecision) {
                    is KellvynModelDecision.Final -> finalMatches++
                    is KellvynModelDecision.CallTool -> toolMatches++
                    is KellvynModelDecision.Plan -> planMatches++
                    is KellvynModelDecision.Delegate -> Unit
                }
            }
        }

        val rate = matches.toDouble() / examples.size.toDouble()
        if (expected.final == 0 || expected.callTool == 0 || expected.plan == 0) {
            reasons += "O conjunto TEST precisa cobrir FINAL, CALL_TOOL e PLAN."
        }
        if (finalMatches < minimumClassMatches(expected.final) ||
            toolMatches < minimumClassMatches(expected.callTool) ||
            planMatches < minimumClassMatches(expected.plan)
        ) {
            reasons += "Uma ou mais classes de decisão não atingiram a cobertura mínima."
        }
        if (rate < MINIMUM_DECISION_MATCH_RATE) {
            reasons += "A correspondência exata de decisões ficou abaixo de $MINIMUM_DECISION_MATCH_RATE."
        }
        val eligible = reasons.isEmpty()
        return Report(
            eligibleForConversationReview = eligible,
            modelFingerprint = modelFingerprint,
            testSetFingerprint = fingerprint,
            sampleCount = examples.size,
            decisionMatchRate = rate,
            matched = Counts(finalMatches, toolMatches, planMatches),
            expected = expected,
            reasons = reasons
        )
    }

    fun testSetFingerprint(examples: List<KellvynInstructionTrainingExample>): String {
        require(examples.all { it.split == KellvynTrainingSplit.TEST })
        val records = examples.map { example ->
            TestRecord(
                id = example.id,
                split = example.split.name,
                contentSha256 = sha256(example.prompt + "\u0000" + example.completion),
                permission = example.provenance.permission.name,
                licenseId = example.provenance.licenseId,
                sourceReference = example.provenance.sourceReference,
                rightsEvidence = example.provenance.rightsEvidence
            )
        }
        return fingerprintRecords(records)
    }

    fun testSetFingerprintFromManifest(entries: List<KellvynTrainingManifestEntry>): String {
        val testEntries = entries.filter { it.split == KellvynTrainingSplit.TEST }
        require(testEntries.isNotEmpty())
        return fingerprintRecords(testEntries.map { entry ->
            TestRecord(
                id = entry.id,
                split = entry.split.name,
                contentSha256 = entry.contentSha256,
                permission = entry.permission.name,
                licenseId = entry.licenseId,
                sourceReference = entry.sourceReference,
                rightsEvidence = entry.rightsEvidence
            )
        })
    }

    fun matchesTestManifest(
        examples: List<KellvynInstructionTrainingExample>,
        manifestEntries: List<KellvynTrainingManifestEntry>
    ): Boolean {
        if (examples.any { it.split != KellvynTrainingSplit.TEST }) return false
        val expected = manifestEntries.filter { it.split == KellvynTrainingSplit.TEST }
            .associateBy { it.id }
        if (expected.size != examples.size || examples.map { it.id }.distinct().size != examples.size) {
            return false
        }
        return examples.all { example ->
            val entry = expected[example.id] ?: return@all false
            entry.contentSha256 == sha256(example.prompt + "\u0000" + example.completion) &&
                entry.permission == example.provenance.permission &&
                entry.licenseId == example.provenance.licenseId &&
                entry.sourceReference == example.provenance.sourceReference &&
                entry.rightsEvidence == example.provenance.rightsEvidence
        }
    }

    private data class TestRecord(
        val id: String,
        val split: String,
        val contentSha256: String,
        val permission: String,
        val licenseId: String,
        val sourceReference: String,
        val rightsEvidence: String
    )

    private fun fingerprintRecords(records: List<TestRecord>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeUTF("KELLVYN-DECISION-TEST-v1")
            records.sortedBy { it.id }.forEach { record ->
                output.writeUTF(record.id)
                output.writeUTF(record.split)
                output.writeUTF(record.contentSha256)
                output.writeUTF(record.permission)
                output.writeUTF(record.licenseId)
                output.writeUTF(record.sourceReference)
                output.writeUTF(record.rightsEvidence)
            }
        }
        return digest.digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun expectedCounts(examples: List<KellvynInstructionTrainingExample>): Counts {
        var finals = 0
        var calls = 0
        var plans = 0
        examples.forEach { example ->
            val parsed = KellvynDecisionProtocol.parse(
                example.completion,
                nowMillis = PROTOCOL_VALIDATION_TIME_MILLIS
            )
            val decision = (parsed as? KellvynDecisionProtocol.ParseResult.Accepted)?.decision
            when (decision) {
                is KellvynModelDecision.Final -> finals++
                is KellvynModelDecision.CallTool -> calls++
                is KellvynModelDecision.Plan -> plans++
                else -> Unit
            }
        }
        return Counts(finals, calls, plans)
    }

    private fun equivalent(
        expected: KellvynModelDecision,
        actual: KellvynModelDecision
    ): Boolean = when {
        expected is KellvynModelDecision.Final && actual is KellvynModelDecision.Final ->
            normalize(expected.text) == normalize(actual.text)
        expected is KellvynModelDecision.CallTool && actual is KellvynModelDecision.CallTool ->
            expected.name == actual.name && expected.arguments.toSortedMap() == actual.arguments.toSortedMap()
        expected is KellvynModelDecision.Plan && actual is KellvynModelDecision.Plan ->
            samePlan(expected.plan, actual.plan)
        else -> false
    }

    private fun samePlan(expected: KellvynTaskPlan, actual: KellvynTaskPlan): Boolean {
        if (expected.id != actual.id || normalize(expected.goal) != normalize(actual.goal) ||
            expected.constraints.map(::normalize).sorted() != actual.constraints.map(::normalize).sorted() ||
            expected.steps.size != actual.steps.size
        ) return false
        val actualById = actual.steps.associateBy { it.id }
        return expected.steps.all { step ->
            val other = actualById[step.id] ?: return@all false
            normalize(step.title) == normalize(other.title) &&
                normalize(step.objective) == normalize(other.objective) &&
                step.dependencies == other.dependencies &&
                step.requiredTools == other.requiredTools &&
                step.risk == other.risk &&
                step.ownerApprovalRequired == other.ownerApprovalRequired &&
                step.preconditions.map(::normalize).toSet() == other.preconditions.map(::normalize).toSet() &&
                step.successCriteria.map(::normalize).toSet() == other.successCriteria.map(::normalize).toSet() &&
                step.recoveryPlan?.let(::normalize) == other.recoveryPlan?.let(::normalize)
        }
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun safeMessage(error: Exception): String =
        (error.message ?: error.javaClass.simpleName)
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(240)

    private fun minimumClassMatches(expected: Int): Int =
        if (expected == 0) 0 else 1

    private const val PROTOCOL_VALIDATION_TIME_MILLIS = 1_800_000_000_000L
}
