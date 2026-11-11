package com.aster.companion.agent

import com.aster.companion.data.MomentCommentEntity
import com.aster.companion.data.MomentPostEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentConversationContextTest {
    @Test
    fun `keeps a character's own post and its user comment in that private chat context`() {
        val post = MomentPostEntity("p1", "CHARACTER", "summer", "I made cookies", "ALL", "PROACTIVE", "PUBLISHED", 1, 1)
        val comment = MomentCommentEntity("c1", "p1", "USER", null, "Save one for me", null, 2)

        val context = MomentConversationContext.forCharacter("summer", listOf(post), listOf(comment))

        assertTrue(context.contains("I made cookies"))
        assertTrue(context.contains("Save one for me"))
    }

    @Test
    fun `does not leak another character's dynamic into the chat`() {
        val post = MomentPostEntity("p2", "CHARACTER", "other", "My private post", "ALL", "PROACTIVE", "PUBLISHED", 1, 1)

        val context = MomentConversationContext.forCharacter("summer", listOf(post), emptyList())

        assertFalse(context.contains("My private post"))
    }

    @Test
    fun `group context includes public posts from its members but excludes unrelated roles`() {
        val userPost = MomentPostEntity("u1", "USER", null, "I made dinner", "ALL", "USER", "PUBLISHED", 1, 1)
        val memberPost = MomentPostEntity("m1", "CHARACTER", "summer", "I am still thinking about dinner", "ALL", "PROACTIVE", "PUBLISHED", 2, 2)
        val outsiderPost = MomentPostEntity("o1", "CHARACTER", "other", "My unrelated post", "ALL", "PROACTIVE", "PUBLISHED", 3, 3)
        val memberComment = MomentCommentEntity("c1", "m1", "CHARACTER", "winter", "That sounds nice", null, 4)

        val context = MomentConversationContext.forGroup(
            memberIds = setOf("summer", "winter"),
            posts = listOf(userPost, memberPost, outsiderPost),
            comments = listOf(memberComment)
        )

        assertTrue(context.contains("I made dinner"))
        assertTrue(context.contains("I am still thinking about dinner"))
        assertTrue(context.contains("That sounds nice"))
        assertFalse(context.contains("My unrelated post"))
    }
}
