package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextVersionTest {
    @Test
    fun `rejects a reply that was started before the conversation was cleared`() {
        assertFalse(ConversationContextVersion.isCurrent(requestVersion = 3, currentVersion = 4))
        assertTrue(ConversationContextVersion.isCurrent(requestVersion = 4, currentVersion = 4))
    }
}
