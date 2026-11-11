package com.aster.companion.agent.world

object WorldSchedulerPolicy {
    const val MIN_VIABLE_HORIZON_MS = 30 * 60_000L
    const val TARGET_HORIZON_MS = 60 * 60_000L
    const val MAX_DETAILED_CATCH_UP_MS = 24 * 60 * 60_000L

    fun shouldPlan(now: Long, viableUntil: Long?, retryAfter: Long): Boolean =
        now >= retryAfter && (viableUntil == null || viableUntil - now < MIN_VIABLE_HORIZON_MS)

    fun nextRetryAfter(now: Long, lastPlanAttemptAt: Long, previousRetryAfter: Long): Long {
        val previousDelay = (previousRetryAfter - lastPlanAttemptAt).takeIf { it > 0L }
        val delay = (previousDelay?.times(2) ?: INITIAL_RETRY_DELAY_MS)
            .coerceAtMost(MAX_RETRY_DELAY_MS)
        return now + delay
    }

    private const val INITIAL_RETRY_DELAY_MS = 60_000L
    private const val MAX_RETRY_DELAY_MS = 15 * 60_000L
}
