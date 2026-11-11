package com.aster.ui

import com.aster.companion.data.PendingActionEntity
import com.aster.companion.data.MomentCommentEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGenerationChatPresentationTest {
    @Test
    fun `keeps an approved photo request visible while it is generating`() {
        assertTrue(PhotoGenerationChatPresentation.shouldShowInConversation(imageAction("RUNNING")))
    }

    @Test
    fun `removes a completed photo request after its image message takes over`() {
        assertFalse(PhotoGenerationChatPresentation.shouldShowInConversation(imageAction("APPROVED")))
    }

    @Test
    fun `renders a character reply as a reply to the user's comment`() {
        val root = MomentCommentEntity("user-1", "post-1", "USER", null, "你在干啥", null, 1L)
        val reply = MomentCommentEntity("role-1", "post-1", "CHARACTER", "summer", "在给你回消息", "user-1", 2L)

        assertEquals("雨宫琴 回复 我", MomentCommentPresentation.authorLine(reply, listOf(root, reply)) { "雨宫琴" })
    }

    private fun imageAction(state: String) = PendingActionEntity(
        id = "image-1",
        eventId = null,
        threadId = "thread-1",
        capabilityId = "companion.propose_image",
        transport = "LOCAL",
        argumentsJson = "{}",
        state = state,
        createdAt = 1L,
        resolvedAt = null,
        resultSummary = null
    )
}
