package com.kellvyn.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KellvynSpecialistProfileStoreInstrumentedTest {
    @Test
    fun encryptedProfileHistorySurvivesStoreRecreationAndAuthenticatesItsHeader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testId = UUID.randomUUID().toString().replace("-", "")
        val root = File(context.noBackupFilesDir, "profile-store-test-$testId")
        val keyAlias = "kellvyn.test.profile.$testId"
        try {
            assertTrue(root.mkdirs())
            val base = KellvynSpecialistProfileRegistry.default()
                .requireProfile(KellvynSpecialistRole.RESEARCHER)
            val candidate = base.copy(
                version = 2,
                instruction = base.instruction + " Registre data e origem de cada fonte."
            )
            val compiler = KellvynSpecialistProfileCompiler()
            val baseHash = requireNotNull(compiler.compile(base)).profileSha256
            val candidateHash = requireNotNull(compiler.compile(candidate)).profileSha256
            val firstStore = KellvynSpecialistProfileStore(
                root,
                KellvynAndroidTaskCheckpointCipher(keyAlias)
            )
            val staged = firstStore.stage(
                candidate = candidate,
                fallbackActiveProfile = base,
                expectedActiveProfileSha256 = baseHash,
                expectedLedgerSha256 = null,
                actor = "instrumented-test",
                reason = "Provar persistência Android Keystore.",
                createdAtMillis = 100L
            )
            val approved = firstStore.approve(
                role = base.role,
                candidateVersion = candidate.version,
                candidateSha256 = candidateHash,
                fallbackActiveProfile = base,
                expectedActiveProfileSha256 = baseHash,
                expectedLedgerSha256 = staged.recordSha256,
                approver = "owner-test",
                reason = "Aprovação isolada do teste.",
                ownerConfirmed = true,
                createdAtMillis = 200L
            )

            val reopened = KellvynSpecialistProfileStore(
                root,
                KellvynAndroidTaskCheckpointCipher(keyAlias)
            )
            assertEquals(candidate, reopened.activeProfile(base))
            assertEquals(approved.recordSha256, reopened.latestRecordSha256())
            assertEquals(2, reopened.history(base.role).size)

            // Change the authenticated timestamp header but recompute the outer SHA-256.
            // The file checksum remains internally valid; AES-GCM AAD must still reject it.
            val approvedFile = File(
                root,
                "profile-" + approved.sequence.toString().padStart(20, '0') + ".kvp"
            )
            val bytes = approvedFile.readBytes()
            bytes[23] = (bytes[23].toInt() + 1).toByte()
            val body = bytes.copyOfRange(0, bytes.size - 32)
            val digest = MessageDigest.getInstance("SHA-256").digest(body)
            digest.copyInto(bytes, bytes.size - digest.size)
            approvedFile.writeBytes(bytes)

            val authenticationFailure = runCatching {
                reopened.history(base.role)
            }.exceptionOrNull()
            assertNotNull("O Keystore/AES-GCM deveria rejeitar AAD alterado.", authenticationFailure)
        } finally {
            root.deleteRecursively()
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    deleteEntry(keyAlias)
                }
            }
        }
    }
}
