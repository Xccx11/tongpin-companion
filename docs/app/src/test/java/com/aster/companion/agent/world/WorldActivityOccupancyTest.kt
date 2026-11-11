package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldActivityOccupancyTest {
    @Test
    fun `confirmed world activity marks every character participant as occupied`() {
        val occupied = WorldActivityOccupancy.characterIds(
            listOf(
                "world-activity:meal" to "{\"character_ids\":[\"linxi\",\"gongyuli\"],\"user_participated\":true}",
                "activity:legacy" to "{\"sender\":\"xiaohe\",\"receiver\":\"linxi\"}"
            )
        )

        assertEquals(setOf("linxi", "gongyuli"), occupied)
    }
}
