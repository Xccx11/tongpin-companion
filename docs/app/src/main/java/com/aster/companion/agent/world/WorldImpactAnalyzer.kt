package com.aster.companion.agent.world

import javax.inject.Inject

enum class WorldInteractionType {
    CHAT,
    MOVE,
    OBSERVE,
    WAIT,
    REST,
    FREE_ACTION,
    INVITE,
    INVITATION_RESPONSE
}

data class WorldInteraction(
    val type: WorldInteractionType,
    val locationId: String,
    val previousLocationId: String? = null,
    val targetCharacterIds: Set<String> = emptySet(),
    val occurredAt: Long,
    val detail: String = ""
)

data class WorldImpactReport(val impacts: Map<String, WorldEventImpact>)

/** Applies deterministic conflicts first; planner re-evaluates only affected windows later. */
class WorldImpactAnalyzer @Inject constructor() {
    fun analyze(interaction: WorldInteraction, opportunities: List<WorldOpportunity>): WorldImpactReport {
        val active = opportunities.filterNot { it.status in TERMINAL_STATUSES }
        val direct = active
            .filter { opportunity -> directlyAffected(interaction, opportunity) }
            .associate { it.id to it.interruptStrategy.toImpact() }
            .toMutableMap()
        if (direct.isEmpty()) return WorldImpactReport(emptyMap())

        val dependents = active
            .flatMap { opportunity -> opportunity.prerequisiteEventIds.map { dependency -> dependency to opportunity.id } }
            .groupBy({ it.first }, { it.second })
        val queue = ArrayDeque(direct.keys)
        val visited = direct.keys.toMutableSet()
        while (queue.isNotEmpty()) {
            val sourceId = queue.removeFirst()
            dependents[sourceId].orEmpty().forEach { dependentId ->
                if (visited.add(dependentId)) {
                    direct[dependentId] = WorldEventImpact.REEVALUATE
                    queue.addLast(dependentId)
                }
            }
        }
        return WorldImpactReport(direct)
    }

    private fun directlyAffected(interaction: WorldInteraction, opportunity: WorldOpportunity): Boolean {
        val participants = opportunity.requiredCharacterIds + opportunity.optionalCharacterIds
        if (interaction.targetCharacterIds.any { it in participants }) return true
        if (interaction.type !in PHYSICAL_INTERACTIONS) return false
        return interaction.locationId == opportunity.locationId ||
            interaction.type == WorldInteractionType.MOVE &&
            interaction.previousLocationId == opportunity.locationId
    }

    private fun WorldEventInterruptStrategy.toImpact(): WorldEventImpact = when (this) {
        WorldEventInterruptStrategy.KEEP -> WorldEventImpact.KEEP
        WorldEventInterruptStrategy.RESCHEDULE -> WorldEventImpact.RESCHEDULE
        WorldEventInterruptStrategy.REEVALUATE -> WorldEventImpact.REEVALUATE
        WorldEventInterruptStrategy.CANCEL -> WorldEventImpact.CANCEL
    }

    private companion object {
        val TERMINAL_STATUSES = setOf(
            WorldEventStatus.CONFIRMED,
            WorldEventStatus.FINISHED,
            WorldEventStatus.CANCELLED,
            WorldEventStatus.SUPERSEDED
        )
        val PHYSICAL_INTERACTIONS = setOf(
            WorldInteractionType.MOVE,
            WorldInteractionType.REST,
            WorldInteractionType.FREE_ACTION,
            WorldInteractionType.INVITE,
            WorldInteractionType.INVITATION_RESPONSE
        )
    }
}
