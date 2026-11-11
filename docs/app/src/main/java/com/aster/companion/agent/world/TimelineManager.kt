package com.aster.companion.agent.world

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.FutureTimelineEntity
import com.aster.companion.data.WorldEventEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Owns candidate persistence. It never writes character living state. */
@Singleton
class TimelineManager @Inject constructor(
    private val dao: CompanionDao
) {
    suspend fun saveOpportunities(
        timelineId: String,
        windowStartAt: Long,
        windowEndAt: Long,
        opportunities: List<WorldOpportunity>,
        now: Long = System.currentTimeMillis()
    ) {
        val normalized = opportunities.filter { it.timelineId == timelineId }
        dao.upsertFutureTimeline(
            FutureTimelineEntity(
                id = timelineId,
                startTime = windowStartAt,
                endTime = windowEndAt,
                eventsJson = LegacyTimelineCodec.encode(normalized.map { it.toLegacyPlannedEvent() }),
                status = "planned",
                createdAt = now
            )
        )
        normalized.forEach { dao.upsertWorldTimelineEvent(WorldOpportunityPersistence.toEntity(it, now)) }
    }

    /** Compatibility entry point. New runtime code should call [saveOpportunities]. */
    suspend fun savePlan(timeline: FutureTimeline) {
        dao.upsertFutureTimeline(
            FutureTimelineEntity(
                id = timeline.id,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
                eventsJson = LegacyTimelineCodec.encode(timeline.events),
                status = timeline.status,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun activeOpportunities(): List<WorldOpportunity> =
        dao.activeWorldTimelineEvents().map(WorldOpportunityPersistence::fromEntity)

    suspend fun opportunity(eventId: String): WorldOpportunity? =
        dao.worldTimelineEvent(eventId)?.let(WorldOpportunityPersistence::fromEntity)

    suspend fun viableUntil(now: Long): Long? = activeOpportunities()
        .filter { it.status in setOf(WorldEventStatus.PLANNED, WorldEventStatus.EVALUATING, WorldEventStatus.WAITING_FOR_USER) }
        .filter { it.latestStartAt >= now }
        .maxOfOrNull { maxOf(it.scheduledAt, it.latestStartAt) + it.durationMinutes * MINUTE_MS }

    suspend fun transition(
        eventId: String,
        target: WorldEventStatus,
        decisionJson: String = "{}",
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val event = opportunity(eventId) ?: return false
        if (WorldEventLifecycle.transition(event.status, target) == null) return false
        dao.updateWorldTimelineEvent(eventId, target.name, decisionJson, now)
        return true
    }

    suspend fun applyImpact(
        eventId: String,
        impact: WorldEventImpact,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val event = opportunity(eventId) ?: return false
        val status = when (impact) {
            WorldEventImpact.KEEP -> return true
            WorldEventImpact.RESCHEDULE, WorldEventImpact.REEVALUATE ->
                WorldEventLifecycle.transition(event.status, WorldEventStatus.PLANNED) ?: return false
            WorldEventImpact.CANCEL ->
                WorldEventLifecycle.transition(event.status, WorldEventStatus.CANCELLED) ?: return false
        }
        dao.updateWorldTimelineEvent(
            eventId,
            status.name,
            "{\"impact\":\"${impact.name}\"}",
            now
        )
        return true
    }

    /**
     * Kept for legacy callers. Settling a legacy event records objective history
     * only; it intentionally cannot overwrite character activity.
     */
    suspend fun executeEvent(event: PlannedEvent, now: Long) {
        dao.upsertWorldEvent(
            WorldEventEntity(
                id = "world-event:${UUID.randomUUID()}",
                eventType = event.type,
                participants = event.participants.joinToString(","),
                eventTime = now,
                result = event.description,
                memory = "",
                createdAt = now
            )
        )
    }

    suspend fun getCurrentPlan(): FutureTimeline? {
        val entity = dao.activeTimelines().firstOrNull() ?: return null
        return FutureTimeline(
            id = entity.id,
            startTime = entity.startTime,
            endTime = entity.endTime,
            events = LegacyTimelineCodec.decode(entity.eventsJson),
            status = entity.status
        )
    }

    suspend fun updateStatus(id: String, status: String) = dao.updateTimelineStatus(id, status)
    suspend fun cancelPlan(id: String) = dao.deleteTimeline(id)

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
