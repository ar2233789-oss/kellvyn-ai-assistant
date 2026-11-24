package com.kellvyn.assistant

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.security.MessageDigest

internal data class KellvynTaskCheckpointMetadata(
    val taskId: String,
    val sequence: Long,
    val createdAtMillis: Long,
    val previousSha256: String,
    val sha256: String
)

internal data class KellvynTaskCheckpointSnapshot(
    val metadata: KellvynTaskCheckpointMetadata,
    val state: KellvynTaskState
)

internal interface KellvynTaskCheckpointCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * Append-only, app-private task snapshots.
 *
 * Every snapshot contains a complete planner state and is linked to the previous snapshot
 * with SHA-256. A load validates the whole chain before exposing the latest state. The store
 * does not decide whether a task is safe to execute; KellvynTaskPlanner and the tool policy
 * remain authoritative for that.
 *
 * The caller must run file operations off the Android main thread and provide the most recent
 * hash when appending to an existing task. That compare-and-swap check prevents stale workers
 * from silently overwriting newer task progress.
 */
internal class KellvynTaskCheckpointStore(
    private val rootDirectory: File,
    private val cipher: KellvynTaskCheckpointCipher
) {
    private val lock = Any()

    fun append(
        state: KellvynTaskState,
        createdAtMillis: Long,
        expectedPreviousSha256: String? = null
    ): KellvynTaskCheckpointMetadata = synchronized(lock) {
        validateTaskState(state)
        require(createdAtMillis > 0L) { "Data do checkpoint inválida." }

        val taskId = state.plan.id
        val taskDirectory = requireNotNull(taskDirectory(taskId, create = true)) {
            "Não foi possível resolver a pasta privada da tarefa."
        }
        val history = readChain(taskId, taskDirectory)
        check(history.size < MAX_CHECKPOINTS_PER_TASK) {
            "O histórico desta tarefa atingiu o limite de checkpoints."
        }

        val latestHash = history.lastOrNull()?.metadata?.sha256
        check(latestHash == expectedPreviousSha256) {
            "O checkpoint mudou desde a última leitura. Recarregue o estado antes de salvar."
        }

        val sequence = (history.lastOrNull()?.metadata?.sequence ?: 0L) + 1L
        val previousHash = latestHash ?: ZERO_HASH
        val payload = encodeState(state)
        check(payload.size <= MAX_STATE_BYTES) { "O estado da tarefa excedeu o limite permitido." }

        val authenticatedHeader = encodeRecordHeader(taskId, sequence, createdAtMillis, previousHash)
        val encryptedPayload = cipher.encrypt(payload, authenticatedHeader)
        check(encryptedPayload.size in 1..MAX_ENCRYPTED_STATE_BYTES) {
            "O estado criptografado excedeu o limite permitido."
        }
        val body = encodeRecordBody(authenticatedHeader, encryptedPayload)
        val digest = sha256(body)
        val completeRecord = body + digest
        val file = File(taskDirectory, checkpointFileName(sequence))
        val temporary = File(taskDirectory, file.name + ".tmp")

        check(!file.exists()) { "O checkpoint de destino já existe." }
        FileOutputStream(temporary).use { output ->
            output.write(completeRecord)
            output.fd.sync()
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Não foi possível confirmar atomicamente o checkpoint.")
        }

        KellvynTaskCheckpointMetadata(
            taskId = taskId,
            sequence = sequence,
            createdAtMillis = createdAtMillis,
            previousSha256 = previousHash,
            sha256 = toHex(digest)
        )
    }

    fun loadLatest(taskId: String): KellvynTaskCheckpointSnapshot? = synchronized(lock) {
        val directory = taskDirectory(taskId, create = false) ?: return@synchronized null
        readChain(taskId, directory).lastOrNull()
    }

    fun history(taskId: String): List<KellvynTaskCheckpointMetadata> = synchronized(lock) {
        val directory = taskDirectory(taskId, create = false) ?: return@synchronized emptyList()
        readChain(taskId, directory).map { it.metadata }
    }

    fun listTaskIds(): List<String> = synchronized(lock) {
        if (!rootDirectory.exists()) return@synchronized emptyList()
        check(rootDirectory.isDirectory) { "A raiz dos checkpoints não é um diretório." }
        val directories = rootDirectory.listFiles()
            ?.filter { it.isDirectory && TASK_ID.matches(it.name) }
            .orEmpty()
        directories.map { directory ->
            readChain(directory.name, directory)
            directory.name
        }.sorted()
    }

    private fun taskDirectory(taskId: String, create: Boolean): File? {
        require(TASK_ID.matches(taskId)) { "Identificador de tarefa inválido." }
        if (!rootDirectory.exists() && !create) return null
        if (create) {
            check(rootDirectory.exists() || rootDirectory.mkdirs()) {
                "Não foi possível criar a raiz privada dos checkpoints."
            }
        }
        check(rootDirectory.isDirectory) { "A raiz dos checkpoints não é um diretório." }

        val root = rootDirectory.canonicalFile
        val directory = File(root, taskId)
        check(directory.canonicalFile.parentFile == root) {
            "O caminho do checkpoint saiu da área privada."
        }
        if (!directory.exists()) {
            if (!create) return null
            check(directory.mkdirs()) { "Não foi possível criar a pasta da tarefa." }
        }
        check(directory.isDirectory) { "O caminho da tarefa não é um diretório." }
        return directory
    }

    private fun readChain(
        taskId: String,
        directory: File
    ): List<KellvynTaskCheckpointSnapshot> {
        val files = directory.listFiles()
            ?.filter { it.isFile && CHECKPOINT_FILE.matches(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
        check(files.size <= MAX_CHECKPOINTS_PER_TASK) {
            "O histórico contém checkpoints acima do limite permitido."
        }

        val snapshots = mutableListOf<KellvynTaskCheckpointSnapshot>()
        var expectedSequence = 1L
        var expectedPreviousHash = ZERO_HASH

        for (file in files) {
            check(file.name == checkpointFileName(expectedSequence)) {
                "O nome do arquivo não corresponde à sequência do checkpoint."
            }
            val snapshot = readRecord(file, taskId)
            val metadata = snapshot.metadata
            check(metadata.sequence == expectedSequence) {
                "A sequência do histórico está incompleta ou fora de ordem."
            }
            check(metadata.previousSha256 == expectedPreviousHash) {
                "A cadeia de integridade dos checkpoints foi interrompida."
            }
            snapshots += snapshot
            expectedSequence++
            expectedPreviousHash = metadata.sha256
        }
        return snapshots
    }

    private fun readRecord(
        file: File,
        expectedTaskId: String
    ): KellvynTaskCheckpointSnapshot {
        check(file.length() in (HASH_BYTES + MIN_RECORD_BYTES).toLong()..MAX_RECORD_BYTES.toLong()) {
            "Checkpoint vazio, truncado ou acima do limite."
        }
        val allBytes = file.readBytes()
        check(allBytes.size >= HASH_BYTES + MIN_RECORD_BYTES) { "Checkpoint truncado." }

        val body = allBytes.copyOfRange(0, allBytes.size - HASH_BYTES)
        val storedDigest = allBytes.copyOfRange(allBytes.size - HASH_BYTES, allBytes.size)
        val computedDigest = sha256(body)
        check(MessageDigest.isEqual(storedDigest, computedDigest)) {
            "A integridade SHA-256 do checkpoint não confere."
        }

        val input = DataInputStream(ByteArrayInputStream(body))
        check(input.readInt() == MAGIC) { "Formato de checkpoint desconhecido." }
        check(input.readInt() == FORMAT_VERSION) { "Versão de checkpoint não suportada." }
        val taskId = readString(input, MAX_TASK_ID_BYTES)
        val sequence = input.readLong()
        val createdAtMillis = input.readLong()
        val previousHash = readString(input, HASH_HEX_LENGTH)
        check(taskId == expectedTaskId && TASK_ID.matches(taskId)) {
            "O checkpoint pertence a outra tarefa."
        }
        check(sequence > 0L && createdAtMillis > 0L) { "Metadados de checkpoint inválidos." }
        check(HASH_HEX.matches(previousHash)) { "Hash anterior inválido." }

        val headerLength = body.size - input.available()
        val authenticatedHeader = body.copyOfRange(0, headerLength)
        val encryptedLength = input.readInt()
        check(encryptedLength in 1..MAX_ENCRYPTED_STATE_BYTES) {
            "Tamanho do estado criptografado inválido."
        }
        val encryptedPayload = ByteArray(encryptedLength)
        input.readFully(encryptedPayload)
        check(input.available() == 0) { "O checkpoint contém dados extras." }

        val payload = cipher.decrypt(encryptedPayload, authenticatedHeader)
        check(payload.size in 1..MAX_STATE_BYTES) { "Tamanho do estado restaurado inválido." }
        val state = decodeState(payload)
        check(state.plan.id == taskId) { "O estado restaurado não corresponde à tarefa." }
        validateTaskState(state)

        return KellvynTaskCheckpointSnapshot(
            metadata = KellvynTaskCheckpointMetadata(
                taskId = taskId,
                sequence = sequence,
                createdAtMillis = createdAtMillis,
                previousSha256 = previousHash,
                sha256 = toHex(computedDigest)
            ),
            state = state
        )
    }

    private fun encodeRecordHeader(
        taskId: String,
        sequence: Long,
        createdAtMillis: Long,
        previousHash: String
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            writeString(output, taskId, MAX_TASK_ID_BYTES)
            output.writeLong(sequence)
            output.writeLong(createdAtMillis)
            writeString(output, previousHash, HASH_HEX_LENGTH)
        }
        return bytes.toByteArray()
    }

    private fun encodeRecordBody(header: ByteArray, encryptedPayload: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream(header.size + encryptedPayload.size + 4)
        DataOutputStream(bytes).use { output ->
            output.write(header)
            output.writeInt(encryptedPayload.size)
            output.write(encryptedPayload)
        }
        return bytes.toByteArray()
    }

    private fun encodeState(state: KellvynTaskState): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            val plan = state.plan
            writeString(output, plan.id, MAX_TASK_ID_BYTES)
            writeString(output, plan.goal, MAX_GOAL_BYTES)
            output.writeInt(plan.schemaVersion)
            output.writeLong(plan.createdAtMillis)
            writeStringList(output, plan.constraints, MAX_CONSTRAINTS, MAX_CONSTRAINT_BYTES)

            output.writeInt(plan.steps.size)
            for (step in plan.steps) {
                writeString(output, step.id, MAX_STEP_ID_BYTES)
                writeString(output, step.title, MAX_TITLE_BYTES)
                writeString(output, step.objective, MAX_OBJECTIVE_BYTES)
                writeStringSet(output, step.dependencies, MAX_STEPS, MAX_STEP_ID_BYTES)
                writeStringSet(output, step.requiredTools, MAX_TOOLS, MAX_TOOL_NAME_BYTES)
                writeString(output, step.risk.name, MAX_ENUM_BYTES)
                output.writeBoolean(step.ownerApprovalRequired)
                writeStringList(output, step.preconditions, MAX_PRECONDITIONS, MAX_CRITERION_BYTES)
                writeStringList(output, step.successCriteria, MAX_CRITERIA, MAX_CRITERION_BYTES)
                writeNullableString(output, step.recoveryPlan, MAX_RECOVERY_BYTES)
            }

            writeEnumMap(output, state.statuses.mapValues { it.value.name }, MAX_STEPS)
            writeStringSet(output, state.approvedStepIds, MAX_STEPS, MAX_STEP_ID_BYTES)

            output.writeInt(state.attempts.size)
            for ((id, attempts) in state.attempts.toSortedMap()) {
                writeString(output, id, MAX_STEP_ID_BYTES)
                output.writeInt(attempts)
            }

            output.writeInt(state.proofs.size)
            for ((id, proof) in state.proofs.toSortedMap()) {
                writeString(output, id, MAX_STEP_ID_BYTES)
                writeString(output, proof.verifiedBy, MAX_VERIFIER_BYTES)
                writeStringSet(output, proof.verifiedCriteria, MAX_CRITERIA, MAX_CRITERION_BYTES)
                writeString(output, proof.summary, MAX_EVIDENCE_SUMMARY_BYTES)
                writeString(output, proof.evidenceSha256, HASH_HEX_LENGTH)
                output.writeLong(proof.observedAtMillis)
            }

            output.writeInt(state.preconditionProofs.size)
            for ((id, proof) in state.preconditionProofs.toSortedMap()) {
                writeString(output, id, MAX_STEP_ID_BYTES)
                writeString(output, proof.checkedBy, MAX_VERIFIER_BYTES)
                writeStringSet(output, proof.verifiedConditions, MAX_PRECONDITIONS, MAX_CRITERION_BYTES)
                writeString(output, proof.summary, MAX_EVIDENCE_SUMMARY_BYTES)
                writeString(output, proof.evidenceSha256, HASH_HEX_LENGTH)
                output.writeLong(proof.observedAtMillis)
            }

            output.writeInt(state.failureReasons.size)
            for ((id, reason) in state.failureReasons.toSortedMap()) {
                writeString(output, id, MAX_STEP_ID_BYTES)
                writeString(output, reason, MAX_FAILURE_BYTES)
            }
        }
        return bytes.toByteArray()
    }

    private fun decodeState(payload: ByteArray): KellvynTaskState {
        val input = DataInputStream(ByteArrayInputStream(payload))
        val id = readString(input, MAX_TASK_ID_BYTES)
        val goal = readString(input, MAX_GOAL_BYTES)
        val schemaVersion = input.readInt()
        val createdAtMillis = input.readLong()
        val constraints = readStringList(input, MAX_CONSTRAINTS, MAX_CONSTRAINT_BYTES)

        val stepCount = readCount(input, MAX_STEPS)
        val steps = List(stepCount) {
            KellvynTaskStep(
                id = readString(input, MAX_STEP_ID_BYTES),
                title = readString(input, MAX_TITLE_BYTES),
                objective = readString(input, MAX_OBJECTIVE_BYTES),
                dependencies = readStringSet(input, MAX_STEPS, MAX_STEP_ID_BYTES),
                requiredTools = readStringSet(input, MAX_TOOLS, MAX_TOOL_NAME_BYTES),
                risk = readEnum(input),
                ownerApprovalRequired = input.readBoolean(),
                preconditions = readStringList(input, MAX_PRECONDITIONS, MAX_CRITERION_BYTES),
                successCriteria = readStringList(input, MAX_CRITERIA, MAX_CRITERION_BYTES),
                recoveryPlan = readNullableString(input, MAX_RECOVERY_BYTES)
            )
        }

        val statuses = readEnumMap<KellvynStepStatus>(input, MAX_STEPS)
        val approved = readStringSet(input, MAX_STEPS, MAX_STEP_ID_BYTES)

        val attemptsCount = readCount(input, MAX_STEPS)
        val attempts = buildMap {
            repeat(attemptsCount) {
                val key = readString(input, MAX_STEP_ID_BYTES)
                check(put(key, input.readInt()) == null) { "Tentativas duplicadas no checkpoint." }
            }
        }

        val proofsCount = readCount(input, MAX_STEPS)
        val proofs = buildMap {
            repeat(proofsCount) {
                val key = readString(input, MAX_STEP_ID_BYTES)
                val proof = KellvynStepProof(
                    verifiedBy = readString(input, MAX_VERIFIER_BYTES),
                    verifiedCriteria = readStringSet(input, MAX_CRITERIA, MAX_CRITERION_BYTES),
                    summary = readString(input, MAX_EVIDENCE_SUMMARY_BYTES),
                    evidenceSha256 = readString(input, HASH_HEX_LENGTH),
                    observedAtMillis = input.readLong()
                )
                check(put(key, proof) == null) { "Evidências duplicadas no checkpoint." }
            }
        }

        val preconditionsCount = readCount(input, MAX_STEPS)
        val preconditions = buildMap {
            repeat(preconditionsCount) {
                val key = readString(input, MAX_STEP_ID_BYTES)
                val proof = KellvynPreconditionEvidence(
                    checkedBy = readString(input, MAX_VERIFIER_BYTES),
                    verifiedConditions = readStringSet(input, MAX_PRECONDITIONS, MAX_CRITERION_BYTES),
                    summary = readString(input, MAX_EVIDENCE_SUMMARY_BYTES),
                    evidenceSha256 = readString(input, HASH_HEX_LENGTH),
                    observedAtMillis = input.readLong()
                )
                check(put(key, proof) == null) { "Pré-condições duplicadas no checkpoint." }
            }
        }

        val failuresCount = readCount(input, MAX_STEPS)
        val failures = buildMap {
            repeat(failuresCount) {
                val key = readString(input, MAX_STEP_ID_BYTES)
                check(put(key, readString(input, MAX_FAILURE_BYTES)) == null) {
                    "Falhas duplicadas no checkpoint."
                }
            }
        }
        check(input.available() == 0) { "O estado salvo contém campos extras." }

        return KellvynTaskState(
            plan = KellvynTaskPlan(
                id = id,
                goal = goal,
                constraints = constraints,
                steps = steps,
                createdAtMillis = createdAtMillis,
                schemaVersion = schemaVersion
            ),
            statuses = statuses,
            approvedStepIds = approved,
            attempts = attempts,
            proofs = proofs,
            preconditionProofs = preconditions,
            failureReasons = failures
        )
    }

    private fun validateTaskState(state: KellvynTaskState) {
        val plan = state.plan
        require(TASK_ID.matches(plan.id)) { "Identificador de tarefa inválido." }
        require(plan.schemaVersion == 1 && plan.createdAtMillis > 0L)
        require(plan.goal.isNotBlank() && plan.goal.length <= 4_000)
        require(plan.steps.size in 1..MAX_STEPS)
        require(plan.constraints.size <= MAX_CONSTRAINTS)

        val stepIds = plan.steps.map { it.id }
        require(stepIds.distinct().size == stepIds.size)
        require(stepIds.all { STEP_ID.matches(it) })
        require(state.statuses.keys == stepIds.toSet()) {
            "O estado precisa conter exatamente o status de cada etapa."
        }

        val knownIds = stepIds.toSet()
        require(state.approvedStepIds.all { it in knownIds })
        require(state.attempts.keys.all { it in knownIds })
        require(state.attempts.values.all { it in 1..MAX_ATTEMPTS })
        require(state.proofs.keys.all { it in knownIds })
        require(state.preconditionProofs.keys.all { it in knownIds })
        require(state.failureReasons.keys.all { it in knownIds })

        val stepsById = plan.steps.associateBy { it.id }
        for ((id, proof) in state.proofs) {
            require(state.statuses[id] == KellvynStepStatus.SUCCEEDED)
            require(proof.verifiedBy.isNotBlank() && proof.summary.isNotBlank())
            require(proof.observedAtMillis > 0L && HASH_HEX.matches(proof.evidenceSha256))
            require(proof.verifiedCriteria.containsAll(stepsById.getValue(id).successCriteria))
        }
        for ((id, proof) in state.preconditionProofs) {
            require(stepsById.getValue(id).preconditions.isNotEmpty())
            require(proof.checkedBy.isNotBlank() && proof.summary.isNotBlank())
            require(proof.observedAtMillis > 0L && HASH_HEX.matches(proof.evidenceSha256))
            require(proof.verifiedConditions.containsAll(stepsById.getValue(id).preconditions))
        }
        for ((id, reason) in state.failureReasons) {
            require(state.statuses[id] == KellvynStepStatus.FAILED)
            require(reason.isNotBlank() && reason.length <= MAX_FAILURE_BYTES)
        }
        for (step in plan.steps) {
            if (state.statuses[step.id] == KellvynStepStatus.SUCCEEDED) {
                require(step.id in state.proofs) { "Etapa concluída sem evidência persistida." }
            }
            if (step.preconditions.isNotEmpty() &&
                state.statuses[step.id] in setOf(
                    KellvynStepStatus.RUNNING,
                    KellvynStepStatus.SUCCEEDED,
                    KellvynStepStatus.FAILED
                )
            ) {
                require(step.id in state.preconditionProofs) {
                    "Etapa iniciada sem evidência de pré-condições."
                }
            }
        }

        val registeredRisks = mutableMapOf<String, KellvynStepRisk>()
        for (step in plan.steps) {
            for (tool in step.requiredTools) {
                val old = registeredRisks[tool]
                if (old == null || step.risk.ordinal > old.ordinal) registeredRisks[tool] = step.risk
            }
        }
        val planErrors = KellvynTaskPlanner().validate(plan, registeredRisks)
        require(planErrors.isEmpty()) { planErrors.joinToString(" ") }
    }

    private fun writeEnumMap(output: DataOutputStream, values: Map<String, String>, maxCount: Int) {
        require(values.size <= maxCount)
        output.writeInt(values.size)
        for ((key, value) in values.toSortedMap()) {
            writeString(output, key, MAX_STEP_ID_BYTES)
            writeString(output, value, MAX_ENUM_BYTES)
        }
    }

    private inline fun <reified T : Enum<T>> readEnum(input: DataInputStream): T =
        enumValueOf(readString(input, MAX_ENUM_BYTES))

    private inline fun <reified T : Enum<T>> readEnumMap(
        input: DataInputStream,
        maxCount: Int
    ): Map<String, T> {
        val count = readCount(input, maxCount)
        return buildMap {
            repeat(count) {
                val key = readString(input, MAX_STEP_ID_BYTES)
                val value = readEnum<T>(input)
                check(put(key, value) == null) { "Status duplicado no checkpoint." }
            }
        }
    }

    private fun writeStringSet(
        output: DataOutputStream,
        values: Set<String>,
        maxCount: Int,
        maxBytes: Int
    ) = writeStringList(output, values.sorted(), maxCount, maxBytes)

    private fun readStringSet(
        input: DataInputStream,
        maxCount: Int,
        maxBytes: Int
    ): Set<String> {
        val values = readStringList(input, maxCount, maxBytes)
        check(values.distinct().size == values.size) { "Conjunto duplicado no checkpoint." }
        return values.toSet()
    }

    private fun writeStringList(
        output: DataOutputStream,
        values: List<String>,
        maxCount: Int,
        maxBytes: Int
    ) {
        require(values.size <= maxCount)
        output.writeInt(values.size)
        values.forEach { writeString(output, it, maxBytes) }
    }

    private fun readStringList(
        input: DataInputStream,
        maxCount: Int,
        maxBytes: Int
    ): List<String> {
        val count = readCount(input, maxCount)
        return List(count) { readString(input, maxBytes) }
    }

    private fun writeNullableString(output: DataOutputStream, value: String?, maxBytes: Int) {
        output.writeBoolean(value != null)
        if (value != null) writeString(output, value, maxBytes)
    }

    private fun readNullableString(input: DataInputStream, maxBytes: Int): String? =
        if (input.readBoolean()) readString(input, maxBytes) else null

    private fun writeString(output: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(UTF8)
        require(bytes.size <= maxBytes) { "Campo textual acima do limite do checkpoint." }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val length = input.readInt()
        check(length in 0..maxBytes) { "Tamanho de texto inválido no checkpoint." }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        val value = String(bytes, UTF8)
        check(value.toByteArray(UTF8).contentEquals(bytes)) { "Texto UTF-8 inválido no checkpoint." }
        return value
    }

    private fun readCount(input: DataInputStream, maxCount: Int): Int {
        val value = input.readInt()
        check(value in 0..maxCount) { "Quantidade inválida no checkpoint." }
        return value
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun checkpointFileName(sequence: Long): String =
        "%08d.kcp".format(java.util.Locale.ROOT, sequence)

    companion object {
        private const val MAGIC = 0x4B56544B // "KVTK"
        private const val FORMAT_VERSION = 1
        private const val HASH_BYTES = 32
        private const val HASH_HEX_LENGTH = 64
        private const val MAX_CHECKPOINTS_PER_TASK = 200
        private const val MAX_STATE_BYTES = 1_048_576
        private const val MAX_ENCRYPTED_STATE_BYTES = MAX_STATE_BYTES + 128
        private const val MAX_RECORD_BYTES = MAX_STATE_BYTES + 8_192
        private const val MIN_RECORD_BYTES = 32
        private const val MAX_TASK_ID_BYTES = 80
        private const val MAX_STEP_ID_BYTES = 64
        private const val MAX_GOAL_BYTES = 16_000
        private const val MAX_CONSTRAINTS = 40
        private const val MAX_CONSTRAINT_BYTES = 2_000
        private const val MAX_STEPS = 64
        private const val MAX_TOOLS = 64
        private const val MAX_TOOL_NAME_BYTES = 64
        private const val MAX_TITLE_BYTES = 480
        private const val MAX_OBJECTIVE_BYTES = 4_000
        private const val MAX_PRECONDITIONS = 12
        private const val MAX_CRITERIA = 12
        private const val MAX_CRITERION_BYTES = 1_200
        private const val MAX_RECOVERY_BYTES = 4_000
        private const val MAX_ENUM_BYTES = 64
        private const val MAX_VERIFIER_BYTES = 240
        private const val MAX_EVIDENCE_SUMMARY_BYTES = 8_000
        private const val MAX_FAILURE_BYTES = 2_000
        private const val MAX_ATTEMPTS = 1_000
        private const val MAX_TASK_ID_CHARS = 80
        private val ZERO_HASH = "0".repeat(HASH_HEX_LENGTH)
        private val UTF8: Charset = Charsets.UTF_8
        private val TASK_ID = Regex("[A-Za-z0-9_-]{1,$MAX_TASK_ID_CHARS}")
        private val STEP_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val HASH_HEX = Regex("[a-f0-9]{64}")
        private val CHECKPOINT_FILE = Regex("[0-9]{8}\\.kcp")
    }
}
