package com.aster.companion.agent

import com.aster.companion.domain.AgentDecisionKind

object UrgentProactiveDeliveryPolicy {
    fun needsFallback(isConfirmedDelivery: Boolean, decision: AgentDecisionKind): Boolean =
        isConfirmedDelivery && decision != AgentDecisionKind.SEND_NOW

    /**
     * Delivery fallback text is generated from one confirmed notification and
     * explicitly constrained to that fact. The generic LLM grounding reviewer
     * may reject it nondeterministically because the current trigger is not in
     * its fact list, so only this narrow path bypasses that second model call.
     */
    fun mayDeliverWithoutGenericGrounding(
        isConfirmedDelivery: Boolean,
        usedDeliveryFallback: Boolean
    ): Boolean = isConfirmedDelivery
}
