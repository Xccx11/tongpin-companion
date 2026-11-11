package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldDirectorParseTest {
    private val characters = setOf("linxi", "gongyuli")
    private val locations = setOf("home.kitchen", "home.living_room")

    @Test
    fun `parses only constrained opportunity contract`() {
        val events = parse(
            """{"events":[{"id":"cook","time_offset_minutes":20,"latest_start_offset_minutes":25,"duration_minutes":30,"type":"meal","initiator_character_id":"linxi","required_character_ids":["linxi"],"location_id":"home.kitchen","description":"prepare dinner","prerequisites":{"ingredients":true}}]}"""
        )

        assertEquals(1, events.size)
        assertEquals(1_201_000L, events.single().scheduledAt)
        assertEquals(1_501_000L, events.single().latestStartAt)
        assertEquals("{\"ingredients\":true}", events.single().prerequisiteJson)
    }

    @Test
    fun `rejects user commanding or malformed events`() {
        val events = parse(
            """{"events":[
              {"id":"user-command","time_offset_minutes":5,"duration_minutes":10,"required_character_ids":["user"],"location_id":"home.kitchen","description":"wait for user"},
              {"id":"bad-prereq","time_offset_minutes":5,"duration_minutes":10,"required_character_ids":["linxi"],"location_id":"home.kitchen","description":"cook","prerequisites":"not-an-object"}
            ]}"""
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `rejects initiator who is not an actual event participant`() {
        val events = parse(
            """{"events":[{"id":"bad-initiator","time_offset_minutes":5,"duration_minutes":10,"initiator_character_id":"linxi","required_character_ids":["gongyuli"],"location_id":"home.kitchen","description":"prepare dinner"}]}"""
        )

        assertTrue(events.isEmpty())
    }

    private fun parse(raw: String) = WorldOpportunityCodec.parseDirectorResponse(
        raw = raw,
        timelineId = "timeline",
        windowStartAt = 1_000L,
        knownCharacterIds = characters,
        knownLocationIds = locations
    )
}
