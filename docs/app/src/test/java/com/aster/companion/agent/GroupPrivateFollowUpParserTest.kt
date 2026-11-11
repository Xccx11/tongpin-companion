package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupPrivateFollowUpParserTest {
    @Test
    fun `parses a send decision with only the private message`() {
        val decision = GroupPrivateFollowUpParser.parse(
            """{"decision":"SEND","message":"刚才在群里看你说累了，我还惦记着。"}"""
        )

        assertEquals(true, decision.shouldSend)
        assertEquals("刚才在群里看你说累了，我还惦记着。", decision.message)
    }

    @Test
    fun `ignore decision never has a private message`() {
        val decision = GroupPrivateFollowUpParser.parse(
            """{"decision":"IGNORE","message":"不应该发送"}"""
        )

        assertEquals(false, decision.shouldSend)
        assertNull(decision.message)
    }

    @Test
    fun `invalid output becomes ignore instead of inventing a message`() {
        val decision = GroupPrivateFollowUpParser.parse("not json")

        assertEquals(false, decision.shouldSend)
        assertNull(decision.message)
    }
}
