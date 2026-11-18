package com.kellvyn.assistant

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class KellvynAgentLabTraceRecord(
    val sequence: Long,
    val event: KellvynAgentLabEvent,
    val previousRecordSha256: String,
    val recordSha256: String
)

internal data class KellvynAgentLabTraceSummary(
    val traceId: String,
    val eventCount: Int,
    val startedAtMillis: Long,
    val lastEventAtMillis: Long,
    val terminalKind: KellvynAgentLabEventKind?
)

/**
 * Encrypted, app-private, append-only event store for the local Agent Lab.
 *
 * Every event is authenticated with its header as AES-GCM associated data and linked to
 * the previous record by SHA-256. No prompt text, model output, tool arguments or file
 * contents are accepted by the event schema.
 *
 * Retention is bounded by trace count and per-trace event count. Pruning is explicit and
 * only removes complete, verified traces older than the supplied cutoff.
 */
internal class KellvynAgentLabTraceStore(
    private val rootDirectory: File,
    private val cipher: KellvynTaskCheckpointCipher
) {
    fun append(event: KellvynAgentLabEvent): KellvynAgentLabTraceRecord =
        synchronized(PROCESS_LOCK) {
            require(KellvynAgentLabEvent.TRACE_ID.matches(event.traceId))
            val directory = traceDirectory(event.traceId, create = true)
            val chain = readChain(event.traceId, directory)
            check(chain.size < MAX_EVENTS_PER_TRACE) {
                "Este trace atingiu o limite de eventos; abra uma nova execução de laboratório."
            }

            val sequence = chain.size.toLong() + 1L
            val previousHash = chain.lastOrNull()?.recordSha256 ?: ZERO_HASH
            val header = encodeHeader(
                traceId = event.traceId,
                sequence = sequence,
                occurredAtMillis = event.occurredAtMillis,
                previousHash = previousHash
            )
            val payload = encodeEvent(event)
            check(payload.size <= MAX_EVENT_BYTES) {
                "O evento do laboratório excedeu o limite permitido."
            }
            val encrypted = cipher.encrypt(payload, header)
            check(encrypted.size in 1..MAX_ENCRYPTED_EVENT_BYTES) {
                "O evento criptografado excedeu o limite permitido."
            }
            val body = encodeBody(header, encrypted)
            val digest = sha256(body)
            val recordBytes = body + digest
            check(recordBytes.size <= MAX_RECORD_BYTES)

            val destination = File(directory, eventFileName(sequence))
            val temporary = File(directory, destination.name + ".tmp")
            check(!destination.exists()) { "O evento de destino já existe." }
            FileOutputStream(temporary).use { output ->
                output.write(recordBytes)
                output.fd.sync()
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                error("Não foi possível confirmar atomicamente o evento do laboratório.")
            }

            KellvynAgentLabTraceRecord(
                sequence = sequence,
                event = event,
                previousRecordSha256 = previousHash,
                recordSha256 = toHex(digest)
            )
        }

    fun history(traceId: String): List<KellvynAgentLabTraceRecord> =
        synchronized(PROCESS_LOCK) {
            val directory = traceDirectory(traceId, create = false)
                ?: return@synchronized emptyList()
            readChain(traceId, directory)
        }

    fun summaries(): List<KellvynAgentLabTraceSummary> = synchronized(PROCESS_LOCK) {
        if (!rootDirectory.exists()) return@synchronized emptyList()
        val root = validatedRoot()
        val directories = root.listFiles()
            ?.filter { it.isDirectory && KellvynAgentLabEvent.TRACE_ID.matches(it.name) }
            .orEmpty()
        check(directories.size <= MAX_TRACES) {
            "O laboratório contém traces acima do limite configurado."
        }
        directories.map { directory ->
            val records = readChain(directory.name, directory)
            check(records.isNotEmpty()) { "Foi encontrada uma pasta de trace vazia." }
            val first = records.first().event
            val last = records.last().event
            KellvynAgentLabTraceSummary(
                traceId = directory.name,
                eventCount = records.size,
                startedAtMillis = first.occurredAtMillis,
                lastEventAtMillis = last.occurredAtMillis,
                terminalKind = last.kind.takeIf(::isTerminal)
            )
        }.sortedByDescending { it.lastEventAtMillis }
    }

    /**
     * Explicit retention operation. A trace is pruned only after its whole hash chain
     * validates, it has a terminal event, and its final event is older than the cutoff.
     */
    fun pruneCompletedBefore(cutoffMillis: Long): Int = synchronized(PROCESS_LOCK) {
        require(cutoffMillis > 0L)
        if (!rootDirectory.exists()) return@synchronized 0
        val root = validatedRoot()
        val candidates = root.listFiles()
            ?.filter { it.isDirectory && KellvynAgentLabEvent.TRACE_ID.matches(it.name) }
            .orEmpty()
        var removed = 0
        for (directory in candidates) {
            val records = readChain(directory.name, directory)
            if (records.isEmpty()) continue
            val last = records.last().event
            if (isTerminal(last.kind) && last.occurredAtMillis < cutoffMillis) {
                check(directory.canonicalFile.parentFile == root) {
                    "A pasta de trace saiu da área privada do laboratório."
                }
                check(directory.deleteRecursively()) {
                    "Não foi possível remover um trace antigo já concluído."
                }
                removed++
            }
        }
        removed
    }

    private fun isTerminal(kind: KellvynAgentLabEventKind): Boolean = when (kind) {
        KellvynAgentLabEventKind.RUN_COMPLETED,
        KellvynAgentLabEventKind.RUN_PLANNED,
        KellvynAgentLabEventKind.RUN_FAILED,
        KellvynAgentLabEventKind.RUN_LIMIT_REACHED,
        KellvynAgentLabEventKind.RUN_CANCELLED -> true
        else -> false
    }

    private fun traceDirectory(traceId: String, create: Boolean): File? {
        require(KellvynAgentLabEvent.TRACE_ID.matches(traceId))
        if (!rootDirectory.exists() && !create) return null
        if (create) {
            check(rootDirectory.exists() || rootDirectory.mkdirs()) {
                "Não foi possível criar o diretório privado do Agent Lab."
            }
        }
        val root = validatedRoot()
        val directory = File(root, traceId)
        check(directory.canonicalFile.parentFile == root) {
            "O caminho do trace saiu da área privada do Agent Lab."
        }
        if (!directory.exists()) {
            if (!create) return null
            val traceCount = root.listFiles()
                ?.count { it.isDirectory && KellvynAgentLabEvent.TRACE_ID.matches(it.name) }
                ?: 0
            check(traceCount < MAX_TRACES) {
                "O Agent Lab atingiu o limite de traces. Revise e remova traces antigos antes de continuar."
            }
            check(directory.mkdirs()) { "Não foi possível criar a pasta do trace." }
        }
        check(directory.isDirectory) { "O caminho do trace não é um diretório." }
        return directory
    }

    private fun validatedRoot(): File {
        check(rootDirectory.exists() || rootDirectory.mkdirs()) {
            "Não foi possível criar a raiz do Agent Lab."
        }
        check(rootDirectory.isDirectory) { "A raiz do Agent Lab não é um diretório." }
        return rootDirectory.canonicalFile
    }

    private fun readChain(
        traceId: String,
        directory: File
    ): List<KellvynAgentLabTraceRecord> {
        val files = directory.listFiles()
            ?.filter { it.isFile && EVENT_FILE.matches(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
        check(files.size <= MAX_EVENTS_PER_TRACE) {
            "O histórico do trace excedeu o limite de eventos."
        }
        val records = mutableListOf<KellvynAgentLabTraceRecord>()
        var expectedSequence = 1L
        var expectedPreviousHash = ZERO_HASH
        for (file in files) {
            check(file.name == eventFileName(expectedSequence)) {
                "A sequência dos eventos do trace está incompleta ou fora de ordem."
            }
            val record = readRecord(file, traceId)
            check(record.sequence == expectedSequence) {
                "O número de sequência do evento não corresponde ao arquivo."
            }
            check(record.previousRecordSha256 == expectedPreviousHash) {
                "A cadeia de integridade do Agent Lab foi interrompida."
            }
            records += record
            expectedSequence++
            expectedPreviousHash = record.recordSha256
        }
        return records
    }

    private fun readRecord(file: File, expectedTraceId: String): KellvynAgentLabTraceRecord {
        check(file.length() in (HASH_BYTES + MIN_RECORD_BYTES).toLong()..MAX_RECORD_BYTES.toLong()) {
            "Evento vazio, truncado ou acima do limite."
        }
        val bytes = file.readBytes()
        val body = bytes.copyOfRange(0, bytes.size - HASH_BYTES)
        val storedDigest = bytes.copyOfRange(bytes.size - HASH_BYTES, bytes.size)
        val digest = sha256(body)
        check(MessageDigest.isEqual(storedDigest, digest)) {
            "A integridade SHA-256 do evento do laboratório não confere."
        }

        val input = DataInputStream(ByteArrayInputStream(body))
        check(input.readInt() == MAGIC) { "Formato de evento do laboratório desconhecido." }
        check(input.readInt() == FORMAT_VERSION) { "Versão de evento do laboratório não suportada." }
        val traceId = readString(input, MAX_TRACE_ID_BYTES)
        val sequence = input.readLong()
        val occurredAtMillis = input.readLong()
        val previousHash = readString(input, HASH_HEX_LENGTH)
        check(traceId == expectedTraceId && KellvynAgentLabEvent.TRACE_ID.matches(traceId))
        check(sequence > 0L && occurredAtMillis > 0L)
        check(HASH_HEX.matches(previousHash))

        val headerLength = body.size - input.available()
        val authenticatedHeader = body.copyOfRange(0, headerLength)
        val encryptedLength = input.readInt()
        check(encryptedLength in 1..MAX_ENCRYPTED_EVENT_BYTES)
        val encrypted = ByteArray(encryptedLength)
        input.readFully(encrypted)
        check(input.available() == 0) { "O registro contém bytes extras." }

        val event = decodeEvent(cipher.decrypt(encrypted, authenticatedHeader), traceId, occurredAtMillis)
        check(event.traceId == traceId && event.occurredAtMillis == occurredAtMillis)
        return KellvynAgentLabTraceRecord(
            sequence = sequence,
            event = event,
            previousRecordSha256 = previousHash,
            recordSha256 = toHex(digest)
        )
    }

    private fun encodeHeader(
        traceId: String,
        sequence: Long,
        occurredAtMillis: Long,
        previousHash: String
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            writeString(output, traceId, MAX_TRACE_ID_BYTES)
            output.writeLong(sequence)
            output.writeLong(occurredAtMillis)
            writeString(output, previousHash, HASH_HEX_LENGTH)
        }
    }.toByteArray()

    private fun encodeBody(header: ByteArray, encrypted: ByteArray): ByteArray =
        ByteArrayOutputStream(header.size + encrypted.size + 4).also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(header)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
        }.toByteArray()

    private fun encodeEvent(event: KellvynAgentLabEvent): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                writeString(output, event.kind.name, MAX_ENUM_BYTES)
                writeString(output, event.actor, MAX_ACTOR_BYTES)
                writeNullableInt(output, event.step)
                writeNullableString(output, event.specialistRole?.name, MAX_ENUM_BYTES)
                writeNullableString(output, event.toolName, MAX_TOOL_NAME_BYTES)
                writeNullableString(output, event.profileSha256, HASH_HEX_LENGTH)
                writeNullableLong(output, event.durationMillis)
                writeNullableString(output, event.outcome?.name, MAX_ENUM_BYTES)
                writeNullableString(output, event.detailCode, MAX_DETAIL_CODE_BYTES)
            }
        }.toByteArray()

    private fun decodeEvent(
        bytes: ByteArray,
        traceId: String,
        occurredAtMillis: Long
    ): KellvynAgentLabEvent {
        require(bytes.size in 1..MAX_EVENT_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val kind = enumValueOf<KellvynAgentLabEventKind>(readString(input, MAX_ENUM_BYTES))
        val actor = readString(input, MAX_ACTOR_BYTES)
        val step = readNullableInt(input)
        val role = readNullableString(input, MAX_ENUM_BYTES)
            ?.let { enumValueOf<KellvynSpecialistRole>(it) }
        val toolName = readNullableString(input, MAX_TOOL_NAME_BYTES)
        val profileHash = readNullableString(input, HASH_HEX_LENGTH)
        val duration = readNullableLong(input)
        val outcome = readNullableString(input, MAX_ENUM_BYTES)
            ?.let { enumValueOf<KellvynAgentLabOutcome>(it) }
        val detailCode = readNullableString(input, MAX_DETAIL_CODE_BYTES)
        check(input.available() == 0) { "O payload do evento contém dados extras." }
        return KellvynAgentLabEvent(
            traceId = traceId,
            occurredAtMillis = occurredAtMillis,
            kind = kind,
            actor = actor,
            step = step,
            specialistRole = role,
            toolName = toolName,
            profileSha256 = profileHash,
            durationMillis = duration,
            outcome = outcome,
            detailCode = detailCode
        )
    }

    private fun writeString(output: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..maxBytes)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val size = input.readInt()
        require(size in 1..maxBytes)
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun writeNullableString(output: DataOutputStream, value: String?, maxBytes: Int) {
        output.writeBoolean(value != null)
        if (value != null) writeString(output, value, maxBytes)
    }

    private fun readNullableString(input: DataInputStream, maxBytes: Int): String? =
        if (input.readBoolean()) readString(input, maxBytes) else null

    private fun writeNullableInt(output: DataOutputStream, value: Int?) {
        output.writeBoolean(value != null)
        if (value != null) output.writeInt(value)
    }

    private fun readNullableInt(input: DataInputStream): Int? =
        if (input.readBoolean()) input.readInt() else null

    private fun writeNullableLong(output: DataOutputStream, value: Long?) {
        output.writeBoolean(value != null)
        if (value != null) output.writeLong(value)
    }

    private fun readNullableLong(input: DataInputStream): Long? =
        if (input.readBoolean()) input.readLong() else null

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun eventFileName(sequence: Long) = "event-%08d.kvt".format(sequence)

    companion object {
        private const val MAGIC = 0x4B564154 // "KVAT"
        private const val FORMAT_VERSION = 1
        private const val HASH_BYTES = 32
        private const val HASH_HEX_LENGTH = 64
        private const val ZERO_HASH = "0".repeat(HASH_HEX_LENGTH)
        private const val MAX_TRACES = 64
        private const val MAX_EVENTS_PER_TRACE = 128
        private const val MAX_TRACE_ID_BYTES = 36
        private const val MAX_ENUM_BYTES = 64
        private const val MAX_ACTOR_BYTES = 64
        private const val MAX_TOOL_NAME_BYTES = 64
        private const val MAX_DETAIL_CODE_BYTES = 80
        private const val MAX_EVENT_BYTES = 1_024
        private const val MAX_ENCRYPTED_EVENT_BYTES = MAX_EVENT_BYTES + 128
        private const val MAX_RECORD_BYTES = 2_048
        private const val MIN_RECORD_BYTES = 4 + 4 + 4 + MAX_TRACE_ID_BYTES + 8 + 8 + 4 + HASH_HEX_LENGTH + 4 + 16
        private val EVENT_FILE = Regex("event-([0-9]{8})\\.kvt")
        private val HASH_HEX = Regex("[0-9a-f]{64}")
        private val PROCESS_LOCK = Any()
    }
}
