package com.kellvyn.assistant

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KellvynSpecialistProfileStoreTest {
    private lateinit var root: File
    private lateinit var store: KellvynSpecialistProfileStore
    private val baseline by lazy {
        KellvynSpecialistProfileRegistry.default()
            .requireProfile(KellvynSpecialistRole.RESEARCHER)
    }
    private val compiler = KellvynSpecialistProfileCompiler()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kellvyn-agent-profiles").toFile()
        store = newStore()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun stagingIsEncryptedAuditedAndDoesNotChangeTheActiveProfile() {
        val candidate = revisedProfile(2)
        val staged = stage(candidate)

        assertEquals(KellvynSpecialistProfileEventType.STAGED, staged.eventType)
        assertEquals(candidate.version, staged.profileVersion)
        assertEquals(profileHash(candidate), staged.profileSha256)
        assertEquals(baseline, store.activeProfile(baseline))
        assertEquals(listOf(staged), store.history(baseline.role))
        assertNotNull(store.latestRecordSha256())

        val reopened = newStore()
        assertEquals(baseline, reopened.activeProfile(baseline))
        assertEquals(listOf(staged), reopened.history(baseline.role))
    }

    @Test
    fun approvalRequiresOwnerConfirmationAndExactCandidateHash() {
        val candidate = revisedProfile(2)
        val staged = stage(candidate)

        val denied = runCatching {
            store.approve(
                role = baseline.role,
                candidateVersion = candidate.version,
                candidateSha256 = profileHash(candidate),
                fallbackActiveProfile = baseline,
                expectedActiveProfileSha256 = profileHash(baseline),
                expectedLedgerSha256 = staged.recordSha256,
                approver = "owner",
                reason = "Revisão confirmada pelo proprietário.",
                ownerConfirmed = false,
                createdAtMillis = 200L
            )
        }.exceptionOrNull()
        assertTrue(denied is IllegalArgumentException)
        assertEquals(baseline, store.activeProfile(baseline))

        val wrongHash = runCatching {
            store.approve(
                role = baseline.role,
                candidateVersion = candidate.version,
                candidateSha256 = "0".repeat(64),
                fallbackActiveProfile = baseline,
                expectedActiveProfileSha256 = profileHash(baseline),
                expectedLedgerSha256 = staged.recordSha256,
                approver = "owner",
                reason = "Tentativa com hash incorreto.",
                ownerConfirmed = true,
                createdAtMillis = 201L
            )
        }.exceptionOrNull()
        assertTrue(wrongHash is IllegalStateException)

        val approved = approve(candidate, staged)
        assertEquals(KellvynSpecialistProfileEventType.APPROVED, approved.eventType)
        assertEquals(candidate, store.activeProfile(baseline))
        assertEquals(2, store.history(baseline.role).size)
    }

    @Test
    fun rollbackCanRestoreTheOriginalBuiltInProfileAfterProcessRestart() {
        val candidate = revisedProfile(2)
        val staged = stage(candidate)
        val approved = approve(candidate, staged)
        val reopened = newStore()

        assertEquals(candidate, reopened.activeProfile(baseline))
        val rolledBack = reopened.rollback(
            role = baseline.role,
            targetVersion = baseline.version,
            fallbackActiveProfile = baseline,
            expectedActiveProfileSha256 = profileHash(candidate),
            expectedLedgerSha256 = approved.recordSha256,
            approver = "owner",
            reason = "Regressão observada no teste de qualidade.",
            ownerConfirmed = true,
            createdAtMillis = 300L
        )

        assertEquals(KellvynSpecialistProfileEventType.ROLLED_BACK, rolledBack.eventType)
        assertEquals(baseline, reopened.activeProfile(baseline))
        assertEquals(3, reopened.history(baseline.role).size)
    }

    @Test
    fun compareAndSwapRejectsStaleLedgerOrActiveProfile() {
        val staged = stage(revisedProfile(2))
        val staleLedger = runCatching {
            store.stage(
                candidate = revisedProfile(3),
                fallbackActiveProfile = baseline,
                expectedActiveProfileSha256 = profileHash(baseline),
                expectedLedgerSha256 = null,
                actor = "agent",
                reason = "Tentativa obsoleta.",
                createdAtMillis = 200L
            )
        }.exceptionOrNull()
        assertTrue(staleLedger is IllegalStateException)

        val wrongActive = runCatching {
            store.approve(
                role = baseline.role,
                candidateVersion = 2,
                candidateSha256 = profileHash(revisedProfile(2)),
                fallbackActiveProfile = baseline,
                expectedActiveProfileSha256 = "f".repeat(64),
                expectedLedgerSha256 = staged.recordSha256,
                approver = "owner",
                reason = "Tentativa com perfil-base incorreto.",
                ownerConfirmed = true,
                createdAtMillis = 201L
            )
        }.exceptionOrNull()
        assertTrue(wrongActive is IllegalStateException)
        assertEquals(baseline, store.activeProfile(baseline))
    }

    @Test
    fun onlyPreviouslyApprovedRevisionsCanBeRollbackTargets() {
        val candidate = revisedProfile(2)
        stage(candidate)
        val rejected = runCatching {
            store.rollback(
                role = baseline.role,
                targetVersion = 2,
                fallbackActiveProfile = baseline,
                expectedActiveProfileSha256 = profileHash(baseline),
                expectedLedgerSha256 = store.latestRecordSha256(),
                approver = "owner",
                reason = "Tentativa de reverter para revisão ainda não aprovada.",
                ownerConfirmed = true,
                createdAtMillis = 300L
            )
        }.exceptionOrNull()
        assertTrue(rejected is IllegalStateException)
        assertEquals(baseline, store.activeProfile(baseline))
    }

    @Test
    fun profileHistoryRejectsModifiedOrTruncatedRecords() {
        val staged = stage(revisedProfile(2))
        val file = File(root, recordFileName(staged.sequence))
        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x20).toByte()
        file.writeBytes(bytes)

        val rejected = runCatching { store.history(baseline.role) }.exceptionOrNull()
        assertTrue(rejected is IllegalStateException)
    }

    @Test
    fun compilerAndStoreRejectMutationOrNestedDelegationProfiles() {
        val mutation = runCatching { revisedProfile(2).copy(allowMutation = true) }.exceptionOrNull()
        val delegation = runCatching { revisedProfile(2).copy(allowDelegation = true) }.exceptionOrNull()
        assertTrue(mutation is IllegalArgumentException)
        assertTrue(delegation is IllegalArgumentException)
        assertFalse(store.history(baseline.role).any {
            it.eventType == KellvynSpecialistProfileEventType.STAGED
        })
    }

    @Test
    fun historyAndDiffExposeChangedFieldsWithoutReplacingTheActiveProfile() {
        val candidate = revisedProfile(2)
        assertEquals(
            setOf("versão", "instruções", "limite de ferramentas"),
            store.diff(baseline, candidate).toSet()
        )

        val staged = stage(candidate)
        assertEquals(candidate.version, staged.profileVersion)
        assertEquals(baseline, store.activeProfile(baseline))
        assertEquals(1, store.history(baseline.role).size)
    }

    private fun revisedProfile(version: Int): KellvynSpecialistProfile =
        baseline.copy(
            version = version,
            instruction = baseline.instruction + " Confirme a data de cada fonte consultada.",
            maxToolCalls = 3
        )

    private fun stage(candidate: KellvynSpecialistProfile) = store.stage(
        candidate = candidate,
        fallbackActiveProfile = baseline,
        expectedActiveProfileSha256 = profileHash(baseline),
        expectedLedgerSha256 = store.latestRecordSha256(),
        actor = "agent-creation-studio",
        reason = "Revisão proposta para melhorar a rastreabilidade.",
        createdAtMillis = 100L
    )

    private fun approve(
        candidate: KellvynSpecialistProfile,
        staged: KellvynSpecialistProfileVersionMetadata
    ) = store.approve(
        role = baseline.role,
        candidateVersion = candidate.version,
        candidateSha256 = profileHash(candidate),
        fallbackActiveProfile = baseline,
        expectedActiveProfileSha256 = profileHash(baseline),
        expectedLedgerSha256 = staged.recordSha256,
        approver = "owner",
        reason = "Aprovação explícita após revisar o diff.",
        ownerConfirmed = true,
        createdAtMillis = 200L
    )

    private fun profileHash(profile: KellvynSpecialistProfile): String =
        requireNotNull(compiler.compile(profile)).profileSha256

    private fun newStore() = KellvynSpecialistProfileStore(root, TestProfileStoreCipher())

    private fun recordFileName(sequence: Long) =
        "profile-" + sequence.toString().padStart(20, '0') + ".kvp"
}

