package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynInstructionTrainingExample
import com.kellvyn.assistant.neural.KellvynTrainingManifestEntry
import com.kellvyn.assistant.neural.KellvynTrainingPermission
import com.kellvyn.assistant.neural.KellvynTrainingProvenance
import com.kellvyn.assistant.neural.KellvynTrainingSplit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class KellvynDecisionProtocolBehaviorBenchmarkTest {
    @Test
    fun exactDecisionOutputsPassAndFingerprintMatchesInstalledManifest() = runBlocking {
        val examples = samples()
        val report = KellvynDecisionProtocolBehaviorBenchmark.evaluate(
            modelFingerprint = "a".repeat(64),
            examples = examples,
            generate = { parse(it.completion) }
        )

        assertTrue(report.eligibleForConversationReview)
        assertEquals(1.0, report.decisionMatchRate, 0.0001)
        assertEquals(8, report.matched.final)
        assertEquals(8, report.matched.callTool)
        assertEquals(8, report.matched.plan)
        assertEquals(
            report.testSetFingerprint,
            KellvynDecisionProtocolBehaviorBenchmark.testSetFingerprintFromManifest(
                examples.map(::manifestEntry)
            )
        )
        assertTrue(
            KellvynDecisionProtocolBehaviorBenchmark.matchesTestManifest(
                examples,
                examples.map(::manifestEntry)
            )
        )
    }

    @Test
    fun benchmarkRejectsPoorAccuracyEvenWhenOutputHasValidProtocolSyntax() = runBlocking {
        val examples = samples()
        val report = KellvynDecisionProtocolBehaviorBenchmark.evaluate(
            modelFingerprint = "b".repeat(64),
            examples = examples,
            generate = { example ->
                if (example.id in setOf("final-0", "final-1", "final-2", "final-3")) {
                    KellvynModelDecision.Final("resposta incorreta")
                } else {
                    parse(example.completion)
                }
            }
        )

        assertFalse(report.eligibleForConversationReview)
        assertTrue(report.decisionMatchRate < KellvynDecisionProtocolBehaviorBenchmark.MINIMUM_DECISION_MATCH_RATE)
        assertTrue(report.reasons.isNotEmpty())
    }

    private fun samples(): List<KellvynInstructionTrainingExample> =
        (0 until 8).flatMap { index ->
            listOf(
                example("final-$index", finalTarget("Resposta $index.")),
                example("tool-$index", toolTarget()),
                example("plan-$index", planTarget(index))
            )
        }

    private fun parse(completion: String): KellvynModelDecision =
        (KellvynDecisionProtocol.parse(completion, nowMillis = 1_800_000_000_000L)
            as KellvynDecisionProtocol.ParseResult.Accepted).decision

    private fun finalTarget(text: String) =
        "KELLVYN/1\nFINAL\nTEXT\t${KellvynDecisionProtocol.encodeText(text)}\nEND"

    private fun toolTarget() = buildString {
        append("KELLVYN/1\nCALL_TOOL\n")
        append("NAME\t").append(KellvynDecisionProtocol.encodeText("local_lookup")).append("\n")
        append("ARG\t").append(KellvynDecisionProtocol.encodeText("query"))
            .append("\t").append(KellvynDecisionProtocol.encodeText("KN")).append("\n")
        append("END")
    }

    private fun planTarget(index: Int) = buildString {
        val step = "read_$index"
        append("KELLVYN/1\nPLAN\n")
        append("ID\t").append(KellvynDecisionProtocol.encodeText("task_$index")).append("\n")
        append("GOAL\t").append(KellvynDecisionProtocol.encodeText("Consultar KN $index.")).append("\n")
        append("STEP\t").append(KellvynDecisionProtocol.encodeText(step)).append("\t")
            .append(KellvynDecisionProtocol.encodeText("Consultar registro")).append("\t")
            .append(KellvynDecisionProtocol.encodeText("Ler registro local $index.")).append("\t")
            .append("READ_ONLY\tfalse\t-\tV1\n")
        append("TOOL\t").append(KellvynDecisionProtocol.encodeText(step)).append("\t")
            .append(KellvynDecisionProtocol.encodeText("local_lookup")).append("\n")
        append("CRITERION\t").append(KellvynDecisionProtocol.encodeText(step)).append("\t")
            .append(KellvynDecisionProtocol.encodeText("Registro $index foi retornado.")).append("\n")
        append("END")
    }

    private fun example(id: String, completion: String) = KellvynInstructionTrainingExample(
        id = id,
        prompt = "Solicitação de teste $id",
        completion = completion,
        split = KellvynTrainingSplit.TEST,
        provenance = KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://kn004-decision-benchmark/$id",
            rightsEvidence = "Exemplo sintético para benchmark do protocolo KN."
        )
    )

    private fun manifestEntry(example: KellvynInstructionTrainingExample) =
        KellvynTrainingManifestEntry(
            id = example.id,
            split = example.split,
            permission = example.provenance.permission,
            licenseId = example.provenance.licenseId,
            sourceReference = example.provenance.sourceReference,
            rightsEvidence = example.provenance.rightsEvidence,
            contentSha256 = MessageDigest.getInstance("SHA-256")
                .digest((example.prompt + "\u0000" + example.completion).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        )
}
