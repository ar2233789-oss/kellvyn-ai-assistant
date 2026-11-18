package com.kellvyn.assistant

import java.util.UUID

internal enum class KellvynAgentLabEventKind {
    RUN_STARTED,
    MODEL_CALL_STARTED,
    MODEL_DECISION,
    MODEL_CALL_FAILED,
    DELEGATION_STARTED,
    DELEGATION_FINISHED,
    TOOL_STARTED,
    TOOL_FINISHED,
    RUN_COMPLETED,
    RUN_FAILED,
    RUN_LIMIT_REACHED
}

internal enum class KellvynAgentLabOutcome {
    STARTED,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    LIMITED
}

/**
 * Privacy-preserving event. It deliberately has no prompt, completion, tool arguments,
 * memory contents, file contents, credentials or raw model output fields.
 */
internal data class KellvynAgentLabEvent(
    val traceId: String,
    val occurredAtMillis: Long,
    val kind: KellvynAgentLabEventKind,
    val actor: String = "coordinator",
    val step: Int? = null,
    val specialistRole: KellvynSpecialistRole? = null,
    val toolName: String? = null,
    val profileSha256: String? = null,
    val durationMillis: Long? = null,
    val outcome: KellvynAgentLabOutcome? = null,
    val detailCode: String? = null
) {
    init {
        require(TRACE_ID.matches(traceId)) { "ID de trace inválido." }
        require(occurredAtMillis > 0L) { "Horário do evento inválido." }
        require(ACTOR.matches(actor)) { "Ator de trace inválido." }
        require(step == null || step > 0) { "Etapa de trace inválida." }
        require(toolName == null || TOOL_NAME.matches(toolName)) { "Nome de ferramenta inválido no trace." }
        require(profileSha256 == null || SHA256.matches(profileSha256)) { "Hash de perfil inválido no trace." }
        require(durationMillis == null || durationMillis >= 0L) { "Duração de trace inválida." }
        require(detailCode == null || DETAIL_CODE.matches(detailCode)) { "Código de detalhe inválido no trace." }
    }

    companion object {
        internal val TRACE_ID = Regex("[0-9a-fA-F-]{36}")
        private val ACTOR = Regex("[A-Za-z0-9:_-]{1,64}")
        private val TOOL_NAME = Regex("[A-Za-z][A-Za-z0-9_]{1,63}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val DETAIL_CODE = Regex("[A-Za-z0-9_.:-]{1,80}")
    }
}

internal interface KellvynAgentLabTraceRecorder {
    suspend fun record(event: KellvynAgentLabEvent)

    companion object {
        val NONE: KellvynAgentLabTraceRecorder = object : KellvynAgentLabTraceRecorder {
            override suspend fun record(event: KellvynAgentLabEvent) = Unit
        }
    }
}

/** A per-user-turn trace context shared with the orchestrator and all delegated specialists. */
internal class KellvynAgentLabTraceSession private constructor(
    val traceId: String,
    private val recorder: KellvynAgentLabTraceRecorder,
    private val clockMillis: () -> Long
) {
    suspend fun record(
        kind: KellvynAgentLabEventKind,
        actor: String = "coordinator",
        step: Int? = null,
        specialistRole: KellvynSpecialistRole? = null,
        toolName: String? = null,
        profileSha256: String? = null,
        durationMillis: Long? = null,
        outcome: KellvynAgentLabOutcome? = null,
        detailCode: String? = null
    ) {
        recorder.record(
            KellvynAgentLabEvent(
                traceId = traceId,
                occurredAtMillis = clockMillis(),
                kind = kind,
                actor = actor,
                step = step,
                specialistRole = specialistRole,
                toolName = toolName,
                profileSha256 = profileSha256,
                durationMillis = durationMillis,
                outcome = outcome,
                detailCode = detailCode
            )
        )
    }

    companion object {
        fun create(
            recorder: KellvynAgentLabTraceRecorder = KellvynAgentLabTraceRecorder.NONE,
            clockMillis: () -> Long = System::currentTimeMillis
        ): KellvynAgentLabTraceSession = KellvynAgentLabTraceSession(
            traceId = UUID.randomUUID().toString(),
            recorder = recorder,
            clockMillis = clockMillis
        )

        fun restore(
            traceId: String,
            recorder: KellvynAgentLabTraceRecorder,
            clockMillis: () -> Long = System::currentTimeMillis
        ): KellvynAgentLabTraceSession {
            require(KellvynAgentLabEvent.TRACE_ID.matches(traceId))
            return KellvynAgentLabTraceSession(traceId, recorder, clockMillis)
        }
    }
}
