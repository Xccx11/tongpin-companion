package com.aster.companion.domain

enum class DurableTaskState { Queued, Running, Retry, Completed, Failed, Cancelled }

data class DurableTaskRecovery(
    val state: DurableTaskState,
    val attempts: Int,
    val nextRunAt: Long
)

object DurableTaskRecoveryPolicy {
    private const val RETRY_DELAY_MS = 5_000L

    fun recover(state: DurableTaskState, attempts: Int, now: Long): DurableTaskRecovery = when (state) {
        DurableTaskState.Running -> DurableTaskRecovery(DurableTaskState.Retry, attempts + 1, now + RETRY_DELAY_MS)
        DurableTaskState.Queued, DurableTaskState.Retry -> DurableTaskRecovery(state, attempts, now)
        DurableTaskState.Completed, DurableTaskState.Failed, DurableTaskState.Cancelled -> DurableTaskRecovery(state, attempts, Long.MAX_VALUE)
    }
}
