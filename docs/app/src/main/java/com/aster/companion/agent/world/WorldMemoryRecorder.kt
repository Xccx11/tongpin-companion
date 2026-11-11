package com.aster.companion.agent.world

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.MemoryEntity
import com.aster.companion.data.WorldEventEntity
import com.aster.companion.domain.MemoryKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Writes objective history first and selective character-scoped memories second. */
@Singleton
class WorldMemoryRecorder @Inject constructor(
    private val dao: CompanionDao
) {
    suspend fun recordSettled(
        event: WorldOpportunity,
        outcome: String,
        now: Long,
        userParticipated: Boolean = WorldDecisionCodec.decode(event.decisionJson).userAccepted == true
    ) {
        val participants = event.requiredCharacterIds.distinct()
        val result = "$outcome: ${event.description}"
        dao.upsertWorldEvent(
            WorldEventEntity(
                id = "world-event:${event.id}",
                eventType = event.type,
                participants = participants.joinToString(","),
                eventTime = now,
                result = result,
                memory = "",
                createdAt = now
            )
        )
        if (!WorldMemoryPolicy.shouldWriteParticipantMemory(participants, userParticipated, event.expectedEffectsJson)) return
        participants.forEach { characterId ->
            dao.upsertMemory(
                MemoryEntity(
                    id = "memory:world:${event.id}:$characterId",
                    characterId = characterId,
                    threadId = null,
                    kind = MemoryKind.EVENT,
                    content = result.take(MAX_MEMORY_LENGTH),
                    blockedFromMention = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun recordRecoverySummary(elapsedMs: Long, now: Long) {
        dao.upsertWorldEvent(
            WorldEventEntity(
                id = "world-recovery:$now",
                eventType = "offline_summary",
                participants = "",
                eventTime = now,
                result = "Summarized ${(elapsedMs / 60_000L)} minutes of offline routine history.",
                memory = "",
                createdAt = now
            )
        )
    }

    private companion object {
        const val MAX_MEMORY_LENGTH = 240
    }
}
