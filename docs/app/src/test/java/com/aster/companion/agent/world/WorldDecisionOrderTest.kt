package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldDecisionOrderTest {
    @Test
    fun `multi party decision asks initiator before other required characters`() {
        val order = WorldDecisionOrder.characterIdsFor(
            opportunity(initiator = "linxi", required = listOf("gongyuli", "linxi", "xiaohe"))
        )

        assertEquals(listOf("linxi", "gongyuli", "xiaohe"), order)
    }

    @Test
    fun `single character event asks that character once`() {
        val order = WorldDecisionOrder.characterIdsFor(opportunity(initiator = null, required = listOf("linxi")))

        assertEquals(listOf("linxi"), order)
    }

    private fun opportunity(initiator: String?, required: List<String>) = WorldOpportunity(
        id = "event",
        timelineId = "timeline",
        scheduledAt = 1L,
        latestStartAt = 2L,
        durationMinutes = 10,
        type = "activity",
        initiatorCharacterId = initiator,
        requiredCharacterIds = required,
        locationId = "home.living_room",
        description = "chat"
    )
}
