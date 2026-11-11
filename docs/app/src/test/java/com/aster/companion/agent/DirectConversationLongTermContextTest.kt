package com.aster.companion.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class DirectConversationLongTermContextTest {
    @Test
    fun `keeps memories and worlds source-labelled for direct chat`() {
        val context = DirectConversationLongTermContext.render(
            savedMemories = listOf("User likes strawberry cake"),
            semanticMemories = listOf("Diary: user had an exam last week"),
            privateWorld = listOf("Lan is writing a song"),
            sharedWorld = listOf("They agreed to visit a bookstore")
        )

        assertTrue(context.any { it.startsWith("Confirmed relationship memory:") })
        assertTrue(context.any { it.startsWith("Long-term diary or memory:") })
        assertTrue(context.any { it.startsWith("Private character continuity, not user reality:") })
        assertTrue(context.any { it.startsWith("Explicitly shared relationship continuity:") })
    }
}
