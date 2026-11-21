package com.kellvyn.assistant

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM checkpoint encryption backed by the Android Keystore.
 *
 * The task header is supplied as AEAD associated data, so changing the task ID, sequence,
 * timestamp, or previous hash also invalidates the authentication tag.
 */
internal class KellvynAndroidTaskCheckpointCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : KellvynTaskCheckpointCipher {

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        check(iv.size == GCM_IV_BYTES) { "O Android Keystore retornou um IV GCM inesperado." }

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(ENVELOPE_MAGIC)
            data.writeInt(ENVELOPE_VERSION)
            data.writeInt(iv.size)
            data.write(iv)
            data.writeInt(encrypted.size)
            data.write(encrypted)
        }
        return output.toByteArray()
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        require(ciphertext.size in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES) {
            "Envelope criptográfico inválido."
        }
        val input = DataInputStream(ByteArrayInputStream(ciphertext))
        check(input.readInt() == ENVELOPE_MAGIC) { "Formato criptográfico desconhecido." }
        check(input.readInt() == ENVELOPE_VERSION) { "Versão criptográfica não suportada." }

        val ivLength = input.readInt()
        check(ivLength == GCM_IV_BYTES) { "Comprimento de IV GCM inválido." }
        val iv = ByteArray(ivLength)
        input.readFully(iv)

        val encryptedLength = input.readInt()
        check(encryptedLength in GCM_TAG_BYTES..MAX_ENVELOPE_BYTES) {
            "Conteúdo criptografado inválido."
        }
        val encrypted = ByteArray(encryptedLength)
        input.readFully(encrypted)
        check(input.available() == 0) { "O envelope criptográfico contém dados extras." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "kellvyn.task-checkpoints.aes-gcm.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENVELOPE_MAGIC = 0x4B564345 // "KVCE"
        private const val ENVELOPE_VERSION = 1
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val MIN_ENVELOPE_BYTES = 4 + 4 + 4 + GCM_IV_BYTES + 4 + GCM_TAG_BYTES
        private const val MAX_ENVELOPE_BYTES = 1_048_576 + 128
    }
}
