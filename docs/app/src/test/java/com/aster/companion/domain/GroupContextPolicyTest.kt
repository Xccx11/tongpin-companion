package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupContextPolicyTest {
    @Test
    fun `a character can carry its own private continuity into group context`() {
        assertTrue(GroupContextPolicy.includePrivateCharacterMemory(ConversationType.GROUP))
        assertTrue(GroupContextPolicy.includePrivateCharacterMemory(ConversationType.PRIVATE))
    }

    @Test
    fun `private conversation visibility is scoped to the current character`() {
        assertTrue(GroupContextPolicy.isPrivateMessageVisibleToCharacter("lan", null))
        assertTrue(GroupContextPolicy.isPrivateMessageVisibleToCharacter("lan", "lan"))
        assertFalse(GroupContextPolicy.isPrivateMessageVisibleToCharacter("lan", "yuan"))
    }

    @Test
    fun `only structured short summaries can be shared from private chat`() {
        assertTrue(GroupContextPolicy.canSharePrivateSummary("I will be home late tonight"))
        assertFalse(GroupContextPolicy.canSharePrivateSummary("content://private/photo.jpg"))
        assertFalse(GroupContextPolicy.canSharePrivateSummary("notification raw payload: package=com.example"))
        assertFalse(GroupContextPolicy.canSharePrivateSummary("A".repeat(281)))
    }
}
