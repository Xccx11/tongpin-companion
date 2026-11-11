package com.aster.companion.ui

import com.aster.companion.data.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagePresentationTest {
    @Test
    fun `stage direction dimming is enabled by default`() {
        assertTrue(ChatDisplayPreferences().dimStageDirections)
    }

    @Test
    fun `splits only character multiline text and keeps original message intact`() {
        val message = MessageEntity("m1", "t1", "c1", "第一句\n\n第二句", null, false, 1L)

        val displayed = ChatMessagePresentation.displayMessages(listOf(message), true)

        assertEquals(listOf("第一句", "第二句"), displayed.map { it.message.body })
        assertEquals("第一句\n\n第二句", message.body)
        assertEquals(listOf("m1:0", "m1:1"), displayed.map { it.id })
    }

    @Test
    fun `splits literal newline escapes returned by a conversation provider`() {
        val message = MessageEntity("m1", "t1", "c1", "第一句\\n\\n第二句", null, false, 1L)

        val displayed = ChatMessagePresentation.displayMessages(listOf(message), true)

        assertEquals(listOf("第一句", "第二句"), displayed.map { it.message.body })
    }

    @Test
    fun `splits Unicode paragraph separators returned by a conversation provider`() {
        val message = MessageEntity("m1", "t1", "c1", "第一句\u2028第二句\u2029第三句", null, false, 1L)

        val displayed = ChatMessagePresentation.displayMessages(listOf(message), true)

        assertEquals(listOf("第一句", "第二句", "第三句"), displayed.map { it.message.body })
    }

    @Test
    fun `keeps user messages as one message`() {
        val message = MessageEntity("m1", "t1", null, "第一句\n第二句", null, true, 1L)

        assertEquals(1, ChatMessagePresentation.displayMessages(listOf(message), true).size)
    }

    @Test
    fun `splits a streaming character reply before it is finalized`() {
        val message = MessageEntity("stream:reply", "t1", "c1", "第一句\n\n第二句", null, false, 1L)

        val displayed = ChatMessagePresentation.displayMessages(listOf(message), true)

        assertEquals(listOf("第一句", "第二句"), displayed.map { it.message.body })
        assertEquals(listOf("stream:reply:0", "stream:reply:1"), displayed.map { it.id })
    }

    @Test
    fun `deleting one displayed segment keeps the rest of the reply`() {
        val body = "第一句\n\n第二句\n\n第三句"

        assertEquals("第一句\n第三句", ChatMessagePresentation.removeSegment(body, 1))
        assertEquals("第二句\n第三句", ChatMessagePresentation.removeSegment(body, 0))
    }

    @Test
    fun `deleting the last remaining segment asks for the whole message to go`() {
        assertNull(ChatMessagePresentation.removeSegment("只有一段", 0))
        assertNull(ChatMessagePresentation.removeSegment("第一句\n\n第二句", 5))
    }

    @Test
    fun `resolves a split bubble back to its message and segment index`() {
        val message = MessageEntity("m1", "t1", "c1", "第一句\n\n第二句", null, false, 1L)

        val ref = ChatMessagePresentation.refOf(ChatMessagePresentation.displayMessages(listOf(message), true), "m1:1")

        assertEquals("m1", ref?.messageId)
        assertEquals(1, ref?.segmentIndex ?: -1)
        assertEquals(2, ref?.segmentCount ?: -1)
    }

    @Test
    fun `an unsplit bubble has no segment index so deleting removes the message`() {
        val message = MessageEntity("m1", "t1", "c1", "只有一段", null, false, 1L)

        val ref = ChatMessagePresentation.refOf(ChatMessagePresentation.displayMessages(listOf(message), true), "m1")

        assertEquals("m1", ref?.messageId)
        assertNull(ref?.segmentIndex)
        assertEquals(1, ref?.segmentCount ?: -1)
    }

    @Test
    fun `every displayed bubble resolves back to the message it came from`() {
        // UI 契约：列表/长按菜单传的是**显示 id**（拆段时是 `<消息id>:<段号>`），
        // refOf 必须能把它解回原始消息 —— 解不回来就会表现成"消息看着像图片消息、删也删不掉"。
        val split = MessageEntity("m1", "t1", "c1", "第一句\n\n第二句", null, false, 1L)
        val single = MessageEntity("m2", "t1", "c1", "只有一段", null, false, 1L)

        val displayed = ChatMessagePresentation.displayMessages(listOf(split, single), true)

        assertEquals(listOf("m1:0", "m1:1", "m2"), displayed.map { it.id })
        displayed.forEach { bubble ->
            assertEquals(bubble.message.id, ChatMessagePresentation.refOf(displayed, bubble.id)?.messageId)
        }
    }

    @Test
    fun `recognizes the three supported stage direction wrappers`() {
        val parts = ChatMessagePresentation.parts("好吧（别开视线）<轻轻叹气>(抿嘴)")

        assertEquals(3, parts.count { it.isStageDirection })
        assertTrue(parts.any { it.value == "（别开视线）" && it.isStageDirection })
        assertFalse(parts.first().isStageDirection)
    }
}
