package com.aster.companion.agent.world

enum class WorldCharacterDecision {
    ACCEPT,
    REJECT,
    DELAY,
    SUBSTITUTE
}

enum class WorldDecisionResolution {
    CONFIRMED,
    CANCELLED,
    DEFERRED,
    WAITING_FOR_USER,
    INCOMPLETE
}

data class WorldDecisionResult(
    val resolution: WorldDecisionResolution,
    val includedCharacterIds: List<String>
)

/** Deterministic agreement rules, independent of model output or persistence. */
object WorldDecisionPolicy {
    fun resolve(
        opportunity: WorldOpportunity,
        decisions: Map<String, WorldCharacterDecision>,
        userAccepted: Boolean = false
    ): WorldDecisionResult {
        val required = opportunity.requiredCharacterIds.distinct()
        val initiator = opportunity.initiatorCharacterId
        if (initiator != null) {
            when (decisions[initiator]) {
                WorldCharacterDecision.REJECT -> return cancelled()
                WorldCharacterDecision.DELAY, WorldCharacterDecision.SUBSTITUTE -> return deferred()
                WorldCharacterDecision.ACCEPT -> Unit
                null -> return incomplete()
            }
        }

        required.forEach { characterId ->
            when (decisions[characterId]) {
                WorldCharacterDecision.REJECT -> return cancelled()
                WorldCharacterDecision.DELAY, WorldCharacterDecision.SUBSTITUTE -> return deferred()
                WorldCharacterDecision.ACCEPT -> Unit
                null -> return incomplete()
            }
        }

        if (opportunity.userParticipation == UserParticipation.REQUIRED && !userAccepted) {
            return WorldDecisionResult(WorldDecisionResolution.WAITING_FOR_USER, required)
        }
        return WorldDecisionResult(WorldDecisionResolution.CONFIRMED, required)
    }

    private fun cancelled() = WorldDecisionResult(WorldDecisionResolution.CANCELLED, emptyList())
    private fun deferred() = WorldDecisionResult(WorldDecisionResolution.DEFERRED, emptyList())
    private fun incomplete() = WorldDecisionResult(WorldDecisionResolution.INCOMPLETE, emptyList())
}
