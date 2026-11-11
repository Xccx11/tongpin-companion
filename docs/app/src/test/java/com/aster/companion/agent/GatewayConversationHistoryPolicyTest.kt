package com.aster.companion.agent

import com.aster.companion.data.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConversationHistoryPolicyTest {
    @Test
    fun `sends every visible message so the gateway owns long history compression`() {
        val messages = (1L..80L).map { index ->
            MessageEntity(
                id = "message-$index",
                threadId = "thread",
                characterId = if (index % 2L == 0L) "lan" else null,
                body = "message $index",
                imageUri = null,
                fromUser = index % 2L == 1L,
                createdAt = index
            )
        }

        val selected = GatewayConversationHistoryPolicy.visibleMessages(messages, memorySince = 1L)

        assertEquals(80, selected.size)
        assertEquals("message-1", selected.first().id)
        assertEquals("message-80", selected.last().id)
    }

    @Test
    fun `fresh window boundary hides old and non conversational rows`() {
        val messages = listOf(
            MessageEntity("old", "thread", null, "old", null, true, 99L),
            MessageEntity("user", "thread", null, "new", null, true, 100L),
            MessageEntity("role", "thread", "lan", "reply", null, false, 101L),
            MessageEntity("system-row", "thread", null, "internal", null, false, 102L)
        )

        val selected = GatewayConversationHistoryPolicy.visibleMessages(messages, memorySince = 100L)

        assertEquals(listOf("user", "role"), selected.map { it.id })
        assertFalse(selected.any { it.id == "old" })
        assertTrue(selected.all { it.fromUser || it.characterId != null })
    }
}
