package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionAvailabilityTargetPolicyTest {
    @Test
    fun `availability wording in a private chat affects only its companion`() {
        assertEquals(
            listOf("character-a"),
            CompanionAvailabilityTargetPolicy.resolve(
                conversationType = ConversationType.PRIVATE,
                participantIds = listOf("character-a")
            )
        )
    }

    @Test
    fun `availability wording in a group pauses every participating role`() {
        assertEquals(
            listOf("character-a", "character-b"),
            CompanionAvailabilityTargetPolicy.resolve(
                conversationType = ConversationType.GROUP,
                participantIds = listOf("character-a", "character-b")
            )
        )
    }
}
