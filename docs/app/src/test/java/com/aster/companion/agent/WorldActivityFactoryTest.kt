package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorldActivityFactoryTest {
    @Test
    fun `confirmed world activity uses character ids and explicit user metadata`() {
        val activity = WorldActivityFactory.create(
            eventId = "dinner",
            type = "meal",
            characterIds = listOf("linxi", "gongyuli"),
            userParticipated = true,
            locationId = "home.kitchen",
            durationMinutes = 30,
            now = 100L
        )

        requireNotNull(activity)
        assertEquals("world-activity:dinner", activity.id)
        assertEquals(listOf("linxi", "gongyuli"), ActivityParticipantCodec.decode(activity.participantsJson).characterIds)
        assertEquals(true, ActivityParticipantCodec.decode(activity.participantsJson).userParticipated)
    }

    @Test
    fun `world activity rejects empty participant or invalid duration`() {
        assertNull(WorldActivityFactory.create("e", "meal", emptyList(), false, "home.kitchen", 20, 1L))
        assertNull(WorldActivityFactory.create("e", "meal", listOf("linxi"), false, "home.kitchen", 0, 1L))
    }
}
