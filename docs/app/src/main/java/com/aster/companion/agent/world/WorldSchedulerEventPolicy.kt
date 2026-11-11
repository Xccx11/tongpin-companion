package com.aster.companion.agent.world

data class WorldDueEvents(
    val dueIds: List<String>,
    val expiredIds: List<String>
)

object WorldSchedulerEventPolicy {
    fun classify(now: Long, opportunities: List<WorldOpportunity>): WorldDueEvents {
        val candidates = opportunities.filter { it.status in ACTIVE_CANDIDATE_STATUSES }
        return WorldDueEvents(
            dueIds = candidates.filter { it.scheduledAt <= now && it.latestStartAt >= now }.map { it.id },
            expiredIds = candidates.filter { it.latestStartAt < now }.map { it.id }
        )
    }

    private val ACTIVE_CANDIDATE_STATUSES = setOf(
        WorldEventStatus.PLANNED,
        WorldEventStatus.EVALUATING,
        WorldEventStatus.WAITING_FOR_USER
    )
}
