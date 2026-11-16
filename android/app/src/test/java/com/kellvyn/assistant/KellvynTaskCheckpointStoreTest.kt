package com.kellvyn.assistant

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KellvynTaskCheckpointStoreTest {
    private lateinit var root: File
    private lateinit var store: KellvynTaskCheckpointStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kellvyn-task-checkpoints").toFile()
        store = KellvynTaskCheckpointStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun completeTaskStateRoundTripsAcrossAppendOnlyCheckpoints() {
        val planner = KellvynTaskPlanner()
        val initial = planner.createState(samplePlan())
        val withStartedStep = planner.startStep(
            initial,
            "inspect",
            KellvynPreconditionEvidence(
                checkedBy = "workspace-validator",
                verifiedConditions = setOf("A pasta privada existe"),
                summary = "A pasta do projeto foi localizada.",
                evidenceSha256 = "c".repeat(64),
                observedAtMillis = 10L
            )
        )
        val completed = planner.completeStep(
            withStartedStep,
            "inspect",
            KellvynTaskOutcome(
                summary = "Arquivo encontrado e conteúdo lido.",
                exitCode = 0,
                artifactRefs = setOf("workspace://project/README.md"),
                observedAtMillis = 20L
            ),
            KellvynIndependentVerifier { step, _ ->
                KellvynStepProof(
                    verifiedBy = "file-inspector",
                    verifiedCriteria = step.successCriteria.toSet(),
                    summary = "O arquivo existe e o conteúdo foi lido.",
                    evidenceSha256 = "a".repeat(64),
                    observedAtMillis = 21L
                )
            }
        )

        val first = store.append(completed, createdAtMillis = 100L)
        assertEquals(1L, first.sequence)
        assertEquals("0".repeat(64), first.previousSha256)
        assertTrue(first.sha256.matches(Regex("[a-f0-9]{64}")))

        val restored = store.loadLatest("task-checkpoint-001")
        assertNotNull(restored)
        assertEquals(completed, restored!!.state)
        assertEquals(first, restored.metadata)

        val approved = planner.approveStep(completed, "create")
        val second = store.append(
            approved,
            createdAtMillis = 200L,
            expectedPreviousSha256 = first.sha256
        )

        assertEquals(2L, second.sequence)
        assertEquals(first.sha256, second.previousSha256)
        assertEquals(listOf(first, second), store.history("task-checkpoint-001"))
        assertEquals(approved, store.loadLatest("task-checkpoint-001")!!.state)
        assertEquals(listOf("task-checkpoint-001"), store.listTaskIds())
    }

    @Test
    fun staleWriterCannotAppendWithoutTheLatestHash() {
        val state = KellvynTaskPlanner().createState(samplePlan())
        val first = store.append(state, createdAtMillis = 100L)

        val failure = runCatching {
            store.append(state, createdAtMillis = 200L)
        }.exceptionOrNull()

        assertTrue("A gravação obsoleta deveria ser recusada.", failure is IllegalStateException)
        assertEquals(listOf(first), store.history(state.plan.id))
    }

    @Test
    fun modifiedCheckpointIsRejectedBeforeStateIsReturned() {
        val state = KellvynTaskPlanner().createState(samplePlan())
        store.append(state, createdAtMillis = 100L)

        val checkpoint = File(File(root, state.plan.id), "00000001.kcp")
        val bytes = checkpoint.readBytes()
        bytes[12] = (bytes[12].toInt() xor 0x01).toByte()
        checkpoint.writeBytes(bytes)

        val failure = runCatching { store.loadLatest(state.plan.id) }.exceptionOrNull()
        assertTrue("A corrupção precisa ser detectada pelo hash.", failure is IllegalStateException)
    }

    @Test
    fun brokenSequenceOrMissingPreviousCheckpointIsRejected() {
        val state = KellvynTaskPlanner().createState(samplePlan())
        store.append(state, createdAtMillis = 100L)

        val directory = File(root, state.plan.id)
        assertTrue(File(directory, "00000001.kcp").renameTo(File(directory, "00000002.kcp")))

        val failure = runCatching { store.loadLatest(state.plan.id) }.exceptionOrNull()
        assertTrue("A lacuna na sequência deveria ser detectada.", failure is IllegalStateException)
    }

    @Test
    fun taskIdCannotEscapePrivateCheckpointRoot() {
        val state = KellvynTaskPlanner().createState(samplePlan())
        val invalid = state.copy(plan = state.plan.copy(id = "../outside"))

        val failure = runCatching {
            store.append(invalid, createdAtMillis = 100L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(File(root.parentFile, "outside").exists())
    }

    private fun samplePlan() = KellvynTaskPlan(
        id = "task-checkpoint-001",
        goal = "Inspecionar e depois criar um projeto.",
        constraints = listOf("Não alterar a interface protegida."),
        steps = listOf(
            KellvynTaskStep(
                id = "inspect",
                title = "Inspecionar",
                objective = "Ler os arquivos relevantes do projeto.",
                preconditions = listOf("A pasta privada existe"),
                successCriteria = listOf("O arquivo existe", "O conteúdo foi lido")
            ),
            KellvynTaskStep(
                id = "create",
                title = "Criar projeto",
                objective = "Criar os arquivos depois da inspeção.",
                dependencies = setOf("inspect"),
                risk = KellvynStepRisk.REVERSIBLE_WRITE,
                ownerApprovalRequired = true,
                successCriteria = listOf("Os arquivos foram criados", "O diff foi verificado"),
                recoveryPlan = "Restaurar o checkpoint anterior."
            )
        ),
        createdAtMillis = 1L
    )
}
