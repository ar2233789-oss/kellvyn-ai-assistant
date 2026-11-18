package com.kellvyn.assistant

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class KellvynSpecialistProfileEventType {
    STAGED,
    APPROVED,
    ROLLED_BACK
}

internal data class KellvynSpecialistProfileVersionMetadata(
    val sequence: Long,
    val role: KellvynSpecialistRole,
    val profileVersion: Int,
    val eventType: KellvynSpecialistProfileEventType,
    val profileSha256: String,
    val baseProfileVersion: Int,
    val baseProfileSha256: String,
    val targetSequence: Long?,
    val actor: String,
    val reason: String,
    val createdAtMillis: Long,
    val previousRecordSha256: String,
    val recordSha256: String
)

internal data class KellvynSpecialistProfileChange(
    val field: String,
    val before: String,
    val after: String
)

internal data class KellvynSpecialistProfileVersionRecord(
    val metadata: KellvynSpecialistProfileVersionMetadata,
    val profile: KellvynSpecialistProfile?
)

/**
 * Encrypted, append-only profile history. Staging never changes the active profile.
 * Approval and rollback are separate owner actions; the central tool policy remains
 * authoritative and profile content can never grant capabilities.
 */
internal class KellvynSpecialistProfileStore(
    private val rootDirectory: File,
    private val cipher: KellvynTaskCheckpointCipher
) {
    private val lock = PROCESS_LOCK
    private val compiler = KellvynSpecialistProfileCompiler()

    fun stage(
        candidate: KellvynSpecialistProfile,
        fallbackActiveProfile: KellvynSpecialistProfile,
        expectedActiveProfileSha256: String,
        expectedLedgerSha256: String?,
        actor: String,
        reason: String,
        createdAtMillis: Long
    ): KellvynSpecialistProfileVersionMetadata = synchronized(lock) {
        validateActorAndReason(actor, reason)
        require(candidate.role == fallbackActiveProfile.role) {
            "O perfil candidato e o perfil ativo pertencem a papéis diferentes."
        }
        val chain = readChain()
        requireLatestLedgerHash(chain, expectedLedgerSha256)
        val active = activeProfileFromChain(fallbackActiveProfile, chain)
        val activeHash = profileHash(active)
        check(activeHash == expectedActiveProfileSha256) {
            "O perfil ativo mudou desde a revisão; recarregue antes de preparar outra versão."
        }
        val state = replayRole(candidate.role, chain.filter { it.metadata.role == candidate.role })
            ?: initialRoleState(fallbackActiveProfile)
        check(state.pendingSequence == null) {
            "Já existe uma revisão pendente para este papel."
        }
        check(candidate.version == state.maxProfileVersion + 1) {
            "A versão candidata deve ser exatamente a próxima versão ainda não utilizada."
        }
        val candidateHash = profileHash(candidate)
        append(
            chain = chain,
            role = candidate.role,
            profileVersion = candidate.version,
            eventType = KellvynSpecialistProfileEventType.STAGED,
            profileSha256 = candidateHash,
            baseProfileVersion = active.version,
            baseProfileSha256 = activeHash,
            targetSequence = null,
            targetProfileSha256 = null,
            profile = candidate,
            actor = actor,
            reason = reason,
            createdAtMillis = createdAtMillis
        )
    }

    fun approve(
        role: KellvynSpecialistRole,
        candidateVersion: Int,
        candidateSha256: String,
        fallbackActiveProfile: KellvynSpecialistProfile,
        expectedActiveProfileSha256: String,
        expectedLedgerSha256: String?,
        approver: String,
        reason: String,
        ownerConfirmed: Boolean,
        createdAtMillis: Long
    ): KellvynSpecialistProfileVersionMetadata = synchronized(lock) {
        require(ownerConfirmed) { "A promoção exige confirmação explícita do proprietário." }
        validateActorAndReason(approver, reason)
        require(fallbackActiveProfile.role == role) { "Perfil-base incompatível com o papel solicitado." }
        val chain = readChain()
        requireLatestLedgerHash(chain, expectedLedgerSha256)
        val active = activeProfileFromChain(fallbackActiveProfile, chain)
        val activeHash = profileHash(active)
        check(activeHash == expectedActiveProfileSha256) {
            "O perfil ativo mudou durante a revisão; a promoção foi recusada."
        }
        val state = replayRole(role, chain.filter { it.metadata.role == role })
            ?: error("Não existe uma revisão preparada para aprovação.")
        val pendingSequence = state.pendingSequence
            ?: error("Não existe uma revisão pendente para este papel.")
        val staged = chain.singleOrNull { it.metadata.sequence == pendingSequence }
            ?: error("A revisão preparada não existe no histórico.")
        check(staged.metadata.profileVersion == candidateVersion) {
            "A versão selecionada não corresponde à revisão pendente."
        }
        check(staged.metadata.profileSha256 == candidateSha256) {
            "O hash aprovado não corresponde ao conteúdo preparado."
        }
        check(staged.metadata.baseProfileSha256 == activeHash &&
            staged.metadata.baseProfileVersion == active.version
        ) { "A revisão foi preparada sobre outra versão ativa e está obsoleta." }

        append(
            chain = chain,
            role = role,
            profileVersion = candidateVersion,
            eventType = KellvynSpecialistProfileEventType.APPROVED,
            profileSha256 = candidateSha256,
            baseProfileVersion = active.version,
            baseProfileSha256 = activeHash,
            targetSequence = pendingSequence,
            targetProfileSha256 = candidateSha256,
            profile = null,
            actor = approver,
            reason = reason,
            createdAtMillis = createdAtMillis
        )
    }

    fun rollback(
        role: KellvynSpecialistRole,
        targetVersion: Int,
        fallbackActiveProfile: KellvynSpecialistProfile,
        expectedActiveProfileSha256: String,
        expectedLedgerSha256: String?,
        approver: String,
        reason: String,
        ownerConfirmed: Boolean,
        createdAtMillis: Long
    ): KellvynSpecialistProfileVersionMetadata = synchronized(lock) {
        require(ownerConfirmed) { "O rollback exige confirmação explícita do proprietário." }
        validateActorAndReason(approver, reason)
        require(fallbackActiveProfile.role == role) { "Perfil-base incompatível com o papel solicitado." }
        val chain = readChain()
        requireLatestLedgerHash(chain, expectedLedgerSha256)
        val active = activeProfileFromChain(fallbackActiveProfile, chain)
        val activeHash = profileHash(active)
        check(activeHash == expectedActiveProfileSha256) {
            "O perfil ativo mudou desde a última leitura; o rollback foi recusado."
        }
        val state = replayRole(role, chain.filter { it.metadata.role == role })
            ?: error("Não há histórico aprovado para este papel.")
        check(state.pendingSequence == null) {
            "Resolva ou descarte a revisão pendente antes de solicitar rollback."
        }
        val targetSequence: Long
        val targetHash: String
        if (targetVersion == state.baselineVersion) {
            targetSequence = 0L
            targetHash = state.baselineSha256
        } else {
            val target = chain.singleOrNull {
                it.metadata.role == role &&
                    it.metadata.eventType == KellvynSpecialistProfileEventType.STAGED &&
                    it.metadata.profileVersion == targetVersion
            } ?: error("A versão-alvo não existe no histórico.")
            check(state.approvedSequences.contains(target.metadata.sequence)) {
                "O rollback só pode apontar para uma versão anteriormente aprovada."
            }
            check(state.activeSequence != target.metadata.sequence) {
                "A versão-alvo já está ativa."
            }
            targetSequence = target.metadata.sequence
            targetHash = target.metadata.profileSha256
        }
        check(state.activeVersion != targetVersion || state.activeSha256 != targetHash) {
            "A versão-alvo já está ativa."
        }
        append(
            chain = chain,
            role = role,
            profileVersion = targetVersion,
            eventType = KellvynSpecialistProfileEventType.ROLLED_BACK,
            profileSha256 = targetHash,
            baseProfileVersion = active.version,
            baseProfileSha256 = activeHash,
            targetSequence = targetSequence,
            targetProfileSha256 = targetHash,
            profile = null,
            actor = approver,
            reason = reason,
            createdAtMillis = createdAtMillis
        )
    }

    fun activeProfile(fallbackActiveProfile: KellvynSpecialistProfile): KellvynSpecialistProfile =
        synchronized(lock) { activeProfileFromChain(fallbackActiveProfile, readChain()) }

    fun activeProfiles(
        fallbackProfiles: List<KellvynSpecialistProfile>
    ): List<KellvynSpecialistProfile> = synchronized(lock) {
        require(fallbackProfiles.map { it.role }.toSet().size == fallbackProfiles.size) {
            "O conjunto de perfis-base contém papéis duplicados."
        }
        val chain = readChain()
        fallbackProfiles.map { activeProfileFromChain(it, chain) }
            .sortedBy { it.role.name }
    }

    fun history(role: KellvynSpecialistRole): List<KellvynSpecialistProfileVersionMetadata> =
        synchronized(lock) {
            readChain().filter { it.metadata.role == role }.map { it.metadata }
        }

    /** Returns the single pending draft, if one exists; it is never considered active. */
    fun pendingProfile(
        role: KellvynSpecialistRole
    ): KellvynSpecialistProfile? = synchronized(lock) {
        val records = readChain().filter { it.metadata.role == role }
        val state = replayRole(role, records) ?: return@synchronized null
        val pending = state.pendingSequence ?: return@synchronized null
        records.singleOrNull { it.metadata.sequence == pending }?.profile
            ?: error("A revisão pendente perdeu seu payload.")
    }

    fun pendingDiff(
        role: KellvynSpecialistRole,
        fallbackActiveProfile: KellvynSpecialistProfile
    ): List<String>? = synchronized(lock) {
        require(fallbackActiveProfile.role == role)
        val chain = readChain()
        val active = activeProfileFromChain(fallbackActiveProfile, chain)
        val records = chain.filter { it.metadata.role == role }
        val state = replayRole(role, records) ?: return@synchronized null
        val pending = state.pendingSequence ?: return@synchronized null
        val candidate = records.singleOrNull { it.metadata.sequence == pending }?.profile
            ?: error("A revisão pendente perdeu seu payload.")
        diff(active, candidate)
    }

    fun pendingDiffDetails(
        role: KellvynSpecialistRole,
        fallbackActiveProfile: KellvynSpecialistProfile
    ): List<KellvynSpecialistProfileChange>? = synchronized(lock) {
        require(fallbackActiveProfile.role == role)
        val chain = readChain()
        val active = activeProfileFromChain(fallbackActiveProfile, chain)
        val roleRecords = chain.filter { it.metadata.role == role }
        val state = replayRole(role, roleRecords) ?: return@synchronized null
        val pendingSequence = state.pendingSequence ?: return@synchronized null
        val candidate = roleRecords.singleOrNull {
            it.metadata.sequence == pendingSequence
        }?.profile ?: error("A revisão pendente perdeu seu payload.")
        diffDetails(active, candidate)
    }

    fun latestRecordSha256(): String? = synchronized(lock) {
        readChain().lastOrNull()?.metadata?.recordSha256
    }

    fun diffDetails(
        before: KellvynSpecialistProfile,
        after: KellvynSpecialistProfile
    ): List<KellvynSpecialistProfileChange> {
        require(before.role == after.role) { "Não é possível comparar papéis diferentes." }
        return buildList {
            fun addChange(field: String, old: String, new: String) {
                if (old != new) {
                    add(
                        KellvynSpecialistProfileChange(
                            field = field,
                            before = old.take(MAX_DIFF_VALUE_CHARS),
                            after = new.take(MAX_DIFF_VALUE_CHARS)
                        )
                    )
                }
            }
            addChange("versão", before.version.toString(), after.version.toString())
            addChange("nome de exibição", before.displayName, after.displayName)
            addChange("missão", before.mission, after.mission)
            addChange("instruções", before.instruction, after.instruction)
            addChange(
                "formato de saída",
                before.requiredOutputSections.joinToString("\\n"),
                after.requiredOutputSections.joinToString("\\n")
            )
            addChange("limite de etapas", before.maxSteps.toString(), after.maxSteps.toString())
            addChange("limite de ferramentas", before.maxToolCalls.toString(), after.maxToolCalls.toString())
            addChange("orçamento de contexto", before.maxContextChars.toString(), after.maxContextChars.toString())
            addChange("delegação", before.allowDelegation.toString(), after.allowDelegation.toString())
            addChange("mutação", before.allowMutation.toString(), after.allowMutation.toString())
        }
    }

    fun diff(
        before: KellvynSpecialistProfile,
        after: KellvynSpecialistProfile
    ): List<String> {
        require(before.role == after.role) { "Não é possível comparar papéis diferentes." }
        return buildList {
            if (before.version != after.version) add("versão")
            if (before.displayName != after.displayName) add("nome de exibição")
            if (before.mission != after.mission) add("missão")
            if (before.instruction != after.instruction) add("instruções")
            if (before.requiredOutputSections != after.requiredOutputSections) add("formato de saída")
            if (before.maxSteps != after.maxSteps) add("limite de etapas")
            if (before.maxToolCalls != after.maxToolCalls) add("limite de ferramentas")
            if (before.maxContextChars != after.maxContextChars) add("orçamento de contexto")
            if (before.allowDelegation != after.allowDelegation) add("delegação")
            if (before.allowMutation != after.allowMutation) add("mutação")
        }
    }

    private data class RoleState(
        var activeVersion: Int,
        var activeSha256: String,
        var activeSequence: Long?,
        val baselineVersion: Int,
        val baselineSha256: String,
        var maxProfileVersion: Int,
        var pendingSequence: Long?,
        val approvedSequences: MutableSet<Long>
    )

    private fun initialRoleState(profile: KellvynSpecialistProfile): RoleState =
        RoleState(
            activeVersion = profile.version,
            activeSha256 = profileHash(profile),
            activeSequence = null,
            baselineVersion = profile.version,
            baselineSha256 = profileHash(profile),
            maxProfileVersion = profile.version,
            pendingSequence = null,
            approvedSequences = mutableSetOf()
        )

    private fun replayRole(
        role: KellvynSpecialistRole,
        records: List<KellvynSpecialistProfileVersionRecord>
    ): RoleState? {
        if (records.isEmpty()) return null
        val first = records.first()
        check(first.metadata.eventType == KellvynSpecialistProfileEventType.STAGED) {
            "O histórico começa sem uma revisão preparada."
        }
        val state = RoleState(
            activeVersion = first.metadata.baseProfileVersion,
            activeSha256 = first.metadata.baseProfileSha256,
            activeSequence = null,
            baselineVersion = first.metadata.baseProfileVersion,
            baselineSha256 = first.metadata.baseProfileSha256,
            maxProfileVersion = first.metadata.baseProfileVersion,
            pendingSequence = null,
            approvedSequences = mutableSetOf()
        )
        for (record in records) {
            val metadata = record.metadata
            check(metadata.role == role)
            check(metadata.baseProfileVersion == state.activeVersion &&
                metadata.baseProfileSha256 == state.activeSha256
            ) { "A cadeia de versões foi baseada em um perfil ativo diferente." }
            when (metadata.eventType) {
                KellvynSpecialistProfileEventType.STAGED -> {
                    check(state.pendingSequence == null) { "Há revisões pendentes sobrepostas." }
                    val profile = requireNotNull(record.profile)
                    check(profile.role == role && profile.version == metadata.profileVersion)
                    check(metadata.profileVersion == state.maxProfileVersion + 1) {
                        "A sequência de versões contém reutilização ou salto."
                    }
                    check(profileHash(profile) == metadata.profileSha256)
                    state.pendingSequence = metadata.sequence
                    state.maxProfileVersion = metadata.profileVersion
                }
                KellvynSpecialistProfileEventType.APPROVED -> {
                    val targetSequence = requireNotNull(metadata.targetSequence)
                    check(state.pendingSequence == targetSequence) {
                        "A aprovação não corresponde à revisão pendente."
                    }
                    val target = records.singleOrNull { it.metadata.sequence == targetSequence }
                        ?: error("A aprovação aponta para revisão inexistente.")
                    check(target.metadata.profileSha256 == metadata.profileSha256)
                    check(target.metadata.profileVersion == metadata.profileVersion)
                    check(target.metadata.baseProfileSha256 == state.activeSha256)
                    state.activeSequence = targetSequence
                    state.activeVersion = target.metadata.profileVersion
                    state.activeSha256 = target.metadata.profileSha256
                    state.approvedSequences += targetSequence
                    state.pendingSequence = null
                }
                KellvynSpecialistProfileEventType.ROLLED_BACK -> {
                    check(state.pendingSequence == null) {
                        "Não é permitido reverter enquanto há revisão pendente."
                    }
                    val targetSequence = requireNotNull(metadata.targetSequence)
                    check(state.activeSequence != targetSequence) {
                        "O histórico tenta reativar uma versão que já está ativa."
                    }
                    if (targetSequence == 0L) {
                        check(metadata.profileVersion == state.baselineVersion)
                        check(metadata.profileSha256 == state.baselineSha256)
                        state.activeSequence = null
                        state.activeVersion = state.baselineVersion
                        state.activeSha256 = state.baselineSha256
                    } else {
                        check(state.approvedSequences.contains(targetSequence)) {
                            "O rollback aponta para uma versão que nunca foi aprovada."
                        }
                        val target = records.singleOrNull { it.metadata.sequence == targetSequence }
                            ?: error("O rollback aponta para revisão inexistente.")
                        check(target.metadata.profileSha256 == metadata.profileSha256)
                        check(target.metadata.profileVersion == metadata.profileVersion)
                        state.activeSequence = targetSequence
                        state.activeVersion = target.metadata.profileVersion
                        state.activeSha256 = target.metadata.profileSha256
                    }
                }
            }
        }
        return state
    }

    private fun activeProfileFromChain(
        fallback: KellvynSpecialistProfile,
        chain: List<KellvynSpecialistProfileVersionRecord>
    ): KellvynSpecialistProfile {
        val roleRecords = chain.filter { it.metadata.role == fallback.role }
        val state = replayRole(fallback.role, roleRecords) ?: return fallback
        check(state.activeSequence == null || state.activeSequence in state.approvedSequences) {
            "O perfil ativo aponta para uma revisão sem aprovação."
        }
        if (state.activeSequence == null) {
            check(profileHash(fallback) == state.activeSha256 && fallback.version == state.activeVersion) {
                "O perfil-base mudou; o histórico exige reconciliação explícita."
            }
            return fallback
        }
        val active = roleRecords.singleOrNull {
            it.metadata.sequence == state.activeSequence &&
                it.metadata.eventType == KellvynSpecialistProfileEventType.STAGED
        }?.profile ?: error("A versão ativa não contém o perfil original.")
        check(profileHash(active) == state.activeSha256)
        return active
    }

    private fun append(
        chain: List<KellvynSpecialistProfileVersionRecord>,
        role: KellvynSpecialistRole,
        profileVersion: Int,
        eventType: KellvynSpecialistProfileEventType,
        profileSha256: String,
        baseProfileVersion: Int,
        baseProfileSha256: String,
        targetSequence: Long?,
        targetProfileSha256: String?,
        profile: KellvynSpecialistProfile?,
        actor: String,
        reason: String,
        createdAtMillis: Long
    ): KellvynSpecialistProfileVersionMetadata {
        require(createdAtMillis > 0L)
        val sequence = (chain.lastOrNull()?.metadata?.sequence ?: 0L) + 1L
        check(sequence <= MAX_RECORDS.toLong()) { "O histórico de perfis atingiu o limite de registros." }
        val previousHash = chain.lastOrNull()?.metadata?.recordSha256 ?: ZERO_HASH
        val header = encodeHeader(
            sequence, createdAtMillis, role, profileVersion, eventType, previousHash
        )
        val payload = encodePayload(
            actor, reason, baseProfileVersion, baseProfileSha256,
            targetSequence, targetProfileSha256, profile
        )
        val encrypted = cipher.encrypt(payload, header)
        check(encrypted.size in 1..MAX_RECORD_BYTES) { "O registro criptografado excedeu o limite." }
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { output ->
            output.write(header)
            output.writeInt(encrypted.size)
            output.write(encrypted)
        }
        val bodyBytes = body.toByteArray()
        val digest = sha256(bodyBytes)
        val recordBytes = bodyBytes + digest
        val directory = recordsDirectory(create = true)
        val file = recordFile(directory, sequence)
        val temporary = File(directory, file.name + ".tmp")
        check(!file.exists()) { "O destino do registro de perfil já existe." }
        if (temporary.exists()) check(temporary.delete()) { "Não foi possível limpar o registro temporário." }
        FileOutputStream(temporary).use { output ->
            output.write(recordBytes)
            output.fd.sync()
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Não foi possível confirmar atomicamente a nova versão de perfil.")
        }
        return KellvynSpecialistProfileVersionMetadata(
            sequence = sequence,
            role = role,
            profileVersion = profileVersion,
            eventType = eventType,
            profileSha256 = profileSha256,
            baseProfileVersion = baseProfileVersion,
            baseProfileSha256 = baseProfileSha256,
            targetSequence = targetSequence,
            actor = actor,
            reason = reason,
            createdAtMillis = createdAtMillis,
            previousRecordSha256 = previousHash,
            recordSha256 = toHex(digest)
        )
    }

    private fun readChain(): List<KellvynSpecialistProfileVersionRecord> {
        val directory = recordsDirectory(create = false) ?: return emptyList()
        val files = directory.listFiles()
            ?.filter { it.isFile && RECORD_FILE.matches(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
        check(files.size <= MAX_RECORDS) { "O histórico contém registros acima do limite." }
        val records = mutableListOf<KellvynSpecialistProfileVersionRecord>()
        var expectedSequence = 1L
        var expectedPreviousHash = ZERO_HASH
        for (file in files) {
            check(file.name == recordFileName(expectedSequence)) {
                "A sequência do histórico de perfis está incompleta."
            }
            val record = readRecord(file)
            check(record.metadata.sequence == expectedSequence)
            check(record.metadata.previousRecordSha256 == expectedPreviousHash) {
                "A cadeia SHA-256 do histórico de perfis foi interrompida."
            }
            records += record
            expectedPreviousHash = record.metadata.recordSha256
            expectedSequence++
        }
        for (role in records.map { it.metadata.role }.toSet()) {
            replayRole(role, records.filter { it.metadata.role == role })
        }
        return records
    }

    private fun readRecord(file: File): KellvynSpecialistProfileVersionRecord {
        check(file.length() in (HASH_BYTES + MIN_RECORD_BYTES).toLong()..MAX_FILE_BYTES.toLong()) {
            "Registro de perfil truncado ou acima do limite."
        }
        val allBytes = file.readBytes()
        val body = allBytes.copyOfRange(0, allBytes.size - HASH_BYTES)
        val storedHash = allBytes.copyOfRange(allBytes.size - HASH_BYTES, allBytes.size)
        val digest = sha256(body)
        check(MessageDigest.isEqual(storedHash, digest)) { "A integridade SHA-256 do registro não confere." }
        val input = DataInputStream(ByteArrayInputStream(body))
        check(input.readInt() == MAGIC) { "Formato de perfil desconhecido." }
        check(input.readInt() == FORMAT_VERSION) { "Versão do armazenamento de perfis não suportada." }
        val sequence = input.readLong()
        val createdAtMillis = input.readLong()
        val role = readEnum<KellvynSpecialistRole>(input, ROLE_NAMES)
        val profileVersion = input.readInt()
        val eventType = readEnum<KellvynSpecialistProfileEventType>(input, EVENT_NAMES)
        val previousHash = readString(input, HASH_LENGTH)
        val headerLength = body.size - input.available()
        val header = body.copyOfRange(0, headerLength)
        val encryptedLength = input.readInt()
        check(encryptedLength in 1..MAX_RECORD_BYTES)
        val encrypted = ByteArray(encryptedLength)
        input.readFully(encrypted)
        check(input.available() == 0) { "O registro contém dados extras." }
        check(sequence > 0L && createdAtMillis > 0L && profileVersion > 0)
        check(HASH_PATTERN.matches(previousHash))
        val payload = cipher.decrypt(encrypted, header)
        val decoded = decodePayload(payload)
        val profileHash = decoded.profile?.let { profileHash(it) } ?: decoded.targetProfileSha256
            ?: error("O registro não contém hash de perfil.")
        if (eventType == KellvynSpecialistProfileEventType.STAGED) {
            check(decoded.profile != null && decoded.targetSequence == null && decoded.targetProfileSha256 == null)
            check(decoded.profile.role == role && decoded.profile.version == profileVersion)
        } else {
            check(decoded.profile == null && decoded.targetSequence != null)
            if (eventType == KellvynSpecialistProfileEventType.APPROVED) {
                check(decoded.targetSequence > 0L)
            }
            check(decoded.targetProfileSha256 == profileHash)
        }
        check(HASH_PATTERN.matches(decoded.baseProfileSha256))
        check(HASH_PATTERN.matches(profileHash))
        val recordMetadata = KellvynSpecialistProfileVersionMetadata(
            sequence = sequence,
            role = role,
            profileVersion = profileVersion,
            eventType = eventType,
            profileSha256 = profileHash,
            baseProfileVersion = decoded.baseProfileVersion,
            baseProfileSha256 = decoded.baseProfileSha256,
            targetSequence = decoded.targetSequence,
            actor = decoded.actor,
            reason = decoded.reason,
            createdAtMillis = createdAtMillis,
            previousRecordSha256 = previousHash,
            recordSha256 = toHex(digest)
        )
        return KellvynSpecialistProfileVersionRecord(recordMetadata, decoded.profile)
    }

    private data class DecodedPayload(
        val actor: String,
        val reason: String,
        val baseProfileVersion: Int,
        val baseProfileSha256: String,
        val targetSequence: Long?,
        val targetProfileSha256: String?,
        val profile: KellvynSpecialistProfile?
    )

    private fun encodeHeader(
        sequence: Long,
        createdAtMillis: Long,
        role: KellvynSpecialistRole,
        profileVersion: Int,
        eventType: KellvynSpecialistProfileEventType,
        previousHash: String
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeLong(sequence)
            output.writeLong(createdAtMillis)
            writeString(output, role.name, MAX_ENUM_BYTES)
            output.writeInt(profileVersion)
            writeString(output, eventType.name, MAX_ENUM_BYTES)
            writeString(output, previousHash, HASH_LENGTH)
        }
        return bytes.toByteArray()
    }

    private fun encodePayload(
        actor: String,
        reason: String,
        baseProfileVersion: Int,
        baseProfileSha256: String,
        targetSequence: Long?,
        targetProfileSha256: String?,
        profile: KellvynSpecialistProfile?
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            writeString(output, actor, MAX_ACTOR_BYTES)
            writeString(output, reason, MAX_REASON_BYTES)
            output.writeInt(baseProfileVersion)
            writeString(output, baseProfileSha256, HASH_LENGTH)
            output.writeLong(targetSequence ?: -1L)
            writeNullableString(output, targetProfileSha256, HASH_LENGTH)
            output.writeBoolean(profile != null)
            profile?.let { encodeProfile(output, it) }
        }
        return bytes.toByteArray()
    }

    private fun decodePayload(bytes: ByteArray): DecodedPayload {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val actor = readString(input, MAX_ACTOR_BYTES)
        val reason = readString(input, MAX_REASON_BYTES)
        val baseVersion = input.readInt()
        val baseSha = readString(input, HASH_LENGTH)
        val targetSequence = input.readLong().takeIf { it >= 0L }
        val targetSha = readNullableString(input, HASH_LENGTH)
        val profile = if (input.readBoolean()) decodeProfile(input) else null
        check(input.available() == 0) { "O payload do perfil contém dados extras." }
        check(baseVersion > 0 && actor.isNotBlank() && reason.isNotBlank())
        return DecodedPayload(actor, reason, baseVersion, baseSha, targetSequence, targetSha, profile)
    }

    private fun encodeProfile(output: DataOutputStream, profile: KellvynSpecialistProfile) {
        writeString(output, profile.role.name, MAX_ENUM_BYTES)
        output.writeInt(profile.version)
        writeString(output, profile.displayName, MAX_DISPLAY_NAME_BYTES)
        writeString(output, profile.mission, MAX_MISSION_BYTES)
        writeString(output, profile.instruction, MAX_INSTRUCTION_BYTES)
        output.writeInt(profile.requiredOutputSections.size)
        profile.requiredOutputSections.forEach { writeString(output, it, MAX_SECTION_BYTES) }
        output.writeInt(profile.maxSteps)
        output.writeInt(profile.maxToolCalls)
        output.writeInt(profile.maxContextChars)
        output.writeBoolean(profile.allowDelegation)
        output.writeBoolean(profile.allowMutation)
    }

    private fun decodeProfile(input: DataInputStream): KellvynSpecialistProfile {
        val role = readEnum<KellvynSpecialistRole>(input, ROLE_NAMES)
        val version = input.readInt()
        val displayName = readString(input, MAX_DISPLAY_NAME_BYTES)
        val mission = readString(input, MAX_MISSION_BYTES)
        val instruction = readString(input, MAX_INSTRUCTION_BYTES)
        val sectionCount = input.readInt()
        check(sectionCount in 3..8) { "Quantidade de seções do perfil inválida." }
        val sections = List(sectionCount) { readString(input, MAX_SECTION_BYTES) }
        return KellvynSpecialistProfile(
            role = role,
            version = version,
            displayName = displayName,
            mission = mission,
            instruction = instruction,
            requiredOutputSections = sections,
            maxSteps = input.readInt(),
            maxToolCalls = input.readInt(),
            maxContextChars = input.readInt(),
            allowDelegation = input.readBoolean(),
            allowMutation = input.readBoolean()
        )
    }

    private fun recordsDirectory(create: Boolean): File? {
        if (!rootDirectory.exists() && !create) return null
        if (create) {
            check(rootDirectory.exists() || rootDirectory.mkdirs()) {
                "Não foi possível criar a pasta privada do histórico de perfis."
            }
        }
        check(rootDirectory.isDirectory) { "A raiz do histórico de perfis não é uma pasta." }
        val directory = rootDirectory.canonicalFile
        check(directory == rootDirectory.absoluteFile.canonicalFile)
        return directory
    }

    private fun recordFile(directory: File, sequence: Long): File {
        val file = File(directory, recordFileName(sequence))
        check(file.canonicalFile.parentFile == directory) { "O registro saiu da pasta privada." }
        return file
    }

    private fun recordFileName(sequence: Long): String =
        "profile-" + sequence.toString().padStart(20, '0') + ".kvp"

    private fun requireLatestLedgerHash(
        chain: List<KellvynSpecialistProfileVersionRecord>,
        expected: String?
    ) {
        val actual = chain.lastOrNull()?.metadata?.recordSha256
        check(actual == expected) {
            "O histórico de perfis mudou desde a leitura; recarregue antes de gravar."
        }
    }

    private fun profileHash(profile: KellvynSpecialistProfile): String =
        compiler.compile(profile)?.profileSha256
            ?: error("O perfil não passou pelo Profile Compiler.")

    private fun validateActorAndReason(actor: String, reason: String) {
        require(actor.isNotBlank() && actor.length <= MAX_ACTOR_BYTES)
        require(reason.isNotBlank() && reason.length <= MAX_REASON_BYTES)
    }

    private fun writeString(output: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maxBytes) { "Campo de perfil excedeu o limite." }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maxBytes: Int): String {
        val size = input.readInt()
        check(size in 0..maxBytes) { "Campo de perfil possui tamanho inválido." }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun writeNullableString(output: DataOutputStream, value: String?, maxBytes: Int) {
        output.writeBoolean(value != null)
        value?.let { writeString(output, it, maxBytes) }
    }

    private fun readNullableString(input: DataInputStream, maxBytes: Int): String? =
        if (input.readBoolean()) readString(input, maxBytes) else null

    private inline fun <reified T : Enum<T>> readEnum(
        input: DataInputStream,
        allowed: Set<String>
    ): T {
        val name = readString(input, MAX_ENUM_BYTES)
        check(name in allowed) { "Enum de perfil desconhecido." }
        return enumValueOf<T>(name)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    companion object {
        private val PROCESS_LOCK = Any()
        private const val MAGIC = 0x4B565053 // "KVPS"
        private const val FORMAT_VERSION = 1
        private const val HASH_BYTES = 32
        private const val HASH_LENGTH = 64
        private const val MAX_DIFF_VALUE_CHARS = 1_200
        private const val MAX_RECORDS = 256
        private const val MAX_RECORD_BYTES = 32 * 1024
        private const val MAX_FILE_BYTES = MAX_RECORD_BYTES + 4 * 1024
        private const val MIN_RECORD_BYTES = 4 + 4 + 8 + 8 + 4 + 1 + 4 + 4 + 4
        private const val MAX_ENUM_BYTES = 64
        private const val MAX_ACTOR_BYTES = 96
        private const val MAX_REASON_BYTES = 2_000
        private const val MAX_DISPLAY_NAME_BYTES = 320
        private const val MAX_MISSION_BYTES = 2_000
        private const val MAX_INSTRUCTION_BYTES = 8_000
        private const val MAX_SECTION_BYTES = 320
        private const val ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
        private val HASH_PATTERN = Regex("[0-9a-f]{64}")
        private val RECORD_FILE = Regex("profile-[0-9]{20}\\.kvp")
        private val ROLE_NAMES = KellvynSpecialistRole.values().map { it.name }.toSet()
        private val EVENT_NAMES = KellvynSpecialistProfileEventType.values().map { it.name }.toSet()
    }
}
