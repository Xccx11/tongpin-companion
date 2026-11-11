package com.aster.companion.agent.world

import javax.inject.Inject
import javax.inject.Singleton

/** Compatibility facade for existing chat callers. It no longer cancels whole timelines. */
@Singleton
class InterruptManager @Inject constructor(
    private val worldStateRepository: WorldStateRepository,
    private val interactionService: WorldInteractionService
) {
    suspend fun onUserAction(
        userAction: String,
        targetCharacterIds: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val snapshot = worldStateRepository.snapshot(now)
        val report = interactionService.record(
            WorldInteraction(
                type = WorldInteractionType.CHAT,
                locationId = snapshot.user.locationId,
                targetCharacterIds = targetCharacterIds,
                occurredAt = now,
                detail = userAction
            )
        )
        return report.impacts.isNotEmpty()
    }

    suspend fun onUserNotVisible(lastLocation: String, lastAction: String) {
        interactionService.recordUserNotVisible(lastLocation, lastAction)
    }
}
