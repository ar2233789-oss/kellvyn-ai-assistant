package com.kellvyn.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local-only memory storage.
 *
 * Content is authenticated-encrypted with an AES-GCM key protected by Android Keystore.
 * Scope metadata is stored as a one-way hash so project/conversation names are not kept in
 * plaintext in the SQLite index. All database work runs on Dispatchers.IO.
 */
internal class KellvynEncryptedMemoryRepository(context: Context) : KellvynMemoryRepository {
    private val helper = MemoryDatabase(context.applicationContext)
    private val crypto = KellvynMemoryCrypto()
    private val lock = Any()

    override suspend fun put(entry: KellvynMemoryEntry) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val values = ContentValues().apply {
                    put(COL_ID, entry.id)
                    put(COL_SCOPE_HASH, scopeHash(entry.address))
                    put(COL_CREATED, entry.createdAtMillis)
                    if (entry.expiresAtMillis == null) putNull(COL_EXPIRES)
                    else put(COL_EXPIRES, entry.expiresAtMillis)
                    put(COL_PAYLOAD, crypto.encrypt(encode(entry)))
                }
                helper.writableDatabase.insertWithOnConflict(
                    TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                ).also { rowId ->
                    check(rowId != -1L) { "Não foi possível gravar a memória local." }
                }
            }
        }
    }

    override suspend fun loadCandidates(query: KellvynMemoryQuery): List<KellvynMemoryEntry> =
        withContext(Dispatchers.IO) {
            require(query.addresses.isNotEmpty() && query.addresses.size <= 8)
            require(query.nowMillis > 0L)
            synchronized(lock) {
                val hashes = query.addresses.distinct().map(::scopeHash)
                val placeholders = hashes.joinToString(",") { "?" }
                val selection = "$COL_SCOPE_HASH IN ($placeholders) AND " +
                    "($COL_EXPIRES IS NULL OR $COL_EXPIRES > ?)"
                val args = (hashes + query.nowMillis.toString()).toTypedArray()
                val result = mutableListOf<KellvynMemoryEntry>()

                helper.readableDatabase.query(
                    TABLE,
                    arrayOf(COL_ID, COL_SCOPE_HASH, COL_CREATED, COL_EXPIRES, COL_PAYLOAD),
                    selection,
                    args,
                    null,
                    null,
                    "$COL_CREATED DESC",
                    MAX_CANDIDATES.toString()
                ).use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(COL_ID)
                    val scopeIndex = cursor.getColumnIndexOrThrow(COL_SCOPE_HASH)
                    val createdIndex = cursor.getColumnIndexOrThrow(COL_CREATED)
                    val expiryIndex = cursor.getColumnIndexOrThrow(COL_EXPIRES)
                    val payloadIndex = cursor.getColumnIndexOrThrow(COL_PAYLOAD)

                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex)
                        val storedHash = cursor.getString(scopeIndex)
                        val createdAt = cursor.getLong(createdIndex)
                        val expiresAt = if (cursor.isNull(expiryIndex)) null else cursor.getLong(expiryIndex)
                        val entry = decode(crypto.decrypt(cursor.getBlob(payloadIndex)))

                        check(entry.id == id) { "O identificador da memória não corresponde ao conteúdo autenticado." }
                        check(entry.createdAtMillis == createdAt) { "A data da memória não corresponde ao conteúdo autenticado." }
                        check(entry.expiresAtMillis == expiresAt) { "A expiração da memória não corresponde ao conteúdo autenticado." }
                        check(scopeHash(entry.address) == storedHash) { "O escopo da memória não corresponde ao conteúdo autenticado." }
                        result += entry
                    }
                }
                result
            }
        }

    override suspend fun delete(id: String, address: KellvynMemoryAddress): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                helper.writableDatabase.delete(
                    TABLE,
                    "$COL_ID = ? AND $COL_SCOPE_HASH = ?",
                    arrayOf(id, scopeHash(address))
                ) > 0
            }
        }

    override suspend fun deleteScope(address: KellvynMemoryAddress): Int =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                helper.writableDatabase.delete(
                    TABLE,
                    "$COL_SCOPE_HASH = ?",
                    arrayOf(scopeHash(address))
                )
            }
        }

    override suspend fun purgeExpired(nowMillis: Long): Int =
        withContext(Dispatchers.IO) {
            require(nowMillis > 0L)
            synchronized(lock) {
                helper.writableDatabase.delete(
                    TABLE,
                    "$COL_EXPIRES IS NOT NULL AND $COL_EXPIRES <= ?",
                    arrayOf(nowMillis.toString())
                )
            }
        }

    fun close() {
        synchronized(lock) { helper.close() }
    }

    private fun scopeHash(address: KellvynMemoryAddress): String {
        val raw = address.scope.name + "\u0000" + address.scopeId
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun encode(entry: KellvynMemoryEntry): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(PAYLOAD_VERSION)
            output.writeUTF(entry.id)
            output.writeUTF(entry.address.scope.name)
            output.writeUTF(entry.address.scopeId)
            output.writeUTF(entry.kind.name)
            output.writeUTF(entry.title)
            output.writeUTF(entry.content)
            output.writeInt(entry.tags.size)
            entry.tags.forEach(output::writeUTF)
            output.writeUTF(entry.source.name)
            output.writeUTF(entry.provenance)
            output.writeDouble(entry.confidence)
            output.writeLong(entry.createdAtMillis)
            output.writeBoolean(entry.expiresAtMillis != null)
            entry.expiresAtMillis?.let(output::writeLong)
            output.writeBoolean(entry.userApproved)
        }
        return bytes.toByteArray()
    }

    private fun decode(bytes: ByteArray): KellvynMemoryEntry {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == PAYLOAD_VERSION) { "Versão do registro de memória não suportada." }
            val id = input.readUTF()
            val scope = enumValueOf<KellvynMemoryScope>(input.readUTF())
            val scopeId = input.readUTF()
            val kind = enumValueOf<KellvynMemoryKind>(input.readUTF())
            val title = input.readUTF()
            val content = input.readUTF()
            val tagCount = input.readInt()
            require(tagCount in 0..12) { "Quantidade de tags inválida no registro local." }
            val tags = List(tagCount) { input.readUTF() }
            val source = enumValueOf<KellvynMemorySource>(input.readUTF())
            val provenance = input.readUTF()
            val confidence = input.readDouble()
            val createdAtMillis = input.readLong()
            val expiresAtMillis = if (input.readBoolean()) input.readLong() else null
            val approved = input.readBoolean()
            require(input.available() == 0) { "O registro de memória contém dados extras." }

            return KellvynMemoryEntry(
                id = id,
                address = KellvynMemoryAddress(scope, scopeId),
                kind = kind,
                title = title,
                content = content,
                tags = tags,
                source = source,
                provenance = provenance,
                confidence = confidence,
                createdAtMillis = createdAtMillis,
                expiresAtMillis = expiresAtMillis,
                userApproved = approved
            )
        }
    }

    private class MemoryDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_SCOPE_HASH TEXT NOT NULL,
                    $COL_CREATED INTEGER NOT NULL,
                    $COL_EXPIRES INTEGER,
                    $COL_PAYLOAD BLOB NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX memory_scope_created_idx ON $TABLE ($COL_SCOPE_HASH, $COL_CREATED DESC)"
            )
            db.execSQL(
                "CREATE INDEX memory_expiry_idx ON $TABLE ($COL_EXPIRES)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw IllegalStateException(
                "Migração de memória não implementada: $oldVersion -> $newVersion. " +
                    "Os dados foram preservados; não será feito upgrade destrutivo."
            )
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw IllegalStateException("Downgrade destrutivo do banco de memória foi bloqueado.")
        }

        companion object {
            private const val DATABASE_NAME = "kellvyn_memory.db"
            private const val DATABASE_VERSION = 1
        }
    }

    private class KellvynMemoryCrypto {
        fun encrypt(plainText: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            require(iv.size == GCM_IV_BYTES) { "Tamanho de IV inesperado para AES-GCM." }
            val encrypted = cipher.doFinal(plainText)
            return ByteBuffer.allocate(MAGIC.size + iv.size + encrypted.size)
                .put(MAGIC)
                .put(iv)
                .put(encrypted)
                .array()
        }

        fun decrypt(envelope: ByteArray): ByteArray {
            require(envelope.size >= MAGIC.size + GCM_IV_BYTES + GCM_TAG_BYTES) {
                "Registro criptografado truncado."
            }
            require(MAGIC.indices.all { envelope[it] == MAGIC[it] }) {
                "Assinatura do registro criptografado inválida."
            }
            val ivStart = MAGIC.size
            val ivEnd = ivStart + GCM_IV_BYTES
            val iv = envelope.copyOfRange(ivStart, ivEnd)
            val encrypted = envelope.copyOfRange(ivEnd, envelope.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(encrypted)
        }

        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null)
            if (existing != null) {
                return existing as? SecretKey
                    ?: throw IllegalStateException("A chave local da memória não é uma chave AES.")
            }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            generator.init(spec)
            return generator.generateKey()
        }

        companion object {
            private const val ANDROID_KEYSTORE = "AndroidKeyStore"
            private const val KEY_ALIAS = "com.kellvyn.assistant.memory.aesgcm.v1"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val GCM_IV_BYTES = 12
            private const val GCM_TAG_BITS = 128
            private const val GCM_TAG_BYTES = 16
            private val MAGIC = byteArrayOf(0x4B, 0x4C, 0x4D, 0x01)
        }
    }

    companion object {
        private const val TABLE = "memory_entries"
        private const val COL_ID = "id"
        private const val COL_SCOPE_HASH = "scope_hash"
        private const val COL_CREATED = "created_at"
        private const val COL_EXPIRES = "expires_at"
        private const val COL_PAYLOAD = "payload"
        private const val PAYLOAD_VERSION = 1
        private const val MAX_CANDIDATES = 500
    }
}
