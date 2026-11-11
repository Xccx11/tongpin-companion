package com.aster.companion.data

import com.aster.companion.domain.DurableTaskRecoveryPolicy
import com.aster.companion.domain.DurableTaskState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DurableTaskStore @Inject constructor(private val dao: CompanionDao) {
    suspend fun enqueue(type: String, threadId: String?, characterId: String?, payloadJson: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertDurableTask(DurableTaskEntity(id, type, threadId, characterId, payloadJson, "QUEUED", 0, now, null, null, null))
        return id
    }

    suspend fun recoverInterruptedTasks(now: Long = System.currentTimeMillis()) {
        dao.dueDurableTasks(Long.MAX_VALUE)
            .filter { it.state == "RUNNING" }
            .forEach { task ->
                val recovery = DurableTaskRecoveryPolicy.recover(DurableTaskState.Running, task.attempts, now)
                dao.upsertDurableTask(task.copy(state = recovery.state.name.uppercase(), attempts = recovery.attempts, nextRunAt = recovery.nextRunAt, startedAt = null))
            }
    }

    suspend fun markRunning(task: DurableTaskEntity, now: Long = System.currentTimeMillis()) {
        dao.upsertDurableTask(task.copy(state = "RUNNING", startedAt = now, attempts = task.attempts + 1))
    }

    suspend fun complete(task: DurableTaskEntity, now: Long = System.currentTimeMillis()) {
        dao.upsertDurableTask(task.copy(state = "COMPLETED", completedAt = now, errorSummary = null))
    }

    suspend fun cancel(task: DurableTaskEntity, now: Long = System.currentTimeMillis()) {
        dao.upsertDurableTask(task.copy(state = "CANCELLED", completedAt = now, errorSummary = "Cancelled by user"))
    }

    suspend fun retry(task: DurableTaskEntity, error: String, now: Long = System.currentTimeMillis()) {
        val recovery = DurableTaskRecoveryPolicy.recover(DurableTaskState.Running, task.attempts, now)
        dao.upsertDurableTask(task.copy(state = recovery.state.name.uppercase(), attempts = recovery.attempts, nextRunAt = recovery.nextRunAt, startedAt = null, errorSummary = error.take(240)))
    }

    suspend fun dueImageActionIds(now: Long = System.currentTimeMillis()): List<String> =
        dao.dueDurableTasks(now)
            .filter { it.type.startsWith("image_generation:") }
            .map { it.type.removePrefix("image_generation:") }
}
