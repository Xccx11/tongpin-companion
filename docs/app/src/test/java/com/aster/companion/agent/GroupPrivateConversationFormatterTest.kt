package com.aster.companion.agent

import com.aster.companion.data.MessageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPrivateConversationFormatterTest {
    @Test
    fun `private continuity keeps the user and current character but filters another character`() {
        val context = GroupPrivateConversationFormatter.render(
            characterId = "lan",
            characterName = "Lan",
            messages = listOf(
                MessageEntity("u1", "private", null, "I will be home late", null, true, 1L),
                MessageEntity("l1", "private", "lan", "Okay, I will wait", null, false, 2L),
                MessageEntity("y1", "private", "yuan", "This belongs to another private chat", null, false, 3L)
            )
        )

        assertTrue(context.any { it.contains("speaker=user") && it.contains("I will be home late") })
        assertTrue(context.any { it.contains("speaker=Lan") && it.contains("Okay, I will wait") })
        assertFalse(context.any { it.contains("This belongs to another private chat") })
    }
}
