package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionInvariantsTest {
    @Test
    fun `proactive group message is accepted when it has visible content`() {
        val result = CompanionInvariants.validateDecision(
            AgentDecision(
                kind = AgentDecisionKind.SEND_NOW,
                threadType = ConversationType.GROUP,
                message = "A group member started a conversation."
            ),
            CompanionCapabilitySnapshot.empty()
        )

        assertEquals(DecisionValidation.Accepted, result)
    }
}
