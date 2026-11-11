package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendDeletionEligibilityTest {
    @Test
    fun `blocks friend deletion while the character remains in a group`() {
        assertEquals(
            FriendDeletionEligibility.BlockedByGroups(listOf("周末小组")),
            FriendDeletionEligibility.fromGroupTitles(listOf("周末小组"))
        )
    }

    @Test
    fun `allows friend deletion when the character has no group memberships`() {
        assertEquals(
            FriendDeletionEligibility.Allowed,
            FriendDeletionEligibility.fromGroupTitles(emptyList())
        )
    }
}
