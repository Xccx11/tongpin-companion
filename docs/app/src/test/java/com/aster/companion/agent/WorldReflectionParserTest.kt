package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldReflectionParserTest {
    @Test
    fun `keeps private and shared worlds separate`() {
        val reflection = WorldReflectionParser.parse(
            """{"private_world":"She has been sketching a rainy street at home.","shared_world":"You both agreed to save a quiet evening for the weekend."}"""
        )

        assertEquals("She has been sketching a rainy street at home.", reflection.privateWorld)
        assertEquals("You both agreed to save a quiet evening for the weekend.", reflection.sharedWorld)
    }
}
