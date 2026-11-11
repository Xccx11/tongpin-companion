package com.aster.companion.agent.world

data class WorldRecoveryPlan(
    val detailedElapsedMs: Long,
    val requiresSummary: Boolean
)

object WorldRecoveryPolicy {
    fun plan(lastAdvancedAt: Long, now: Long): WorldRecoveryPlan {
        val elapsed = (now - lastAdvancedAt).coerceAtLeast(0L)
        return WorldRecoveryPlan(
            detailedElapsedMs = elapsed.coerceAtMost(WorldSchedulerPolicy.MAX_DETAILED_CATCH_UP_MS),
            requiresSummary = elapsed > WorldSchedulerPolicy.MAX_DETAILED_CATCH_UP_MS
        )
    }
}
