package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupConversationWindowTest {
    @Test
    fun `keeps a bounded recent group transcript with a clear truncation marker`() {
        val window = GroupConversationWindow.forModel(
            listOf(
                GroupTranscriptLine("1", null, "User", true, "old one"),
                GroupTranscriptLine("2", "lan", "岚", false, "old two"),
                GroupTranscriptLine("3", null, "User", true, "new one"),
                GroupTranscriptLine("4", "xue", "雪", false, "new two")
            ),
            maxMessages = 2
        )

        assertEquals(2, window.lines.size)
        assertTrue(window.truncated)
        assertEquals("new one", window.lines.first().body)
        assertTrue(window.renderedTranscript.contains("OLDER_GROUP_MESSAGES_OMITTED"))
        assertTrue(window.renderedTranscript.contains("speaker_name=雪"))
    }
}
