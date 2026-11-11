package com.aster.companion.agent.world

import com.aster.companion.agent.ActivityManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent 30-second world heartbeat. Planning is rare: it only refills the
 * 60-minute rolling window once viable coverage drops below 30 minutes.
 */
@Singleton
class WorldScheduler @Inject constructor(
    private val worldStateRepository: WorldStateRepository,
    private val worldDirector: WorldDirector,
    private val timelineManager: TimelineManager,
    private val decisionCoordinator: WorldEventDecisionCoordinator,
    private val activityManager: ActivityManager,
    private val memoryRecorder: WorldMemoryRecorder
) {
    private val mutex = Mutex()

    suspend fun tick(now: Long = System.currentTimeMillis()) = mutex.withLock {
        val state = worldStateRepository.snapshot(now)
        val candidates = timelineManager.activeOpportunities()
        val classified = WorldSchedulerEventPolicy.classify(now, candidates)

        candidates.filter { it.id in classified.expiredIds }.forEach { event ->
            if (timelineManager.transition(event.id, WorldEventStatus.CANCELLED, event.decisionJson, now)) {
                memoryRecorder.recordSettled(event, outcome = "expired", now = now)
            }
        }
        candidates.filter { it.id in classified.dueIds }.forEach { event ->
            when (decisionCoordinator.evaluate(event.id, now).type) {
                WorldDecisionOutcomeType.CANCELLED -> memoryRecorder.recordSettled(event, "cancelled", now)
                else -> Unit
            }
        }

        activityManager.updateRunningActivities(now)
            .filter { it.id.startsWith(WORLD_ACTIVITY_PREFIX) }
            .forEach { activity ->
                val eventId = activity.id.removePrefix(WORLD_ACTIVITY_PREFIX)
                val event = timelineManager.opportunity(eventId) ?: return@forEach
                if (timelineManager.transition(event.id, WorldEventStatus.FINISHED, event.decisionJson, now)) {
                    memoryRecorder.recordSettled(event, "finished", now)
                }
            }

        worldStateRepository.markAdvanced(now)
        refillWindow(state, now)
    }

    suspend fun recoverOnAppStart(now: Long = System.currentTimeMillis()) {
        val state = worldStateRepository.snapshot(now)
        val recovery = WorldRecoveryPolicy.plan(state.world.lastAdvancedAt, now)
        tick(now)
        if (recovery.requiresSummary) {
            memoryRecorder.recordRecoverySummary(now - state.world.lastAdvancedAt, now)
        }
    }

    suspend fun respondToUserInvitation(
        eventId: String,
        accepted: Boolean,
        now: Long = System.currentTimeMillis()
    ): WorldDecisionOutcome {
        val outcome = decisionCoordinator.respondToUserInvitation(eventId, accepted, now)
        if (outcome.type == WorldDecisionOutcomeType.CANCELLED) {
            timelineManager.opportunity(eventId)?.let { event ->
                memoryRecorder.recordSettled(event, "cancelled", now, userParticipated = false)
            }
        }
        return outcome
    }

    private suspend fun refillWindow(state: WorldEngineSnapshot, now: Long) {
        val viableUntil = timelineManager.viableUntil(now)
        if (!WorldSchedulerPolicy.shouldPlan(now, viableUntil, state.world.planRetryAfter)) return

        val timelineId = "timeline:$now"
        val opportunities = runCatching {
            worldDirector.generateOpportunities(
                snapshot = state,
                timelineId = timelineId,
                windowStartAt = now
            )
        }.getOrDefault(emptyList())

        if (opportunities.isEmpty()) {
            worldStateRepository.recordPlanningAttempt(
                now = now,
                retryAfter = WorldSchedulerPolicy.nextRetryAfter(
                    now = now,
                    lastPlanAttemptAt = state.world.lastPlanAttemptAt,
                    previousRetryAfter = state.world.planRetryAfter
                )
            )
            return
        }

        timelineManager.saveOpportunities(
            timelineId = timelineId,
            windowStartAt = now,
            windowEndAt = now + WorldSchedulerPolicy.TARGET_HORIZON_MS,
            opportunities = opportunities,
            now = now
        )
        worldStateRepository.recordPlanningAttempt(now, retryAfter = 0L)
    }

    private companion object {
        const val WORLD_ACTIVITY_PREFIX = "world-activity:"
    }
}
