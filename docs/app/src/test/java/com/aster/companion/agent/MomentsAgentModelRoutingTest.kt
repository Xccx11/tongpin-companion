package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsAgentModelRoutingTest {
    @Test
    fun `moments use conversation model for decisions`() {
        assertEquals("conversation", MomentsAgentCoordinator.MOMENTS_CONVERSATION_MODEL)
        assertEquals("image_understanding", ImageUnderstandingCoordinator.IMAGE_UNDERSTANDING_MODEL)
        assertEquals("image_generation", ImageGenerationCoordinator.IMAGE_GENERATION_MODEL)
    }

    @Test
    fun `reaction prompt includes successful vision descriptions without inventing fallback text`() {
        val prompt = MomentsAgentPromptComposer.reactionPrompt(
            body = "窗边的晚餐",
            imageCount = 2,
            imageDescriptions = listOf("一张餐桌照片，窗外天色已暗")
        )

        assertTrue(prompt.contains("窗边的晚餐"))
        assertTrue(prompt.contains("IMAGE COUNT: 2"))
        assertTrue(prompt.contains("一张餐桌照片，窗外天色已暗"))
        assertFalse(prompt.contains("看起来像是用户正在庆祝生日"))
    }

    @Test
    fun `reaction prompt remains usable when vision model has no result`() {
        val prompt = MomentsAgentPromptComposer.reactionPrompt(
            body = "",
            imageCount = 1,
            imageDescriptions = emptyList()
        )

        assertTrue(prompt.contains("(no text)"))
        assertTrue(prompt.contains("IMAGE COUNT: 1"))
        assertTrue(prompt.contains("No reliable visual description is available."))
    }

    @Test
    fun `proactive post prompt carries the saved appearance into image planning`() {
        val prompt = MomentsAgentPromptComposer.proactivePostPrompt(
            appearance = "black high ponytail, sharp almond eyes",
            groundedFacts = "message:m1 | user: I am studying tonight",
            recentPosts = "(no earlier feed posts)"
        )

        assertTrue(prompt.contains("black high ponytail, sharp almond eyes"))
        assertTrue(prompt.contains("message:m1"))
        assertTrue(prompt.contains("grounding_id"))
    }
}
