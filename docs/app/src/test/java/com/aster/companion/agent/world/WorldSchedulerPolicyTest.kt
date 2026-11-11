package com.aster.companion.agent.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldSchedulerPolicyTest {

    @Test
    fun `scheduler plans when viable horizon is below thirty minutes`() {
        assertTrue(WorldSchedulerPolicy.shouldPlan(now = 0L, viableUntil = 29 * 60_000L, retryAfter = 0L))
    }

    @Test
    fun `scheduler waits when horizon and retry backoff are still valid`() {
        assertFalse(WorldSchedulerPolicy.shouldPlan(now = 0L, viableUntil = 60 * 60_000L, retryAfter = 0L))
        assertFalse(WorldSchedulerPolicy.shouldPlan(now = 0L, viableUntil = 0L, retryAfter = 60_000L))
    }

    @Test
    fun `planner retry backoff doubles the prior persisted interval with a cap`() {
        assertTrue(
            WorldSchedulerPolicy.nextRetryAfter(
                now = 10_000L,
                lastPlanAttemptAt = 0L,
                previousRetryAfter = 0L
            ) == 70_000L
        )
        assertTrue(
            WorldSchedulerPolicy.nextRetryAfter(
                now = 100_000L,
                lastPlanAttemptAt = 70_000L,
                previousRetryAfter = 90_000L
            ) == 140_000L
        )
    }
}
