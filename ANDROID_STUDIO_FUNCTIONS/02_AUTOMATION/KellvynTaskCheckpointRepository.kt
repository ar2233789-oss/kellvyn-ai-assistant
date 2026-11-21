package com.kellvyn.assistant

/**
 * Suspendable boundary for durable agent task plans.
 *
 * Implementations must persist the complete validated planner state and enforce the
 * expected previous hash. This lets the agent survive process/chat interruption without
 * treating a model's in-memory statement as durable progress.
 */
internal interface KellvynTaskCheckpointRepository {
    suspend fun append(
        state: KellvynTaskState,
        createdAtMillis: Long,
        expectedPreviousSha256: String? = null
    ): KellvynTaskCheckpointMetadata

    suspend fun loadLatest(taskId: String): KellvynTaskCheckpointSnapshot?
}
