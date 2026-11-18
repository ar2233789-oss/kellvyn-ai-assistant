package com.kellvyn.assistant

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal enum class KellvynQueuedTaskState {
    QUEUED,
    RUNNING,
    WAITING_RESOURCES,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

internal data class KellvynQueuedTask(
    val id: String,
    val handlerId: String,
    val payload: String,
    val priority: Int,
    val workloadClass: KellvynWorkloadClass,
    val estimatedWorkingSetBytes: Long,
    val createdAtMillis: Long,
    val notBeforeMillis: Long,
    val attempts: Int,
    val maxAttempts: Int,
    val state: KellvynQueuedTaskState,
    val lastError: String?,
    val resultSummary: String?,
    val updatedAtMillis: Long
)

internal data class KellvynQueuedTaskSpec(
    val handlerId: String,
    val payload: String,
    val priority: Int = 50,
    val workloadClass: KellvynWorkloadClass = KellvynWorkloadClass.STANDARD,
    val estimatedWorkingSetBytes: Long = 0L,
    val maxAttempts: Int = 3,
    val notBeforeMillis: Long = 0L
) {
    init {
        require(handlerId.matches(Regex("[a-z][a-z0-9_.-]{1,79}")))
        require(payload.length <= 32_000)
        require(priority in 0..100)
        require(estimatedWorkingSetBytes >= 0L)
        require(maxAttempts in 1..10)
        require(notBeforeMillis >= 0L)
    }
}

/**
 * Durable metadata queue. Payloads are bounded descriptors/references, not in-memory closures,
 * Activity objects, images or model tensors. Execution is at-least-once, so handlers must be
 * idempotent or use the task id with the planner/checkpoint store.
 */
internal class KellvynPersistentTaskQueue(
    context: Context,
    databaseName: String = DATABASE_NAME,
    private val payloadCipher: KellvynTaskCheckpointCipher =
        KellvynAndroidTaskCheckpointCipher(DEFAULT_QUEUE_KEY_ALIAS)
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TASKS (
                id TEXT PRIMARY KEY NOT NULL,
                handler_id TEXT NOT NULL,
                payload BLOB NOT NULL,
                priority INTEGER NOT NULL,
                workload_class TEXT NOT NULL,
                estimated_working_set_bytes INTEGER NOT NULL,
                created_at_ms INTEGER NOT NULL,
                not_before_ms INTEGER NOT NULL,
                attempts INTEGER NOT NULL,
                max_attempts INTEGER NOT NULL,
                state TEXT NOT NULL,
                last_error TEXT,
                result_summary BLOB,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_kellvyn_task_queue ON $TABLE_TASKS(state, not_before_ms, priority DESC, created_at_ms ASC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop the task journal. Future versions must use explicit additive migrations.
        if (oldVersion < 1) onCreate(db)
    }

    @Synchronized
    fun enqueue(spec: KellvynQueuedTaskSpec, nowMillis: Long): String {
        require(nowMillis > 0L)
        val db = writableDatabase
        pruneCompleted(db, nowMillis - FINISHED_RETENTION_MILLIS)
        check(pendingCountInternal(db) < MAX_PENDING_TASKS) {
            "A fila local atingiu o limite de tarefas pendentes."
        }
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_HANDLER, spec.handlerId)
            put(
                COL_PAYLOAD,
                payloadCipher.encrypt(
                    spec.payload.toByteArray(StandardCharsets.UTF_8),
                    payloadAssociatedData(
                        id,
                        spec.handlerId,
                        spec.priority,
                        spec.workloadClass,
                        spec.estimatedWorkingSetBytes,
                        nowMillis,
                        spec.maxAttempts
                    )
                )
            )
            put(COL_PRIORITY, spec.priority)
            put(COL_WORKLOAD, spec.workloadClass.name)
            put(COL_ESTIMATE, spec.estimatedWorkingSetBytes)
            put(COL_CREATED, nowMillis)
            put(COL_NOT_BEFORE, maxOf(nowMillis, spec.notBeforeMillis))
            put(COL_ATTEMPTS, 0)
            put(COL_MAX_ATTEMPTS, spec.maxAttempts)
            put(COL_STATE, KellvynQueuedTaskState.QUEUED.name)
            putNull(COL_ERROR)
            putNull(COL_RESULT)
            put(COL_UPDATED, nowMillis)
        }
        check(db.insertOrThrow(TABLE_TASKS, null, values) > 0L) {
            "Não foi possível persistir a tarefa."
        }
        return id
    }

    /** Claims one ready task atomically in priority-descending, FIFO-within-priority order. */
    @Synchronized
    fun claimNext(
        nowMillis: Long,
        allowHeavyWorkload: Boolean = true
    ): KellvynQueuedTask? {
        require(nowMillis > 0L)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val selection = if (allowHeavyWorkload) {
                "$COL_STATE IN (?, ?) AND $COL_NOT_BEFORE <= ?"
            } else {
                "$COL_STATE IN (?, ?) AND $COL_NOT_BEFORE <= ? AND $COL_WORKLOAD != ?"
            }
            val selectionArgs = if (allowHeavyWorkload) {
                arrayOf(
                    KellvynQueuedTaskState.QUEUED.name,
                    KellvynQueuedTaskState.WAITING_RESOURCES.name,
                    nowMillis.toString()
                )
            } else {
                arrayOf(
                    KellvynQueuedTaskState.QUEUED.name,
                    KellvynQueuedTaskState.WAITING_RESOURCES.name,
                    nowMillis.toString(),
                    KellvynWorkloadClass.HEAVY.name
                )
            }
            val task = db.query(
                TABLE_TASKS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                "$COL_PRIORITY DESC, $COL_CREATED ASC",
                "1"
            ).use { cursor -> if (cursor.moveToFirst()) cursor.toTask() else null }
                ?: return null

            val values = ContentValues().apply {
                put(COL_STATE, KellvynQueuedTaskState.RUNNING.name)
                put(COL_ATTEMPTS, task.attempts + 1)
                put(COL_UPDATED, nowMillis)
                putNull(COL_ERROR)
            }
            val changed = db.update(
                TABLE_TASKS,
                values,
                "$COL_ID = ? AND $COL_STATE IN (?, ?)",
                arrayOf(
                    task.id,
                    KellvynQueuedTaskState.QUEUED.name,
                    KellvynQueuedTaskState.WAITING_RESOURCES.name
                )
            )
            check(changed == 1) { "A tarefa deixou de estar disponível durante a reserva." }
            db.setTransactionSuccessful()
            return task.copy(
                attempts = task.attempts + 1,
                state = KellvynQueuedTaskState.RUNNING,
                lastError = null,
                updatedAtMillis = nowMillis
            )
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun complete(id: String, summary: String, nowMillis: Long) {
        require(summary.length <= MAX_SUMMARY_CHARS && nowMillis > 0L)
        val task = get(id) ?: error("Tarefa não encontrada.")
        check(task.state == KellvynQueuedTaskState.RUNNING)
        val encryptedSummary = payloadCipher.encrypt(
            summary.toByteArray(StandardCharsets.UTF_8),
            resultAssociatedData(id, task.createdAtMillis, nowMillis)
        )
        val values = ContentValues().apply {
            put(COL_STATE, KellvynQueuedTaskState.SUCCEEDED.name)
            put(COL_RESULT, encryptedSummary)
            putNull(COL_ERROR)
            put(COL_UPDATED, nowMillis)
        }
        check(writableDatabase.update(
            TABLE_TASKS, values,
            "$COL_ID = ? AND $COL_STATE = ?",
            arrayOf(id, KellvynQueuedTaskState.RUNNING.name)
        ) == 1) { "A tarefa não estava em execução para ser concluída." }
    }

    @Synchronized
    fun defer(id: String, notBeforeMillis: Long, reasonCode: String, nowMillis: Long) {
        require(notBeforeMillis >= 0L && nowMillis > 0L)
        require(reasonCode.matches(ERROR_CODE))
        val values = ContentValues().apply {
            put(COL_STATE, KellvynQueuedTaskState.WAITING_RESOURCES.name)
            put(COL_NOT_BEFORE, maxOf(nowMillis, notBeforeMillis))
            put(COL_ERROR, reasonCode)
            put(COL_UPDATED, nowMillis)
        }
        check(writableDatabase.update(
            TABLE_TASKS, values,
            "$COL_ID = ? AND $COL_STATE = ?",
            arrayOf(id, KellvynQueuedTaskState.RUNNING.name)
        ) == 1) { "A tarefa não estava em execução para ser adiada." }
    }

    @Synchronized
    fun failOrRetry(id: String, reasonCode: String, nowMillis: Long): KellvynQueuedTaskState {
        require(nowMillis > 0L && reasonCode.matches(ERROR_CODE))
        val task = get(id) ?: error("Tarefa não encontrada.")
        check(task.state == KellvynQueuedTaskState.RUNNING)
        val terminal = task.attempts >= task.maxAttempts
        val nextState = if (terminal) KellvynQueuedTaskState.FAILED else KellvynQueuedTaskState.QUEUED
        val values = ContentValues().apply {
            put(COL_STATE, nextState.name)
            put(COL_ERROR, reasonCode)
            put(COL_NOT_BEFORE, if (terminal) nowMillis else nowMillis + retryDelay(task.attempts))
            put(COL_UPDATED, nowMillis)
        }
        check(writableDatabase.update(
            TABLE_TASKS, values,
            "$COL_ID = ? AND $COL_STATE = ?",
            arrayOf(id, KellvynQueuedTaskState.RUNNING.name)
        ) == 1)
        return nextState
    }

    /**
     * Permanently rejects a task whose descriptor violates its registered handler contract.
     * This is distinct from transient execution errors, which remain eligible for retry.
     */
    @Synchronized
    fun failPermanently(id: String, reasonCode: String, nowMillis: Long) {
        require(nowMillis > 0L && reasonCode.matches(ERROR_CODE))
        val values = ContentValues().apply {
            put(COL_STATE, KellvynQueuedTaskState.FAILED.name)
            put(COL_ERROR, reasonCode)
            put(COL_NOT_BEFORE, nowMillis)
            put(COL_UPDATED, nowMillis)
        }
        check(writableDatabase.update(
            TABLE_TASKS,
            values,
            "$COL_ID = ? AND $COL_STATE = ?",
            arrayOf(id, KellvynQueuedTaskState.RUNNING.name)
        ) == 1) { "A tarefa não estava em execução para ser rejeitada." }
    }

    @Synchronized
    fun cancelQueued(id: String, nowMillis: Long): Boolean {
        require(nowMillis > 0L)
        val values = ContentValues().apply {
            put(COL_STATE, KellvynQueuedTaskState.CANCELLED.name)
            put(COL_UPDATED, nowMillis)
            put(COL_ERROR, "OWNER_CANCELLED")
        }
        return writableDatabase.update(
            TABLE_TASKS,
            values,
            "$COL_ID = ? AND $COL_STATE IN (?, ?)",
            arrayOf(
                id,
                KellvynQueuedTaskState.QUEUED.name,
                KellvynQueuedTaskState.WAITING_RESOURCES.name
            )
        ) == 1
    }

    /** Requeues RUNNING records after process death; the queue is deliberately at-least-once. */
    @Synchronized
    fun recoverInterruptedTasks(nowMillis: Long, recoveryDelayMillis: Long = 5_000L): Int {
        require(nowMillis > 0L && recoveryDelayMillis >= 0L)
        val values = ContentValues().apply {
            put(COL_STATE, KellvynQueuedTaskState.QUEUED.name)
            put(COL_NOT_BEFORE, nowMillis + recoveryDelayMillis)
            put(COL_ERROR, "PROCESS_INTERRUPTED")
            put(COL_UPDATED, nowMillis)
        }
        val db = writableDatabase
        val requeued = db.update(
            TABLE_TASKS,
            values,
            "$COL_STATE = ? AND $COL_ATTEMPTS < $COL_MAX_ATTEMPTS",
            arrayOf(KellvynQueuedTaskState.RUNNING.name)
        )
        val exhausted = ContentValues().apply {
            put(COL_STATE, KellvynQueuedTaskState.FAILED.name)
            put(COL_ERROR, "PROCESS_INTERRUPTED_MAX_ATTEMPTS")
            put(COL_UPDATED, nowMillis)
        }
        val failed = db.update(
            TABLE_TASKS,
            exhausted,
            "$COL_STATE = ? AND $COL_ATTEMPTS >= $COL_MAX_ATTEMPTS",
            arrayOf(KellvynQueuedTaskState.RUNNING.name)
        )
        requeued + failed
    }

    @Synchronized
    fun get(id: String): KellvynQueuedTask? =
        readableDatabase.query(
            TABLE_TASKS, null, "$COL_ID = ?", arrayOf(id), null, null, null, "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toTask() else null }

    @Synchronized
    fun pendingCount(): Int = pendingCountInternal(readableDatabase)

    @Synchronized
    fun markHandlerAvailable(handlerId: String, nowMillis: Long): Int {
        require(handlerId.matches(Regex("[a-z][a-z0-9_.-]{1,79}")) && nowMillis > 0L)
        val values = ContentValues().apply {
            put(COL_STATE, KellvynQueuedTaskState.QUEUED.name)
            put(COL_NOT_BEFORE, nowMillis)
            putNull(COL_ERROR)
            put(COL_UPDATED, nowMillis)
        }
        return writableDatabase.update(
            TABLE_TASKS,
            values,
            "$COL_HANDLER = ? AND $COL_STATE = ? AND $COL_ERROR = ?",
            arrayOf(
                handlerId,
                KellvynQueuedTaskState.WAITING_RESOURCES.name,
                "HANDLER_UNAVAILABLE"
            )
        )
    }

    /** Delay until the next eligible short task; HEAVY work is reserved for foreground execution. */
    @Synchronized
    fun nextRunnableDelayMillis(
        nowMillis: Long,
        allowHeavyWorkload: Boolean
    ): Long? {
        require(nowMillis > 0L)
        val workloadFilter = if (allowHeavyWorkload) "" else " AND $COL_WORKLOAD != ?"
        val args = if (allowHeavyWorkload) {
            arrayOf(
                KellvynQueuedTaskState.QUEUED.name,
                KellvynQueuedTaskState.WAITING_RESOURCES.name,
                "HANDLER_UNAVAILABLE"
            )
        } else {
            arrayOf(
                KellvynQueuedTaskState.QUEUED.name,
                KellvynQueuedTaskState.WAITING_RESOURCES.name,
                "HANDLER_UNAVAILABLE",
                KellvynWorkloadClass.HEAVY.name
            )
        }
        return readableDatabase.rawQuery(
            "SELECT MIN($COL_NOT_BEFORE) FROM $TABLE_TASKS " +
                "WHERE $COL_STATE IN (?, ?) AND ($COL_ERROR IS NULL OR $COL_ERROR != ?)$workloadFilter",
            args
        ).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null
            else (cursor.getLong(0) - nowMillis).coerceAtLeast(0L)
        }
    }

    private fun pendingCountInternal(db: SQLiteDatabase): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_TASKS WHERE $COL_STATE IN (?, ?)",
            arrayOf(KellvynQueuedTaskState.QUEUED.name, KellvynQueuedTaskState.WAITING_RESOURCES.name)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun pruneCompleted(db: SQLiteDatabase, cutoffMillis: Long) {
        db.delete(
            TABLE_TASKS,
            "$COL_STATE IN (?, ?, ?) AND $COL_UPDATED < ?",
            arrayOf(
                KellvynQueuedTaskState.SUCCEEDED.name,
                KellvynQueuedTaskState.FAILED.name,
                KellvynQueuedTaskState.CANCELLED.name,
                cutoffMillis.toString()
            )
        )
    }

    private fun Cursor.toTask(): KellvynQueuedTask {
        val workload = runCatching {
            KellvynWorkloadClass.valueOf(getString(getColumnIndexOrThrow(COL_WORKLOAD)))
        }.getOrElse { error("Classe de carga persistida desconhecida.") }
        val state = runCatching {
            KellvynQueuedTaskState.valueOf(getString(getColumnIndexOrThrow(COL_STATE)))
        }.getOrElse { error("Estado de tarefa persistido desconhecido.") }
        val id = getString(getColumnIndexOrThrow(COL_ID))
        val handlerId = getString(getColumnIndexOrThrow(COL_HANDLER))
        val priority = getInt(getColumnIndexOrThrow(COL_PRIORITY))
        val estimatedBytes = getLong(getColumnIndexOrThrow(COL_ESTIMATE))
        val createdAt = getLong(getColumnIndexOrThrow(COL_CREATED))
        val maxAttempts = getInt(getColumnIndexOrThrow(COL_MAX_ATTEMPTS))
        val payloadBytes = getBlob(getColumnIndexOrThrow(COL_PAYLOAD))
        val payload = payloadCipher.decrypt(
            payloadBytes,
            payloadAssociatedData(
                id, handlerId, priority, workload, estimatedBytes, createdAt, maxAttempts
            )
        ).toString(StandardCharsets.UTF_8)
        val updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED))
        val resultIndex = getColumnIndexOrThrow(COL_RESULT)
        val result = if (isNull(resultIndex)) null else {
            payloadCipher.decrypt(
                getBlob(resultIndex),
                resultAssociatedData(id, createdAt, updatedAt)
            ).toString(StandardCharsets.UTF_8)
        }
        return KellvynQueuedTask(
            id = id,
            handlerId = handlerId,
            payload = payload,
            priority = priority,
            workloadClass = workload,
            estimatedWorkingSetBytes = estimatedBytes,
            createdAtMillis = createdAt,
            notBeforeMillis = getLong(getColumnIndexOrThrow(COL_NOT_BEFORE)),
            attempts = getInt(getColumnIndexOrThrow(COL_ATTEMPTS)),
            maxAttempts = maxAttempts,
            state = state,
            lastError = getStringOrNull(COL_ERROR),
            resultSummary = result,
            updatedAtMillis = updatedAt
        )
    }

    private fun payloadAssociatedData(
        id: String,
        handlerId: String,
        priority: Int,
        workload: KellvynWorkloadClass,
        estimatedBytes: Long,
        createdAtMillis: Long,
        maxAttempts: Int
    ): ByteArray = listOf(
        "KELLVYN_TASK_PAYLOAD_V1",
        id,
        handlerId,
        priority.toString(),
        workload.name,
        estimatedBytes.toString(),
        createdAtMillis.toString(),
        maxAttempts.toString()
    ).joinToString("\u0000").toByteArray(StandardCharsets.UTF_8)

    private fun resultAssociatedData(
        id: String,
        createdAtMillis: Long,
        updatedAtMillis: Long
    ): ByteArray = listOf(
        "KELLVYN_TASK_RESULT_V1",
        id,
        createdAtMillis.toString(),
        updatedAtMillis.toString()
    ).joinToString("\u0000").toByteArray(StandardCharsets.UTF_8)

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun retryDelay(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 6)
        return minOf(BASE_RETRY_MILLIS shl exponent, MAX_RETRY_MILLIS)
    }

    companion object {
        const val DATABASE_NAME = "kellvyn_task_queue.db"
        private const val DATABASE_VERSION = 1
        private const val DEFAULT_QUEUE_KEY_ALIAS = "kellvyn.task-queue.aes-gcm.v1"
        private val ERROR_CODE = Regex("[A-Z0-9_]{1,64}")
        private const val TABLE_TASKS = "kellvyn_tasks"
        private const val MAX_SUMMARY_CHARS = 4_000
        private const val MAX_ERROR_CHARS = 500
        private const val MAX_PENDING_TASKS = 500
        private const val FINISHED_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        private const val BASE_RETRY_MILLIS = 5_000L
        private const val MAX_RETRY_MILLIS = 300_000L

        private const val COL_ID = "id"
        private const val COL_HANDLER = "handler_id"
        private const val COL_PAYLOAD = "payload"
        private const val COL_PRIORITY = "priority"
        private const val COL_WORKLOAD = "workload_class"
        private const val COL_ESTIMATE = "estimated_working_set_bytes"
        private const val COL_CREATED = "created_at_ms"
        private const val COL_NOT_BEFORE = "not_before_ms"
        private const val COL_ATTEMPTS = "attempts"
        private const val COL_MAX_ATTEMPTS = "max_attempts"
        private const val COL_STATE = "state"
        private const val COL_ERROR = "last_error"
        private const val COL_RESULT = "result_summary"
        private const val COL_UPDATED = "updated_at_ms"
    }
}

