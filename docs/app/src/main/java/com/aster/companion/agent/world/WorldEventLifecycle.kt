package com.aster.companion.agent.world

/** Keeps candidate event changes explicit and prevents terminal events reviving. */
object WorldEventLifecycle {
    fun transition(from: WorldEventStatus, to: WorldEventStatus): WorldEventStatus? {
        if (from == to) return from
        return to.takeIf { target -> target in allowedTargets(from) }
    }

    private fun allowedTargets(status: WorldEventStatus): Set<WorldEventStatus> = when (status) {
        WorldEventStatus.PLANNED -> setOf(
            WorldEventStatus.EVALUATING,
            WorldEventStatus.CANCELLED,
            WorldEventStatus.SUPERSEDED
        )
        WorldEventStatus.EVALUATING -> setOf(
            WorldEventStatus.CONFIRMED,
            WorldEventStatus.WAITING_FOR_USER,
            WorldEventStatus.PLANNED,
            WorldEventStatus.CANCELLED,
            WorldEventStatus.SUPERSEDED
        )
        WorldEventStatus.WAITING_FOR_USER -> setOf(
            WorldEventStatus.CONFIRMED,
            WorldEventStatus.PLANNED,
            WorldEventStatus.CANCELLED,
            WorldEventStatus.SUPERSEDED
        )
        WorldEventStatus.CONFIRMED -> setOf(
            WorldEventStatus.FINISHED,
            WorldEventStatus.CANCELLED
        )
        WorldEventStatus.FINISHED,
        WorldEventStatus.CANCELLED,
        WorldEventStatus.SUPERSEDED -> emptySet()
    }
}
