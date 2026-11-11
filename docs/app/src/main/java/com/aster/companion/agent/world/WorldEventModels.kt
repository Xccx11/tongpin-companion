package com.aster.companion.agent.world

enum class WorldEventStatus {
    PLANNED,
    EVALUATING,
    WAITING_FOR_USER,
    CONFIRMED,
    FINISHED,
    CANCELLED,
    SUPERSEDED
}

enum class WorldEventImpact {
    KEEP,
    RESCHEDULE,
    REEVALUATE,
    CANCEL
}

enum class WorldEventFlexibility {
    FIXED,
    FLEXIBLE
}

enum class WorldEventInterruptStrategy {
    KEEP,
    RESCHEDULE,
    REEVALUATE,
    CANCEL
}

/**
 * A possible world event. It is not a command and cannot change a character
 * state until the decision coordinator obtains the required consent.
 */
data class WorldOpportunity(
    val id: String,
    val timelineId: String,
    val scheduledAt: Long,
    val latestStartAt: Long,
    val durationMinutes: Int,
    val type: String,
    val initiatorCharacterId: String?,
    val requiredCharacterIds: List<String>,
    val optionalCharacterIds: List<String> = emptyList(),
    val userParticipation: UserParticipation = UserParticipation.NONE,
    val locationId: String,
    val description: String,
    val prerequisiteEventIds: List<String> = emptyList(),
    val prerequisiteJson: String = "{}",
    val expectedEffectsJson: String = "{}",
    val flexibility: WorldEventFlexibility = WorldEventFlexibility.FLEXIBLE,
    val interruptStrategy: WorldEventInterruptStrategy = WorldEventInterruptStrategy.REEVALUATE,
    val status: WorldEventStatus = WorldEventStatus.PLANNED,
    val decisionJson: String = "{}"
)

enum class UserParticipation {
    NONE,
    OPTIONAL,
    REQUIRED
}
