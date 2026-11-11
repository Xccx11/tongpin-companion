package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldSchedulerEventPolicyTest {
    @Test
    fun `due policy separates evaluable and expired candidates`() {
        val result = WorldSchedulerEventPolicy.classify(
            now = 100L,
            opportunities = listOf(
                event("due", scheduledAt = 90L, latestStartAt = 110L),
                event("later", scheduledAt = 101L, latestStartAt = 120L),
                event("expired", scheduledAt = 50L, latestStartAt = 99L)
            )
        )

        assertEquals(listOf("due"), result.dueIds)
        assertEquals(listOf("expired"), result.expiredIds)
    }

    private fun event(id: String, scheduledAt: Long, latestStartAt: Long) = WorldOpportunity(
        id = id,
        timelineId = "t",
        scheduledAt = scheduledAt,
        latestStartAt = latestStartAt,
        durationMinutes = 10,
        type = "activity",
        initiatorCharacterId = "linxi",
        requiredCharacterIds = listOf("linxi"),
        locationId = "home.living_room",
        description = id
    )
}
