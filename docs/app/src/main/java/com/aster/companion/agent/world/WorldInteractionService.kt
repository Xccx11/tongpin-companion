package com.aster.companion.agent.world

import javax.inject.Inject
import javax.inject.Singleton

/** Records user world participation and applies only direct/dependent candidate impact. */
@Singleton
class WorldInteractionService @Inject constructor(
    private val worldStateRepository: WorldStateRepository,
    private val timelineManager: TimelineManager,
    private val impactAnalyzer: WorldImpactAnalyzer = WorldImpactAnalyzer()
) {
    suspend fun record(interaction: WorldInteraction): WorldImpactReport {
        val now = interaction.occurredAt
        worldStateRepository.recordUserState(
            locationId = interaction.locationId,
            visibility = "VISIBLE",
            action = interaction.detail,
            now = now
        )
        val report = impactAnalyzer.analyze(interaction, timelineManager.activeOpportunities())
        report.impacts.forEach { (eventId, impact) ->
            timelineManager.applyImpact(eventId, impact, now)
        }
        return report
    }

    suspend fun recordUserNotVisible(
        locationId: String,
        lastAction: String,
        now: Long = System.currentTimeMillis()
    ) {
        worldStateRepository.recordUserState(
            locationId = locationId,
            visibility = "NOT_VISIBLE",
            action = lastAction,
            now = now
        )
    }

    suspend fun recordCurrentUserNotVisible(
        lastAction: String,
        now: Long = System.currentTimeMillis()
    ) {
        val currentLocationId = worldStateRepository.snapshot(now).user.locationId
        recordUserNotVisible(currentLocationId, lastAction, now)
    }
}
