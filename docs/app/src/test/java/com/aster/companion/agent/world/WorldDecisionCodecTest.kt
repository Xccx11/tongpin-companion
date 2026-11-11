package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorldDecisionCodecTest {
    @Test
    fun `decision snapshot retains character responses and explicit user acceptance`() {
        val snapshot = WorldDecisionSnapshot(
            characterDecisions = mapOf("linxi" to WorldCharacterDecision.ACCEPT),
            userAccepted = true
        )

        val restored = WorldDecisionCodec.decode(WorldDecisionCodec.encode(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun `missing user response is not treated as acceptance`() {
        val restored = WorldDecisionCodec.decode("{\"characters\":{\"linxi\":\"ACCEPT\"}}")

        assertNull(restored.userAccepted)
    }
}
