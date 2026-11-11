package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class PlannerMessagePolicyTest {
    @Test
    fun `uses the planner message when natural rendering is empty`() {
        assertEquals("Come eat while it is warm.", PlannerMessagePolicy.select("Come eat while it is warm.", "   "))
    }
}
