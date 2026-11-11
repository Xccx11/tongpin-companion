package com.aster.companion.agent.world

/** Initiator first, then each other required character independently. */
object WorldDecisionOrder {
    fun characterIdsFor(opportunity: WorldOpportunity): List<String> = buildList {
        opportunity.initiatorCharacterId?.let(::add)
        opportunity.requiredCharacterIds
            .filter { it != opportunity.initiatorCharacterId }
            .forEach(::add)
    }.distinct()
}
