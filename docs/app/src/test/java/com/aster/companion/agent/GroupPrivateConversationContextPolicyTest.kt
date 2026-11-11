package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupPrivateConversationContextPolicyTest {
    @Test
    fun `group responder receives only their own private chat continuity`() {
        val context = GroupPrivateConversationContextPolicy.render(
            ownerCharacterId = "xia",
            lines = listOf(
                GroupPrivateConversationContextPolicy.Line(null, true, "I am tired"),
                GroupPrivateConversationContextPolicy.Line("xia", false, "Rest for a while"),
                GroupPrivateConversationContextPolicy.Line("lan", false, "This must never leak")
            )
        )

        assertEquals(listOf("User: I am tired", "Character: Rest for a while"), context)
    }
}
