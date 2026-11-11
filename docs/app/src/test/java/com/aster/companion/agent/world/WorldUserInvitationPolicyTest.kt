package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldUserInvitationPolicyTest {
    @Test
    fun `declining an optional invitation keeps the character event eligible`() {
        assertEquals(
            WorldUserInvitationOutcome.CONTINUE_WITHOUT_USER,
            WorldUserInvitationPolicy.afterResponse(UserParticipation.OPTIONAL, accepted = false)
        )
    }

    @Test
    fun `declining a required invitation cancels the event`() {
        assertEquals(
            WorldUserInvitationOutcome.CANCEL,
            WorldUserInvitationPolicy.afterResponse(UserParticipation.REQUIRED, accepted = false)
        )
    }
}
