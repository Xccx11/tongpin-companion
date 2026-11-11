package com.aster.companion.agent.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldMemoryPolicyTest {
    @Test
    fun `multi character event receives participant scoped memory`() {
        assertTrue(
            WorldMemoryPolicy.shouldWriteParticipantMemory(
                participants = listOf("linxi", "gongyuli"),
                userParticipated = false,
                expectedEffectsJson = "{}"
            )
        )
    }

    @Test
    fun `ordinary solo routine remains objective history only`() {
        assertFalse(
            WorldMemoryPolicy.shouldWriteParticipantMemory(
                participants = listOf("linxi"),
                userParticipated = false,
                expectedEffectsJson = "{}"
            )
        )
    }

    @Test
    fun `declared world effects make a solo event memorable`() {
        assertTrue(
            WorldMemoryPolicy.shouldWriteParticipantMemory(
                participants = listOf("linxi"),
                userParticipated = false,
                expectedEffectsJson = "{\"relationship\":true}"
            )
        )
    }
}
