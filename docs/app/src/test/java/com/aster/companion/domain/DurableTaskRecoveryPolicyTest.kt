package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DurableTaskRecoveryPolicyTest {
    @Test
    fun `requeues an interrupted running task instead of losing it`() {
        val recovered = DurableTaskRecoveryPolicy.recover(
            state = DurableTaskState.Running,
            attempts = 2,
            now = 1_000L
        )

        assertEquals(DurableTaskRecovery(state = DurableTaskState.Retry, attempts = 3, nextRunAt = 6_000L), recovered)
    }

    @Test
    fun `keeps queued tasks ready without increasing attempts`() {
        val recovered = DurableTaskRecoveryPolicy.recover(
            state = DurableTaskState.Queued,
            attempts = 1,
            now = 1_000L
        )

        assertEquals(DurableTaskRecovery(state = DurableTaskState.Queued, attempts = 1, nextRunAt = 1_000L), recovered)
    }
}
