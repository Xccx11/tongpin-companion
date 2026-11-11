package com.aster.companion.agent

/** The final rendered copy, not the planner draft, is the text that must be grounded. */
object ProactiveDeliveryTextPolicy {
    fun finalText(planned: String, rendered: String?): String =
        RoleplayResponseSanitizer.clean(PlannerMessagePolicy.select(planned, rendered))
}
