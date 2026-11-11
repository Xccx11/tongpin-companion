package com.aster.companion.agent

import com.aster.companion.domain.MomentReactionDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MomentsAgentDecisionParserTest {
    @Test
    fun `parses character reaction decisions`() {
        assertEquals(MomentReactionDecision.LIKE, MomentsAgentDecisionParser.parseReaction("{\"decision\":\"like\"}").decision)
        assertEquals(MomentReactionDecision.COMMENT, MomentsAgentDecisionParser.parseReaction("{\"decision\":\"comment\",\"comment\":\"看起来不错\"}").decision)
        assertEquals("看起来不错", MomentsAgentDecisionParser.parseReaction("{\"decision\":\"comment\",\"comment\":\"看起来不错\"}").comment)
        assertEquals(MomentReactionDecision.BOTH, MomentsAgentDecisionParser.parseReaction("{\"decision\":\"both\"}").decision)
        assertEquals(MomentReactionDecision.IGNORE, MomentsAgentDecisionParser.parseReaction("{\"decision\":\"unknown\"}").decision)
    }

    @Test
    fun `parses proactive post with image request`() {
        val plan = MomentsAgentDecisionParser.parsePost(
            """{"decision":"post","body":"今天的晚霞很漂亮","with_image":true,"image_prompt":"warm sunset by the window"}"""
        )
        assertEquals(true, plan.post)
        assertEquals("今天的晚霞很漂亮", plan.body)
        assertEquals(true, plan.withImage)
        assertEquals("warm sunset by the window", plan.imagePrompt)
    }

    @Test
    fun `ignores malformed post response instead of inventing a post`() {
        val result = runCatching { MomentsAgentDecisionParser.parsePost("not json") }
        assertEquals(true, result.isFailure)
    }

    @Test
    fun `blank proactive body is rejected by the coordinator contract`() {
        val plan = MomentsAgentDecisionParser.parsePost("{\"decision\":\"post\",\"body\":\" \"}")
        assertEquals(true, plan.post)
        assertEquals("", plan.body)
        assertNull(plan.imagePrompt)
    }

    @Test
    fun `keeps an image post when the role wrote a chinese image prompt`() {
        val plan = MomentsAgentDecisionParser.parsePost(
            """{"decision":"post","body":"今天的晚霞很漂亮","with_image":true,"image_prompt":"女孩坐在窗边看晚霞"}"""
        )

        // Lite 解除了语言限制：中文 image_prompt 同样有效（Scene 流水线负责把它写成最终提示词）。
        assertEquals(true, plan.withImage)
        assertEquals("女孩坐在窗边看晚霞", plan.imagePrompt)
    }
}
