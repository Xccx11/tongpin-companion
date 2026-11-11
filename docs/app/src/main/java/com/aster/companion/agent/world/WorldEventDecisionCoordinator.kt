package com.aster.companion.agent.world

import com.aster.companion.agent.ActivityManager
import com.aster.companion.agent.CharacterCognitionEngine
import com.aster.companion.domain.CandidateAction
import javax.inject.Inject
import javax.inject.Singleton

enum class WorldDecisionOutcomeType {
    CONFIRMED,
    CANCELLED,
    DEFERRED,
    WAITING_FOR_USER,
    UNCHANGED
}

data class WorldDecisionOutcome(
    val eventId: String,
    val type: WorldDecisionOutcomeType,
    val activityId: String? = null
)

/**
 * Bridges persistent opportunities to the existing character cognition engine.
 * The user is never sent to cognition and can join only by explicit response.
 */
@Singleton
class WorldEventDecisionCoordinator @Inject constructor(
    private val timelineManager: TimelineManager,
    private val cognitionEngine: CharacterCognitionEngine,
    private val activityManager: ActivityManager
) {
    suspend fun evaluate(eventId: String, now: Long = System.currentTimeMillis()): WorldDecisionOutcome {
        val event = timelineManager.opportunity(eventId) ?: return unchanged(eventId)
        if (event.status in TERMINAL_OR_CONFIRMED) return unchanged(eventId)
        var snapshot = WorldDecisionCodec.decode(event.decisionJson)

        if (event.status == WorldEventStatus.WAITING_FOR_USER) {
            return when (snapshot.userAccepted) {
                false -> cancel(event, snapshot, now)
                true -> confirmIfAgreed(event, snapshot, now)
                null -> waiting(eventId)
            }
        }
        if (snapshot.nextAttemptAt > now) return deferred(eventId)

        if (event.status == WorldEventStatus.PLANNED && !timelineManager.transition(
                event.id, WorldEventStatus.EVALUATING, WorldDecisionCodec.encode(snapshot), now
            )
        ) return unchanged(eventId)

        for (characterId in WorldDecisionOrder.characterIdsFor(event)) {
            if (snapshot.characterDecisions.containsKey(characterId)) continue
            val decision = decideForCharacter(event, characterId) ?: return defer(event, snapshot, now)
            snapshot = snapshot.copy(characterDecisions = snapshot.characterDecisions + (characterId to decision))
            when (WorldDecisionPolicy.resolve(event, snapshot.characterDecisions, userAccepted = false).resolution) {
                WorldDecisionResolution.CANCELLED -> return cancel(event, snapshot, now)
                WorldDecisionResolution.DEFERRED -> return defer(event, snapshot, now)
                else -> Unit
            }
        }
        return confirmIfAgreed(event, snapshot, now)
    }

    suspend fun respondToUserInvitation(
        eventId: String,
        accepted: Boolean,
        now: Long = System.currentTimeMillis()
    ): WorldDecisionOutcome {
        val event = timelineManager.opportunity(eventId) ?: return unchanged(eventId)
        if (event.userParticipation == UserParticipation.NONE || event.status in TERMINAL_OR_CONFIRMED) {
            return unchanged(eventId)
        }
        val snapshot = WorldDecisionCodec.decode(event.decisionJson).copy(userAccepted = accepted)
        if (WorldUserInvitationPolicy.afterResponse(event.userParticipation, accepted) == WorldUserInvitationOutcome.CANCEL) {
            return cancel(event, snapshot, now)
        }
        return evaluatePersistedResponse(event, snapshot, now)
    }

    private suspend fun evaluatePersistedResponse(
        event: WorldOpportunity,
        snapshot: WorldDecisionSnapshot,
        now: Long
    ): WorldDecisionOutcome {
        return when (event.status) {
            WorldEventStatus.WAITING_FOR_USER -> confirmIfAgreed(event, snapshot, now)
            WorldEventStatus.PLANNED, WorldEventStatus.EVALUATING -> {
                timelineManager.transition(event.id, WorldEventStatus.EVALUATING, WorldDecisionCodec.encode(snapshot), now)
                evaluate(event.id, now)
            }
            else -> unchanged(event.id)
        }
    }

    private suspend fun decideForCharacter(
        event: WorldOpportunity,
        characterId: String
    ): WorldCharacterDecision? {
        val candidates = if (characterId == event.initiatorCharacterId) INITIATOR_CANDIDATES else PARTICIPANT_CANDIDATES
        val result = cognitionEngine.cognize(
            characterId = characterId,
            candidates = candidates,
            triggerContext = "Possible world event at ${event.locationId}: ${event.description}. " +
                "This is an opportunity, not an instruction."
        ) ?: return null
        cognitionEngine.recordSummary(characterId, result, System.currentTimeMillis())
        return when (result.action) {
            "accept" -> WorldCharacterDecision.ACCEPT
            "reject" -> WorldCharacterDecision.REJECT
            "delay" -> WorldCharacterDecision.DELAY
            "substitute" -> WorldCharacterDecision.SUBSTITUTE
            else -> null
        }
    }

    private suspend fun confirmIfAgreed(
        event: WorldOpportunity,
        snapshot: WorldDecisionSnapshot,
        now: Long
    ): WorldDecisionOutcome {
        if (snapshot.userAccepted == false && event.userParticipation == UserParticipation.REQUIRED) {
            return cancel(event, snapshot, now)
        }
        val resolution = WorldDecisionPolicy.resolve(
            opportunity = event,
            decisions = snapshot.characterDecisions,
            userAccepted = snapshot.userAccepted == true
        )
        return when (resolution.resolution) {
            WorldDecisionResolution.CANCELLED -> cancel(event, snapshot, now)
            WorldDecisionResolution.DEFERRED, WorldDecisionResolution.INCOMPLETE -> defer(event, snapshot, now)
            WorldDecisionResolution.WAITING_FOR_USER -> {
                timelineManager.transition(event.id, WorldEventStatus.WAITING_FOR_USER, WorldDecisionCodec.encode(snapshot), now)
                waiting(event.id)
            }
            WorldDecisionResolution.CONFIRMED -> {
                val activity = activityManager.startWorldActivity(
                    eventId = event.id,
                    type = event.type,
                    characterIds = resolution.includedCharacterIds,
                    userParticipated = snapshot.userAccepted == true,
                    locationId = event.locationId,
                    durationMinutes = event.durationMinutes,
                    description = event.description,
                    now = now
                ) ?: return defer(event, snapshot, now)
                timelineManager.transition(event.id, WorldEventStatus.CONFIRMED, WorldDecisionCodec.encode(snapshot), now)
                WorldDecisionOutcome(event.id, WorldDecisionOutcomeType.CONFIRMED, activity.id)
            }
        }
    }

    private suspend fun cancel(
        event: WorldOpportunity,
        snapshot: WorldDecisionSnapshot,
        now: Long
    ): WorldDecisionOutcome {
        timelineManager.transition(event.id, WorldEventStatus.CANCELLED, WorldDecisionCodec.encode(snapshot), now)
        return WorldDecisionOutcome(event.id, WorldDecisionOutcomeType.CANCELLED)
    }

    private suspend fun defer(
        event: WorldOpportunity,
        snapshot: WorldDecisionSnapshot,
        now: Long
    ): WorldDecisionOutcome {
        val retryAt = minOf(now + RETRY_DELAY_MS, event.latestStartAt)
        val deferred = snapshot.copy(nextAttemptAt = retryAt)
        timelineManager.transition(event.id, WorldEventStatus.PLANNED, WorldDecisionCodec.encode(deferred), now)
        return deferred(event.id)
    }

    private fun unchanged(eventId: String) = WorldDecisionOutcome(eventId, WorldDecisionOutcomeType.UNCHANGED)
    private fun deferred(eventId: String) = WorldDecisionOutcome(eventId, WorldDecisionOutcomeType.DEFERRED)
    private fun waiting(eventId: String) = WorldDecisionOutcome(eventId, WorldDecisionOutcomeType.WAITING_FOR_USER)

    private companion object {
        val INITIATOR_CANDIDATES = listOf(
            CandidateAction("accept", "world", "Start or invite participants", "character"),
            CandidateAction("reject", "world", "Do not start this event", "character"),
            CandidateAction("delay", "world", "Delay the event", "character"),
            CandidateAction("substitute", "world", "Choose a different activity later", "character")
        )
        val PARTICIPANT_CANDIDATES = listOf(
            CandidateAction("accept", "world", "Accept the invitation", "character"),
            CandidateAction("reject", "world", "Decline the invitation", "character"),
            CandidateAction("delay", "world", "Respond later", "character"),
            CandidateAction("substitute", "world", "Suggest a different activity later", "character")
        )
        val TERMINAL_OR_CONFIRMED = setOf(
            WorldEventStatus.CONFIRMED,
            WorldEventStatus.FINISHED,
            WorldEventStatus.CANCELLED,
            WorldEventStatus.SUPERSEDED
        )
        const val RETRY_DELAY_MS = 5 * 60_000L
    }
}
