package com.kellvyn.assistant

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

internal data class KellvynArtifactStoreSnapshot(
    val storedBytes: Long,
    val recordCount: Int,
    val maxArtifactBytes: Long,
    val maxStoreBytes: Long
)

/** Small immutable reference returned to the KN; binary bytes remain outside model context. */
internal data class KellvynArtifactRef(
    val id: String,
    val taskId: String,
    val fileName: String,
    val mediaType: String,
    val producerId: String,
    val sha256: String,
    val sizeBytes: Long,
    val createdAtMillis: Long
)

/**
 * App-private, content-addressed artifact storage shared by invisible-studio engines.
 *
 * Objects are deduplicated by SHA-256. Task-scoped metadata records preserve the producing
 * task and engine without copying the binary. Writes stage to the same filesystem, sync, and
 * atomically rename. Reads are hash-verified before a stream is returned or copied.
 */
internal class KellvynArtifactStore(
    rootDirectory: File,
    private val maxArtifactBytes: Long = DEFAULT_MAX_ARTIFACT_BYTES,
    private val maxStoreBytes: Long = DEFAULT_MAX_STORE_BYTES,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val root = rootDirectory.canonicalFile
    private val objectsDirectory = File(root, "objects")
    private val recordsDirectory = File(root, "records")
    private val temporaryDirectory = File(root, "tmp")
    private val lock = Any()

    init {
        require(maxArtifactBytes > 0L)
        require(maxStoreBytes >= maxArtifactBytes)
    }

    /**
     * Stores a stream without closing the caller-owned input. The stream is read once and is
     * never buffered in memory beyond a fixed 64 KiB transfer buffer.
     */
    fun put(
        taskId: String,
        fileName: String,
        mediaType: String,
        producerId: String,
        input: InputStream,
        createdAtMillis: Long = clock()
    ): KellvynArtifactRef = synchronized(lock) {
        validateMetadata(taskId, fileName, mediaType, producerId, createdAtMillis)
        ensureDirectory(objectsDirectory)
        ensureDirectory(recordsDirectory)
        ensureDirectory(temporaryDirectory)

        val staged = File(temporaryDirectory, UUID.randomUUID().toString() + ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            FileOutputStream(staged).use { raw ->
                val output = BufferedOutputStream(raw)
                val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                while (true) {
                    var count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val single = input.read()
                        if (single < 0) break
                        buffer[0] = single.toByte()
                        count = 1
                    }
                    require(size <= maxArtifactBytes - count.toLong()) {
                        "O artefato ultrapassa o limite de $maxArtifactBytes bytes."
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    size += count.toLong()
                }
                require(size > 0L) { "Artefatos vazios não são aceitos." }
                output.flush()
                raw.fd.sync()
            }

            val contentHash = toHex(digest.digest())
            val blob = objectFile(contentHash, createParent = true)
            if (blob.exists()) {
                require(blob.isFile && blob.length() == size && hashFile(blob) == contentHash) {
                    "O objeto existente com o mesmo hash está corrompido."
                }
            } else {
                val usedBytes = storedBytesInternal()
                require(usedBytes <= maxStoreBytes - size) {
                    "O armazenamento privado de artefatos atingiu o limite configurado."
                }
                check(staged.renameTo(blob)) {
                    "Não foi possível publicar atomicamente o objeto do artefato."
                }
            }

            val ref = KellvynArtifactRef(
                id = artifactId(taskId, fileName, mediaType, producerId, contentHash, size, createdAtMillis),
                taskId = taskId,
                fileName = fileName,
                mediaType = mediaType,
                producerId = producerId,
                sha256 = contentHash,
                sizeBytes = size,
                createdAtMillis = createdAtMillis
            )
            writeRecordAtomically(ref)
            ref
        } finally {
            staged.delete()
        }
    }

    /** Reads task-scoped metadata only; binary contents are not loaded. */
    fun get(artifactId: String): KellvynArtifactRef = synchronized(lock) {
        readRecord(recordFile(artifactId))
    }

    /** Lists records for one task; objects are shared when their content hashes match. */
    fun listForTask(taskId: String): List<KellvynArtifactRef> = synchronized(lock) {
        validateTaskId(taskId)
        if (!recordsDirectory.isDirectory) return@synchronized emptyList()
        recordsDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(RECORD_EXTENSION) }
            ?.map { readRecord(it) }
            ?.filter { it.taskId == taskId }
            ?.sortedWith(compareBy<KellvynArtifactRef> { it.createdAtMillis }.thenBy { it.id })
            .orEmpty()
    }

    /** Verifies existence, length and SHA-256 without loading the artifact into memory. */
    fun verify(artifactId: String): Boolean = synchronized(lock) {
        val ref = readRecord(recordFile(artifactId))
        val blob = objectFile(ref.sha256, createParent = false)
        blob.isFile && blob.length() == ref.sizeBytes && hashFile(blob) == ref.sha256
    }

    /**
     * Copies a verified artifact to a caller-owned stream. The caller should use a temporary
     * destination and publish it only after this method returns, because a corrupt source can
     * be detected after bytes have already been written to that stream.
     */
    fun copyVerifiedTo(artifactId: String, output: OutputStream): KellvynArtifactRef =
        synchronized(lock) {
            val ref = readRecord(recordFile(artifactId))
            val blob = objectFile(ref.sha256, createParent = false)
            require(blob.isFile && blob.length() == ref.sizeBytes) {
                "O objeto do artefato está ausente ou tem tamanho divergente."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            FileInputStream(blob).use { raw ->
                val input = BufferedInputStream(raw)
                val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                while (true) {
                    var count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val single = input.read()
                        if (single < 0) break
                        buffer[0] = single.toByte()
                        count = 1
                    }
                    copied += count.toLong()
                    require(copied <= ref.sizeBytes) {
                        "O objeto do artefato excedeu o tamanho declarado."
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            require(copied == ref.sizeBytes && toHex(digest.digest()) == ref.sha256) {
                "A integridade SHA-256 do artefato não confere."
            }
            ref
        }

    /** Counts unique stored binary bytes, excluding task metadata and temporary files. */
    fun storedBytes(): Long = synchronized(lock) { storedBytesInternal() }

    /** A bounded metadata-only snapshot for the KN; no file paths or binary bytes are exposed. */
    fun snapshot(): KellvynArtifactStoreSnapshot = synchronized(lock) {
        val count = if (!recordsDirectory.isDirectory) 0 else {
            recordsDirectory.listFiles()?.count {
                it.isFile && it.name.endsWith(RECORD_EXTENSION)
            } ?: 0
        }
        KellvynArtifactStoreSnapshot(
            storedBytes = storedBytesInternal(),
            recordCount = count,
            maxArtifactBytes = maxArtifactBytes,
            maxStoreBytes = maxStoreBytes
        )
    }

    private fun writeRecordAtomically(ref: KellvynArtifactRef) {
        val destination = recordFile(ref.id)
        if (destination.exists()) {
            require(readRecord(destination) == ref) {
                "O identificador do artefato já existe com metadados diferentes."
            }
            return
        }
        val count = recordsDirectory.listFiles()
            ?.count { it.isFile && it.name.endsWith(RECORD_EXTENSION) } ?: 0
        require(count < MAX_RECORDS) { "O catálogo atingiu o limite de registros de artefatos." }

        val temporary = File(temporaryDirectory, UUID.randomUUID().toString() + ".record.part")
        try {
            FileOutputStream(temporary).use { raw ->
                val data = DataOutputStream(BufferedOutputStream(raw))
                data.writeInt(RECORD_MAGIC)
                data.writeInt(RECORD_VERSION)
                writeString(data, ref.id, HASH_HEX_CHARS)
                writeString(data, ref.taskId, MAX_TASK_ID_BYTES)
                writeString(data, ref.fileName, MAX_FILE_NAME_BYTES)
                writeString(data, ref.mediaType, MAX_MEDIA_TYPE_BYTES)
                writeString(data, ref.producerId, MAX_PRODUCER_ID_BYTES)
                writeString(data, ref.sha256, HASH_HEX_CHARS)
                data.writeLong(ref.sizeBytes)
                data.writeLong(ref.createdAtMillis)
                data.flush()
                raw.fd.sync()
            }
            check(temporary.renameTo(destination)) {
                "Não foi possível publicar atomicamente os metadados do artefato."
            }
        } finally {
            temporary.delete()
        }
    }

    private fun readRecord(file: File): KellvynArtifactRef {
        require(file.isFile && file.length() in 1..MAX_RECORD_BYTES) {
            "Registro do artefato ausente, vazio ou acima do limite."
        }
        val ref = FileInputStream(file).use { raw ->
            val data = DataInputStream(BufferedInputStream(raw))
            require(data.readInt() == RECORD_MAGIC) { "Formato de registro de artefato desconhecido." }
            require(data.readInt() == RECORD_VERSION) { "Versão de registro de artefato não suportada." }
            val id = readString(data, HASH_HEX_CHARS)
            val taskId = readString(data, MAX_TASK_ID_BYTES)
            val fileName = readString(data, MAX_FILE_NAME_BYTES)
            val mediaType = readString(data, MAX_MEDIA_TYPE_BYTES)
            val producerId = readString(data, MAX_PRODUCER_ID_BYTES)
            val sha256 = readString(data, HASH_HEX_CHARS)
            val sizeBytes = data.readLong()
            val createdAtMillis = data.readLong()
            require(data.available() == 0) { "O registro do artefato contém bytes extras." }
            validateMetadata(taskId, fileName, mediaType, producerId, createdAtMillis)
            require(id.matches(HASH_HEX) && sha256.matches(HASH_HEX)) {
                "Hash inválido no registro do artefato."
            }
            require(sizeBytes in 1..maxArtifactBytes) { "Tamanho inválido no registro do artefato." }
            KellvynArtifactRef(
                id, taskId, fileName, mediaType, producerId, sha256, sizeBytes, createdAtMillis
            )
        }
        require(file.name == ref.id + RECORD_EXTENSION) {
            "Nome do registro não corresponde ao identificador do artefato."
        }
        require(ref.id == artifactId(
            ref.taskId, ref.fileName, ref.mediaType, ref.producerId,
            ref.sha256, ref.sizeBytes, ref.createdAtMillis
        )) { "Os metadados do artefato não correspondem ao identificador autenticador." }
        return ref
    }

    private fun recordFile(id: String): File {
        require(id.matches(HASH_HEX)) { "Identificador de artefato inválido." }
        return File(recordsDirectory, id + RECORD_EXTENSION).canonicalFile.also { file ->
            require(file.parentFile == recordsDirectory.canonicalFile) {
                "O registro do artefato saiu da pasta privada."
            }
        }
    }

    private fun objectFile(hash: String, createParent: Boolean): File {
        require(hash.matches(HASH_HEX)) { "Hash de conteúdo inválido." }
        val parent = File(objectsDirectory, hash.take(2))
        if (createParent) ensureDirectory(parent)
        val file = File(parent, hash + OBJECT_EXTENSION).canonicalFile
        require(file.parentFile == parent.canonicalFile) {
            "O objeto do artefato saiu da pasta privada."
        }
        return file
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Não foi possível preparar o armazenamento privado de artefatos.")
        }
        require(directory.isDirectory) { "O caminho de armazenamento de artefatos não é uma pasta." }
        require(directory.canonicalFile.path.startsWith(root.path + File.separator)) {
            "O diretório de artefatos saiu da raiz privada."
        }
    }

    private fun storedBytesInternal(): Long {
        if (!objectsDirectory.isDirectory) return 0L
        var total = 0L
        objectsDirectory.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(OBJECT_EXTENSION)) {
                total = Math.addExact(total, file.length())
            }
        }
        return total
    }

    private fun validateMetadata(
        taskId: String,
        fileName: String,
        mediaType: String,
        producerId: String,
        createdAtMillis: Long
    ) {
        validateTaskId(taskId)
        val fileBytes = fileName.toByteArray(StandardCharsets.UTF_8)
        require(fileName.isNotBlank() && fileName == fileName.trim() &&
            fileBytes.size in 1..MAX_FILE_NAME_BYTES &&
            fileName != "." && fileName != ".." &&
            !fileName.contains("/") && !fileName.contains("\\") && !fileName.contains("|") &&
            fileName.none { it.code < 32 || it.code == 127 }
        ) { "Nome de arquivo do artefato inválido." }
        require(mediaType.matches(MEDIA_TYPE_PATTERN)) { "Tipo MIME do artefato inválido." }
        require(producerId.matches(PRODUCER_ID_PATTERN)) { "Identificador do motor produtor inválido." }
        require(createdAtMillis > 0L) { "Data de criação do artefato inválida." }
    }

    private fun validateTaskId(taskId: String) {
        require(taskId.matches(TASK_ID_PATTERN)) { "Identificador da tarefa inválido." }
    }

    private fun artifactId(
        taskId: String,
        fileName: String,
        mediaType: String,
        producerId: String,
        contentHash: String,
        sizeBytes: Long,
        createdAtMillis: Long
    ): String = sha256(
        listOf(
            taskId, fileName, mediaType, producerId, contentHash,
            sizeBytes.toString(), createdAtMillis.toString()
        ).joinToString("|")
    )

    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { raw ->
            val input = BufferedInputStream(raw)
            val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
            while (true) {
                var count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val single = input.read()
                    if (single < 0) break
                    buffer[0] = single.toByte()
                    count = 1
                }
                digest.update(buffer, 0, count)
            }
        }
        return toHex(digest.digest())
    }

    private fun writeString(data: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..maxBytes)
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readString(data: DataInputStream, maxBytes: Int): String {
        val size = data.readInt()
        require(size in 1..maxBytes) { "Campo textual inválido no registro de artefato." }
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun sha256(value: String): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)))

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val RECORD_MAGIC = 0x4B564152 // KVAR
        const val RECORD_VERSION = 1
        const val RECORD_EXTENSION = ".kar"
        const val OBJECT_EXTENSION = ".blob"
        const val TRANSFER_BUFFER_BYTES = 64 * 1024
        const val HASH_HEX_CHARS = 64
        const val MAX_TASK_ID_BYTES = 96
        const val MAX_FILE_NAME_BYTES = 180
        const val MAX_MEDIA_TYPE_BYTES = 128
        const val MAX_PRODUCER_ID_BYTES = 64
        const val MAX_RECORD_BYTES = 1_024L
        const val MAX_RECORDS = 10_000
        const val DEFAULT_MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L
        const val DEFAULT_MAX_STORE_BYTES = 4L * 1024L * 1024L * 1024L
        val HASH_HEX = Regex("[a-f0-9]{64}")
        val TASK_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")
        val PRODUCER_ID_PATTERN = Regex("[a-z][a-z0-9._-]{0,63}")
        val MEDIA_TYPE_PATTERN = Regex(
            "[A-Za-z0-9][A-Za-z0-9.+-]{0,63}/[A-Za-z0-9][A-Za-z0-9.+-]{0,63}"
        )
    }
}
