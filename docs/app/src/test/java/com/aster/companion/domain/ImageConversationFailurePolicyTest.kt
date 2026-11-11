package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImageConversationFailurePolicyTest {
    @Test
    fun `visual request failure remains a natural companion reply`() {
        val message = ImageConversationFailurePolicy.reply()

        assertEquals("我这次没看清这张图，你晚点再发我一次好不好？", message)
        assertFalse(message.contains("HTTP", ignoreCase = true))
        assertFalse(message.contains("Socket", ignoreCase = true))
    }

    @Test
    fun `missing vision model explains the actionable setup step`() {
        val message = ImageConversationFailurePolicy.reply(
            IllegalStateException("Image understanding model is not configured")
        )

        assertEquals("我现在还没有接上视觉模型，暂时看不到这张图。到设置 > 模型 > 视觉模型配置后再发给我吧。", message)
    }
}
