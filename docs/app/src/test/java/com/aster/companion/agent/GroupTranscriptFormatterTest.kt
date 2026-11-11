package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupTranscriptFormatterTest {
    @Test
    fun `keeps user and character messages as different factual speakers`() {
        val transcript = GroupTranscriptFormatter.render(
            listOf(
                GroupTranscriptLine("u1", null, "用户", true, "我今天有点累"),
                GroupTranscriptLine("m1", "xue", "雪", false, "那你先歇一会儿"),
                GroupTranscriptLine("m2", "lan", "岚", false, "我也想陪着你")
            )
        )

        assertTrue(transcript.contains("speaker_type=USER; speaker_id=user; speaker_name=用户"))
        assertTrue(transcript.contains("speaker_type=CHARACTER; speaker_id=xue; speaker_name=雪"))
        assertTrue(transcript.contains("speaker_type=CHARACTER; speaker_id=lan; speaker_name=岚"))
        assertFalse(transcript.contains("speaker_type=USER; speaker_id=xue"))
    }

    @Test
    fun `reply request makes the complete transcript authoritative and permits replying to another character`() {
        val prompt = GroupTranscriptFormatter.replyRequest(
            responderId = "lan",
            responderName = "岚",
            userText = "你们聊什么呢",
            transcript = "speaker_type=CHARACTER; speaker_id=xue; speaker_name=雪; text=我刚才说完了"
        )

        assertTrue(prompt.contains("only authoritative record"))
        assertTrue(prompt.contains("reply directly to another character"))
        assertTrue(prompt.contains("must not treat a character message as a user message"))
        assertTrue(prompt.contains("responder_id=lan"))
        assertTrue(prompt.contains("你们聊什么呢"))
    }

    @Test
    fun `proactive reply request carries an explicit topic directive`() {
        val prompt = GroupTranscriptFormatter.replyRequest(
            responderId = "lan",
            responderName = "岚",
            userText = "No new user message; this is a planned group turn.",
            transcript = "speaker_type=USER; speaker_id=user; text=我今天有点累",
            proactiveDirective = "Open a small topic grounded in the transcript and invite yuan to join naturally."
        )

        assertTrue(prompt.contains("Open a small topic grounded in the transcript"))
        assertTrue(prompt.contains("must not invent a missing user utterance"))
    }

    @Test
    fun `reply request can leave transcript in ordered history messages`() {
        val prompt = GroupTranscriptFormatter.replyRequest(
            responderId = "lan",
            responderName = "岚",
            userText = "你们聊什么呢",
            transcript = "speaker_type=CHARACTER; speaker_id=xue; speaker_name=雪; text=完整历史不应重复注入",
            includeTranscript = false
        )

        assertTrue(prompt.contains("你们聊什么呢"))
        assertFalse(prompt.contains("完整历史不应重复注入"))
        assertFalse(prompt.contains("BEGIN_AUTHORITATIVE_GROUP_TRANSCRIPT"))
    }
}
