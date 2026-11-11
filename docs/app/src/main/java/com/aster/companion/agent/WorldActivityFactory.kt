package com.aster.companion.agent

import com.aster.companion.data.ActivityEntity

/** Builds an ActivityEntity only after the V3 agreement stage has confirmed it. */
object WorldActivityFactory {
    fun create(
        eventId: String,
        type: String,
        characterIds: List<String>,
        userParticipated: Boolean,
        locationId: String,
        durationMinutes: Int,
        now: Long
    ): ActivityEntity? {
        val participants = characterIds.distinct().filter(String::isNotBlank)
        if (eventId.isBlank() || type.isBlank() || locationId.isBlank()) return null
        if (participants.isEmpty() || durationMinutes !in 1..240) return null
        return ActivityEntity(
            id = "world-activity:$eventId",
            type = type,
            participantsJson = ActivityParticipantCodec.encodeWorld(participants, userParticipated),
            location = locationId,
            durationMinutes = durationMinutes,
            status = "running",
            startedAt = now,
            finishedAt = null,
            effectJson = "",
            createdAt = now
        )
    }
}
