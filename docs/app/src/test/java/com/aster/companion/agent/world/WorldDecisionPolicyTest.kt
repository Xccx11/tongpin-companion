package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldDecisionPolicyTest {
    private val multiParty = WorldOpportunity(
        id = "dinner",
        timelineId = "timeline",
        scheduledAt = 1L,
        latestStartAt = 2L,
        durationMinutes = 30,
        type = "meal",
        initiatorCharacterId = "linxi",
        requiredCharacterIds = listOf("linxi", "gongyuli"),
        locationId = "home.kitchen",
        description = "prepare dinner"
    )

    @Test
    fun `initiator rejection cancels a multi party opportunity`() {
        val result = WorldDecisionPolicy.resolve(
            multiParty,
            mapOf("linxi" to WorldCharacterDecision.REJECT)
        )

        assertEquals(WorldDecisionResolution.CANCELLED, result.resolution)
    }

    @Test
    fun `required participant rejection cancels after initiator accepts`() {
        val result = WorldDecisionPolicy.resolve(
            multiParty,
            mapOf(
                "linxi" to WorldCharacterDecision.ACCEPT,
                "gongyuli" to WorldCharacterDecision.REJECT
            )
        )

        assertEquals(WorldDecisionResolution.CANCELLED, result.resolution)
    }

    @Test
    fun `delay keeps opportunity available for later reevaluation`() {
        val result = WorldDecisionPolicy.resolve(
            multiParty,
            mapOf("linxi" to WorldCharacterDecision.DELAY)
        )

        assertEquals(WorldDecisionResolution.DEFERRED, result.resolution)
    }

    @Test
    fun `required user participation waits for explicit user response`() {
        val result = WorldDecisionPolicy.resolve(
            multiParty.copy(userParticipation = UserParticipation.REQUIRED),
            mapOf(
                "linxi" to WorldCharacterDecision.ACCEPT,
                "gongyuli" to WorldCharacterDecision.ACCEPT
            )
        )

        assertEquals(WorldDecisionResolution.WAITING_FOR_USER, result.resolution)
    }

    @Test
    fun `optional user participation never blocks confirmed character activity`() {
        val result = WorldDecisionPolicy.resolve(
            multiParty.copy(userParticipation = UserParticipation.OPTIONAL),
            mapOf(
                "linxi" to WorldCharacterDecision.ACCEPT,
                "gongyuli" to WorldCharacterDecision.ACCEPT
            )
        )

        assertEquals(WorldDecisionResolution.CONFIRMED, result.resolution)
    }
}
