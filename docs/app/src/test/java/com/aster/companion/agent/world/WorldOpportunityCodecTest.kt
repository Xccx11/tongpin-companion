package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldOpportunityCodecTest {

    @Test
    fun `parser accepts stable ids and registered locations`() {
        val parsed = WorldOpportunityCodec.parseDirectorResponse(
            raw = """{"events":[{"id":"cook","time_offset_minutes":20,"duration_minutes":30,"type":"meal","initiator_character_id":"linxi","required_character_ids":["linxi"],"location_id":"home.kitchen","description":"准备晚饭"}]}""",
            timelineId = "timeline-1",
            windowStartAt = 1_000L,
            knownCharacterIds = setOf("linxi"),
            knownLocationIds = setOf("home.kitchen")
        )

        assertEquals(1, parsed.size)
        assertEquals(1_201_000L, parsed.single().scheduledAt)
        assertEquals("linxi", parsed.single().initiatorCharacterId)
    }

    @Test
    fun `parser rejects display names and unknown locations`() {
        val parsed = WorldOpportunityCodec.parseDirectorResponse(
            raw = """{"events":[{"id":"bad-name","time_offset_minutes":20,"duration_minutes":30,"type":"meal","initiator_character_id":"林夕","required_character_ids":["林夕"],"location_id":"home.kitchen","description":"准备晚饭"},{"id":"bad-location","time_offset_minutes":30,"duration_minutes":30,"type":"rest","initiator_character_id":"linxi","required_character_ids":["linxi"],"location_id":"imaginary.room","description":"休息"}]}""",
            timelineId = "timeline-1",
            windowStartAt = 1_000L,
            knownCharacterIds = setOf("linxi"),
            knownLocationIds = setOf("home.kitchen")
        )

        assertTrue(parsed.isEmpty())
    }
}