internal fun interface KellvynScheduledTaskHandler {
    /** Handlers receive only a bounded serialized descriptor and must be idempotent. */
    suspend fun execute(payload: String): String
}

internal sealed class KellvynScheduledTaskResult {
    data class Completed(val taskId: String, val summary: String) : KellvynScheduledTaskResult()
    data class Deferred(val taskId: String, val reason: String, val retryAfterMillis: Long) : KellvynScheduledTaskResult()
    data class Retrying(val taskId: String, val reason: String) : KellvynScheduledTaskResult()
    data class Failed(val taskId: String, val reason: String) : KellvynScheduledTaskResult()
}

internal class KellvynPermanentScheduledTaskException(
    val reasonCode: String,
    message: String
) : IllegalArgumentException(message) {
    init {
        require(reasonCode.matches(Regex("[A-Z0-9_]{1,64}")))
    }
}

internal class KellvynTaskScheduler(
    private val queue: KellvynPersistentTaskQueue,
    private val taskBroker: KellvynTaskBroker,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val handlers = ConcurrentHashMap<String, KellvynScheduledTaskHandler>()

    private val recoveryMutex = Mutex()
    @Volatile
    private var recoveryCompleted = false

    suspend fun registerHandler(handlerId: String, handler: KellvynScheduledTaskHandler) {
        require(handlerId.matches(Regex("[a-z][a-z0-9_.-]{1,79}")))
        require(handlers.putIfAbsent(handlerId, handler) == null) {
            "Handler de tarefa duplicado: $handlerId"
        }
        try {
            withContext(Dispatchers.IO) {
                queue.markHandlerAvailable(handlerId, clock())
            }
        } catch (error: Throwable) {
            // Keep the in-memory allowlist and durable queue state atomic from the caller's view.
            handlers.remove(handlerId, handler)
            throw error
        }
    }

    suspend fun enqueue(spec: KellvynQueuedTaskSpec): String =
        withContext(Dispatchers.IO) { queue.enqueue(spec, clock()) }

    private suspend fun ensureRecovered() {
        if (recoveryCompleted) return
        recoveryMutex.withLock {
            if (!recoveryCompleted) {
                withContext(Dispatchers.IO) {
                    queue.recoverInterruptedTasks(clock())
                }
                recoveryCompleted = true
            }
        }
    }

    suspend fun runNext(allowHeavyWorkload: Boolean = true): KellvynScheduledTaskResult? {
        ensureRecovered()
        val task = withContext(Dispatchers.IO) {
            queue.claimNext(clock(), allowHeavyWorkload)
        } ?: return null
        val handler = handlers[task.handlerId]
        if (handler == null) {
            val reason = "HANDLER_UNAVAILABLE: handler ainda não registrado nesta sessão."
            withContext(Dispatchers.IO) {
                queue.defer(task.id, clock() + 60_000L, "HANDLER_UNAVAILABLE", clock())
            }
            return KellvynScheduledTaskResult.Deferred(task.id, reason, 60_000L)
        }

        return try {
            val summary = withContext(Dispatchers.Default) {
                taskBroker.execute(
                    KellvynResourceRequest(
                        taskId = task.id,
                        workload = task.workloadClass,
                        estimatedWorkingSetBytes = task.estimatedWorkingSetBytes
                    )
                ) {
                    handler.execute(task.payload)
                }
            }
            withContext(Dispatchers.IO) { queue.complete(task.id, summary.take(4_000), clock()) }
            KellvynScheduledTaskResult.Completed(task.id, summary.take(4_000))
        } catch (rejected: KellvynPermanentScheduledTaskException) {
            withContext(Dispatchers.IO) {
                queue.failPermanently(task.id, rejected.reasonCode, clock())
            }
            KellvynScheduledTaskResult.Failed(
                task.id,
                rejected.message ?: rejected.reasonCode
            )
        } catch (deferred: KellvynTaskDeferredException) {
            withContext(Dispatchers.IO) {
                queue.defer(task.id, clock() + deferred.retryAfterMillis, deferred.reasonCode, clock())
            }
            KellvynScheduledTaskResult.Deferred(
                task.id,
                deferred.reasonCode,
                deferred.retryAfterMillis
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    queue.defer(task.id, clock() + 5_000L, "HANDLER_CANCELLED", clock())
                }
            }
            throw cancelled
        } catch (error: Exception) {
            val reason = (error.message ?: error.javaClass.simpleName).take(500)
            val state = withContext(Dispatchers.IO) {
                queue.failOrRetry(task.id, "EXECUTION_FAILED", clock())
            }
            if (state == KellvynQueuedTaskState.FAILED) {
                KellvynScheduledTaskResult.Failed(task.id, reason)
            } else {
                KellvynScheduledTaskResult.Retrying(task.id, reason)
            }
        }
    }

    suspend fun runReady(
        maxTasks: Int = 4,
        allowHeavyWorkload: Boolean = true
    ): List<KellvynScheduledTaskResult> {
        require(maxTasks in 1..16)
        val results = mutableListOf<KellvynScheduledTaskResult>()
        repeat(maxTasks) {
            when (val result = runNext(allowHeavyWorkload)) {
                null -> return results
                is KellvynScheduledTaskResult.Deferred -> {
                    results += result
                    // An unavailable handler must not block unrelated ready work.
                    if (result.reason.startsWith("HANDLER_UNAVAILABLE")) return@repeat
                    return results
                }
                else -> results += result
            }
        }
        return results
    }

    suspend fun nextRunnableDelayMillis(
        allowHeavyWorkload: Boolean = false
    ): Long? = withContext(Dispatchers.IO) {
        queue.nextRunnableDelayMillis(clock(), allowHeavyWorkload)
    }
}
