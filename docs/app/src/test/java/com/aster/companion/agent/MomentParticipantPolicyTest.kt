package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class MomentParticipantPolicyTest {
    @Test
    fun `a character post invites every other role but never asks its author to react to itself`() {
        assertEquals(
            listOf("spring", "autumn"),
            MomentParticipantPolicy.reactorsForPost(listOf("summer", "spring", "autumn"), "summer")
        )
    }

    @Test
    fun `the post author is included first when roles can answer a user comment`() {
        assertEquals(
            listOf("summer", "spring", "autumn"),
            MomentParticipantPolicy.repliersForUserComment(listOf("summer", "spring", "autumn"), "summer")
        )
    }
}
