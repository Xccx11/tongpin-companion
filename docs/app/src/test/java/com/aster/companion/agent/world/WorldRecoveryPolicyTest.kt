package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldRecoveryPolicyTest {
    @Test
    fun `recovery limits detailed catch up to twenty four hours`() {
        val plan = WorldRecoveryPolicy.plan(lastAdvancedAt = 0L, now = 30 * 60 * 60_000L)

        assertEquals(WorldSchedulerPolicy.MAX_DETAILED_CATCH_UP_MS, plan.detailedElapsedMs)
        assertTrue(plan.requiresSummary)
    }

    @Test
    fun `recovery keeps short offline periods detailed`() {
        val plan = WorldRecoveryPolicy.plan(lastAdvancedAt = 10L, now = 60_010L)

        assertEquals(60_000L, plan.detailedElapsedMs)
        assertEquals(false, plan.requiresSummary)
    }
}
