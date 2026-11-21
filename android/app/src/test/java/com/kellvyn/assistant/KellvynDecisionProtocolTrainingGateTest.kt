package com.kellvyn.assistant

import com.kellvyn.assistant.neural.KellvynInstructionTrainingExample
import com.kellvyn.assistant.neural.KellvynTrainingPermission
import com.kellvyn.assistant.neural.KellvynTrainingProvenance
import com.kellvyn.assistant.neural.KellvynTrainingSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynDecisionProtocolTrainingGateTest {
    @Test
    fun acceptsOnlyValidFinalToolAndPlanTargetsAcrossAllSplits() {
        val report = KellvynDecisionProtocolTrainingGate.evaluate(
            examples = sampleExamples(),
            tools = registry(),
            minimumTrainExamples = 3,
            minimumValidationExamples = 3,
            minimumTestExamples = 3,
            minimumTrainPerDecision = 1,
            minimumValidationPerDecision = 1,
            minimumTestPerDecision = 1
        )

        assertTrue(report.readyForDecisionTraining)
        assertEquals(3, report.trainExamples)
        assertEquals(3, report.validationExamples)
        assertEquals(3, report.testExamples)
        assertEquals(1, report.trainCounts.finalDecision)
        assertEquals(1, report.trainCounts.callTool)
        assertEquals(1, report.trainCounts.plan)
        assertEquals(0, report.rejectedExamples)
    }

    @Test
    fun rejectsFreeFormCompletionsInsteadOfTreatingThemAsDecisionTraining() {
        val examples = sampleExamples().toMutableList()
        examples[0] = examples[0].copy(completion = "Claro, vou pesquisar para você.")

        val report = KellvynDecisionProtocolTrainingGate.evaluate(
            examples = examples,
            tools = registry(),
            minimumTrainExamples = 3,
            minimumValidationExamples = 3,
            minimumTestExamples = 3,
            minimumTrainPerDecision = 1,
            minimumValidationPerDecision = 1,
            minimumTestPerDecision = 1
        )

        assertFalse(report.readyForDecisionTraining)
        assertEquals(1, report.rejectedExamples)
        assertTrue(report.reasons.any { it.contains("KELLVYN/1") })
    }

    @Test
    fun rejectsCallsToToolsThatAreNotRegistered() {
        val examples = sampleExamples().toMutableList()
        examples[1] = examples[1].copy(
            completion = "KELLVYN/1\nCALL_TOOL\nNAME\t${KellvynDecisionProtocol.encodeText("missing_tool")}\nEND"
        )

        val report = KellvynDecisionProtocolTrainingGate.evaluate(
            examples = examples,
            tools = registry(),
            minimumTrainExamples = 3,
            minimumValidationExamples = 3,
            minimumTestExamples = 3,
            minimumTrainPerDecision = 1,
            minimumValidationPerDecision = 1,
            minimumTestPerDecision = 1
        )

        assertFalse(report.readyForDecisionTraining)
        assertEquals(1, report.rejectedExamples)
        assertTrue(report.reasons.any { it.contains("ferramenta não registrada") })
    }

    @Test
    fun rejectsPlansThatReferenceToolsOutsideTheLocalRegistry() {
        val examples = sampleExamples().toMutableList()
        examples[2] = examples[2].copy(completion = planDecision("missing_tool"))

        val report = KellvynDecisionProtocolTrainingGate.evaluate(
            examples = examples,
            tools = registry(),
            minimumTrainExamples = 3,
            minimumValidationExamples = 3,
            minimumTestExamples = 3,
            minimumTrainPerDecision = 1,
            minimumValidationPerDecision = 1,
            minimumTestPerDecision = 1
        )

        assertFalse(report.readyForDecisionTraining)
        assertEquals(1, report.rejectedExamples)
        assertTrue(report.reasons.any { it.contains("Ferramentas não registradas") })
    }

    @Test
    fun rejectsDecisionTargetsThatTryToTransferReasoning() {
        val examples = sampleExamples().toMutableList()
        examples[0] = examples[0].copy(
            completion =
                "KELLVYN/1\nDELEGATE\nROLE\tRESEARCHER\n" +
                    "TASK\t" + KellvynDecisionProtocol.encodeText("Pesquisar") + "\n" +
                    "CONTEXT\t" + KellvynDecisionProtocol.encodeText("Nenhum contexto") + "\nEND"
        )

        val report = KellvynDecisionProtocolTrainingGate.evaluate(
            examples = examples,
            tools = registry(),
            minimumTrainExamples = 3,
            minimumValidationExamples = 3,
            minimumTestExamples = 3,
            minimumTrainPerDecision = 1,
            minimumValidationPerDecision = 1,
            minimumTestPerDecision = 1
        )

        assertFalse(report.readyForDecisionTraining)
        assertEquals(1, report.rejectedExamples)
        assertTrue(report.reasons.any { it.contains("transfere raciocínio") })
    }

    @Test
    fun planValidationAcceptsEveryRegisteredToolRiskCategory() {
        val extraRisks = listOf(
            KellvynToolRisk.ARTIFACT_CREATE,
            KellvynToolRisk.APPEND_ONLY_TASK_CREATE,
            KellvynToolRisk.CANCEL_QUEUED_TASK
        )
        val extraTools = extraRisks.mapIndexed { index, risk ->
            object : KellvynAgentTool {
                override val spec = KellvynToolSpec(
                    name = "extra_tool_$index",
                    description = "Ferramenta de teste para o risco $risk.",
                    risk = risk
                )
                override suspend fun execute(arguments: Map<String, String>) =
                    KellvynToolResult(true, "ok")
            }
        }
        val registry = KellvynToolRegistry(
            listOf(requireNotNull(registry().find("local_lookup"))) + extraTools
        )

        val report = KellvynDecisionProtocolTrainingGate.evaluate(
            examples = sampleExamples(),
            tools = registry,
            minimumTrainExamples = 3,
            minimumValidationExamples = 3,
            minimumTestExamples = 3,
            minimumTrainPerDecision = 1,
            minimumValidationPerDecision = 1,
            minimumTestPerDecision = 1
        )

        assertTrue(report.readyForDecisionTraining)
        assertEquals(0, report.rejectedExamples)
        assertEquals(KellvynStepRisk.REVERSIBLE_WRITE, KellvynToolRisk.ARTIFACT_CREATE.toStepRisk())
        assertEquals(KellvynStepRisk.REVERSIBLE_WRITE, KellvynToolRisk.APPEND_ONLY_TASK_CREATE.toStepRisk())
        assertEquals(KellvynStepRisk.REVERSIBLE_WRITE, KellvynToolRisk.CANCEL_QUEUED_TASK.toStepRisk())
    }

    private fun sampleExamples(): List<KellvynInstructionTrainingExample> =
        KellvynTrainingSplit.values().flatMap { split ->
            listOf(
                example("${split.name.lowercase()}-final", split, finalDecision()),
                example("${split.name.lowercase()}-tool", split, toolDecision()),
                example("${split.name.lowercase()}-plan", split, planDecision())
            )
        }

    private fun finalDecision() =
        "KELLVYN/1\nFINAL\nTEXT\t${KellvynDecisionProtocol.encodeText("A consulta foi concluída.")}\nEND"

    private fun toolDecision() = buildString {
        append("KELLVYN/1\nCALL_TOOL\n")
        append("NAME\t").append(KellvynDecisionProtocol.encodeText(toolName)).append("\n")
        append("ARG\t").append(KellvynDecisionProtocol.encodeText("query"))
            .append("\t").append(KellvynDecisionProtocol.encodeText("KN")).append("\n")
        append("END")
    }

    private fun planDecision(toolName: String = "local_lookup"): String {
        val stepId = "inspect"
        return buildString {
            append("KELLVYN/1\nPLAN\n")
            append("ID\t").append(KellvynDecisionProtocol.encodeText("task_kn")).append("\n")
            append("GOAL\t").append(KellvynDecisionProtocol.encodeText("Consultar o registro KN.")).append("\n")
            append("STEP\t").append(KellvynDecisionProtocol.encodeText(stepId)).append("\t")
                .append(KellvynDecisionProtocol.encodeText("Consultar registro")).append("\t")
                .append(KellvynDecisionProtocol.encodeText("Ler o registro local KN.")).append("\t")
                .append("READ_ONLY\tfalse\t-\tV1\n")
            append("TOOL\t").append(KellvynDecisionProtocol.encodeText(stepId)).append("\t")
                .append(KellvynDecisionProtocol.encodeText(toolName)).append("\n")
            append("CRITERION\t").append(KellvynDecisionProtocol.encodeText(stepId)).append("\t")
                .append(KellvynDecisionProtocol.encodeText("O resultado contém o registro KN.")).append("\n")
            append("END")
        }
    }

    private fun registry() = KellvynToolRegistry(listOf(object : KellvynAgentTool {
        override val spec = KellvynToolSpec(
            name = "local_lookup",
            description = "Consulta um registro local pelo termo.",
            requiredArguments = setOf("query"),
            risk = KellvynToolRisk.READ_ONLY
        )
        override suspend fun execute(arguments: Map<String, String>) =
            KellvynToolResult(true, "registro")
    }))

    private fun example(
        id: String,
        split: KellvynTrainingSplit,
        completion: String
    ) = KellvynInstructionTrainingExample(
        id = id,
        prompt = "Solicitação única $id",
        completion = completion,
        split = split,
        provenance = KellvynTrainingProvenance(
            permission = KellvynTrainingPermission.OWNER_CREATED,
            licenseId = "owner-created",
            sourceReference = "owner://kn004-protocol-test/$id",
            rightsEvidence = "Exemplo sintético criado para testar o gate de protocolo."
        )
    )
}
