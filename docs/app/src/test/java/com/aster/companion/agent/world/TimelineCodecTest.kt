package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineCodecTest {
    @Test
    fun `legacy compatibility snapshot round trips special characters`() {
        val events = listOf(
            PlannedEvent(
                id = "e1",
                time = "19:20",
                durationMinutes = 30,
                type = "activity",
                participants = listOf("linxi", "gongyuli"),
                description = "She said: \"come over\""
            )
        )

        val restored = LegacyTimelineCodec.decode(LegacyTimelineCodec.encode(events))

        assertEquals(events, restored)
    }

    @Test
    fun `persistence mapper preserves candidate data without living state fields`() {
        val opportunity = WorldOpportunity(
            id = "event-1",
            timelineId = "timeline-1",
            scheduledAt = 10L,
            latestStartAt = 20L,
            durationMinutes = 15,
            type = "meal",
            initiatorCharacterId = "linxi",
            requiredCharacterIds = listOf("linxi"),
            locationId = "home.kitchen",
            description = "prepare dinner",
            prerequisiteEventIds = listOf("prep")
        )

        val entity = WorldOpportunityPersistence.toEntity(opportunity, now = 100L)
        val restored = WorldOpportunityPersistence.fromEntity(entity)

        assertEquals(opportunity, restored)
        assertTrue(entity::class.java.declaredFields.none { it.name.contains("Activity", ignoreCase = true) })
    }
}
