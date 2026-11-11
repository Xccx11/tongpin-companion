package com.aster.companion.ui

import com.aster.companion.data.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadPreviewBuilderTest {
    @Test
    fun `uses the newest message and counts only unread companion messages`() {
        val previews = ThreadPreviewBuilder.build(
            listOf(
                MessageEntity("one", "thread", null, "我先说", null, true, 10, 10),
                MessageEntity("two", "thread", "lan", "晚安", null, false, 20, null),
                MessageEntity("three", "thread", "lan", "记得吃饭", null, false, 30, null)
            )
        )

        assertEquals("记得吃饭", previews.getValue("thread").lastPreview)
        assertEquals(2, previews.getValue("thread").unreadCount)
        assertEquals(30, previews.getValue("thread").lastMessageAt)
    }
}
