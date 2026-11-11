package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationResetGuardTest {
    @Test
    fun `events queued before or during a reset cannot recreate deleted context`() {
        assertFalse(ConversationResetGuard.mayEnterConversation(9_999L, 10_000L))
        assertFalse(ConversationResetGuard.mayEnterConversation(10_000L, 10_000L))
        assertTrue(ConversationResetGuard.mayEnterConversation(10_001L, 10_000L))
    }
}
