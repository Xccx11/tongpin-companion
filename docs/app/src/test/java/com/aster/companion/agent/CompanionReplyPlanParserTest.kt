package com.aster.companion.agent

import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.ConfirmationRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionReplyPlanParserTest {
    @Test
    fun `extracts a natural reply and a model chosen action plan`() {
        val plan = CompanionReplyPlanParser.parse("""{"decision":"tool_calls","message":"我帮你定到七点，确认一下。","tool_calls":[{"capability_id":"device.set_alarm","transport":"shizuku","confirmation":"required","arguments":{"hour":"7","minute":"0"}}]}""")

        requireNotNull(plan)
        assertEquals("我帮你定到七点，确认一下。", plan.message)
        assertEquals("device.set_alarm", plan.actions.single().capabilityId)
        assertEquals(ActionTransport.SHIZUKU, plan.actions.single().transport)
        assertEquals(ConfirmationRequirement.REQUIRED, plan.actions.single().confirmation)
    }

    @Test
    fun `leaves an ordinary chat reply untouched`() {
        assertNull(CompanionReplyPlanParser.parse("我在呢，先忙你的。"))
    }

    @Test
    fun `extracts an embedded tool plan with provider style enum values`() {
        val plan = CompanionReplyPlanParser.parse(
            "哼，那就再试一次！\n{\"decision\":\"tool_calls\",\"message\":\"行，17:05 对吧，我再试一次。\",\"tool_calls\":[{\"capability_id\":\"device.set_alarm\",\"transport\":\"SHIZUKU\",\"confirmation\":\"确认将闹钟设为17:05\",\"arguments\":{\"hour\":\"17\",\"minute\":\"5\"}}]}"
        )

        requireNotNull(plan)
        assertEquals("行，17:05 对吧，我再试一次。", plan.message)
        assertEquals(ActionTransport.SHIZUKU, plan.actions.single().transport)
        assertEquals(ConfirmationRequirement.REQUIRED, plan.actions.single().confirmation)
    }
}