private class TestProfileStoreCipher : KellvynTaskCheckpointCipher {
    private val key = javax.crypto.spec.SecretKeySpec(
        ByteArray(32) { index -> (index * 11 + 3).toByte() },
        "AES"
    )

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val iv = ByteArray(12).also(java.security.SecureRandom()::nextBytes)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            key,
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        cipher.updateAAD(associatedData)
        val encrypted = cipher.doFinal(plaintext)
        val output = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(output).use { data ->
            data.writeInt(0x4B565045)
            data.writeInt(1)
            data.writeInt(iv.size)
            data.write(iv)
            data.writeInt(encrypted.size)
            data.write(encrypted)
        }
        return output.toByteArray()
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        val input = java.io.DataInputStream(java.io.ByteArrayInputStream(ciphertext))
        check(input.readInt() == 0x4B565045)
        check(input.readInt() == 1)
        val ivLength = input.readInt()
        check(ivLength == 12)
        val iv = ByteArray(ivLength)
        input.readFully(iv)
        val encryptedLength = input.readInt()
        check(encryptedLength in 16..65_536)
        val encrypted = ByteArray(encryptedLength)
        input.readFully(encrypted)
        check(input.available() == 0)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            key,
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted)
    }
}


class KellvynSpecialistProfileStoreStudioTest {
    @Test
    fun hiddenStudioCanInspectPersistentHistoryAndPendingDiffWithoutPromoting() = runBlocking {
        val root = Files.createTempDirectory("kellvyn-agent-studio-history").toFile()
        try {
            val registry = KellvynSpecialistProfileRegistry.default()
            val base = registry.requireProfile(KellvynSpecialistRole.RESEARCHER)
            val compiler = KellvynSpecialistProfileCompiler()
            val baseHash = requireNotNull(compiler.compile(base)).profileSha256
            val store = KellvynSpecialistProfileStore(root, TestProfileStoreCipher())
            val candidate = base.copy(
                version = 2,
                instruction = base.instruction + " Registre a data da consulta."
            )
            val staged = store.stage(
                candidate = candidate,
                fallbackActiveProfile = base,
                expectedActiveProfileSha256 = baseHash,
                expectedLedgerSha256 = null,
                actor = "agent-creation-studio",
                reason = "Proposta para rastreabilidade.",
                createdAtMillis = 10L
            )
            val studio = KellvynAgentCreationStudio(registry, store)

            val history = studio.tools
                .single { it.spec.name == KellvynAgentCreationStudio.PROFILE_HISTORY_TOOL }
                .execute(mapOf("role" to "RESEARCHER"))
            val diff = studio.tools
                .single { it.spec.name == KellvynAgentCreationStudio.PROFILE_PENDING_DIFF_TOOL }
                .execute(mapOf("role" to "RESEARCHER"))

            assertTrue(history.success)
            assertTrue(history.content.contains("STAGED"))
            assertTrue(history.content.contains(staged.profileSha256))
            assertTrue(diff.success)
            assertTrue(diff.content.contains("instruções"))
            assertTrue(diff.content.contains("ANTES:"))
            assertTrue(diff.content.contains("DEPOIS:"))
            assertTrue(diff.content.contains("continua inativa"))
            assertEquals(base, store.activeProfile(base))
        } finally {
            root.deleteRecursively()
        }
    }
}
