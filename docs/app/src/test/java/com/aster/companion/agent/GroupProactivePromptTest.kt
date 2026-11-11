package com.aster.companion.agent

import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.domain.GroupProactiveIntent
import com.aster.companion.domain.GroupTurnPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupProactivePromptTest {
    @Test
    fun `selection prompt makes contextual continuation and new topic first class choices`() {
        val prompt = GroupProactivePrompt.selectionSystemPrompt(GroupAtmosphere.DAILY)

        assertTrue(prompt.contains("CONTINUE"))
        assertTrue(prompt.contains("START_TOPIC"))
        assertTrue(prompt.contains("invite another character"))
        assertTrue(prompt.contains("Do not invent user facts"))
    }

    @Test
    fun `invited follow up reacts to the main speaker instead of reopening the topic`() {
        val directive = GroupProactivePrompt.replyDirective(
            plan = GroupTurnPlan(
                mainSpeakerId = "a",
                followUpSpeakerIds = listOf("b"),
                intent = GroupProactiveIntent.START_TOPIC,
                topic = "聊聊今天的晚饭",
                invitedSpeakerId = "b"
            ),
            responderId = "b",
            responderName = "乙",
            previousSpeakerName = "甲"
        )

        assertTrue(directive.contains("Respond to the main speaker"))
        assertTrue(!directive.contains("Open the grounded topic in your own voice"))
    }

    @Test
    fun `previous speaker is only the immediately preceding visible line`() {
        val lines = listOf(
            GroupTranscriptLine("m1", "a", "甲", false, "旧话题"),
            GroupTranscriptLine("u1", null, "用户", true, "我刚说完")
        )

        assertEquals(null, GroupProactivePrompt.immediatePreviousSpeakerName(lines))
    }

    @Test
    fun `role context instruction keeps private continuity scoped to the current character`() {
        val instruction = GroupProactivePrompt.roleContextInstruction("岚")

        assertTrue(instruction.contains("岚's own private-chat continuity"))
        assertTrue(instruction.contains("Do not reveal it to other group members"))
    }
}
