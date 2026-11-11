package com.aster.companion.agent

import com.aster.companion.domain.AgentDecisionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgentProactiveDeliveryPolicyTest {
    @Test fun `confirmed delivery falls back when the planner does not send`() {
        assertTrue(UrgentProactiveDeliveryPolicy.needsFallback(true, AgentDecisionKind.IGNORE))
    }

    @Test fun `ordinary events never force a fallback`() {
        assertTrue(!UrgentProactiveDeliveryPolicy.needsFallback(false, AgentDecisionKind.IGNORE))
    }

    @Test fun `any confirmed delivery can skip the generic grounding model`() {
        assertTrue(UrgentProactiveDeliveryPolicy.mayDeliverWithoutGenericGrounding(true, true))
        assertTrue(UrgentProactiveDeliveryPolicy.mayDeliverWithoutGenericGrounding(true, false))
        assertFalse(UrgentProactiveDeliveryPolicy.mayDeliverWithoutGenericGrounding(false, true))
    }
}
