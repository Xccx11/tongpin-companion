package com.aster.companion.agent

import com.aster.companion.proactive.RelationshipPulseKind
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveDecisionInstructionTest {
    @Test fun `delivered notification requires an in character send decision`() {
        val instruction = ProactiveDecisionInstruction.forEvent("notification:com.sankuai.meituan", "订单已送达，已放在门口")

        assertTrue(instruction.contains("send_now"))
    }

    @Test fun `ordinary proactive planning treats life snapshot as decision input rather than chat decoration`() {
        val instruction = ProactiveDecisionInstruction.forEvent("device:battery", "battery is low")

        assertTrue(instruction.contains("verified current-life facts"))
    }

    @Test fun `presence pulse asks for a fresh relationship move rather than an old answer`() {
        val instruction = ProactiveDecisionInstruction.forEvent(
            "system:presence:thread:character:presence",
            "pulse",
            RelationshipPulseKind.PRESENCE
        )

        assertTrue(instruction.contains("not a request to repeat any old answer"))
    }
    @Test fun `follow up pulse requires a fresh message when proactive mode is active`() {
        val instruction = ProactiveDecisionInstruction.forEvent(
            "system:presence:thread:character:follow_up",
            "pulse",
            RelationshipPulseKind.FOLLOW_UP
        )

        assertTrue(instruction.contains("must send"))
        assertTrue(instruction.contains("not repeat"))
        assertTrue(instruction.contains("not the user's last message alone"))
    }
}
