package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveMessageCooldownTest {
    @Test
    fun `ordinary proactive messages wait ninety seconds after the last companion message`() {
        val lastMessageAt = 1_000_000L

        assertFalse(ProactiveMessageCooldown.maySend(1_089_999L, lastMessageAt, urgent = false))
        assertTrue(ProactiveMessageCooldown.maySend(1_090_000L, lastMessageAt, urgent = false))
    }

    @Test
    fun `delivery notifications may still arrive immediately`() {
        assertTrue(ProactiveMessageCooldown.maySend(1_001_000L, 1_000_000L, urgent = true))
    }
}
