package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageReplySanitizerTest {
    @Test
    fun `removes a leaked image command from the visible character reply`() {
        val raw = """
            算了，最后一张！看完必须去睡！

            我是林见夏，我想拍一张自己坐在书桌前的照片，有点无奈地看着镜头。
        """.trimIndent()

        assertEquals("算了，最后一张！看完必须去睡！", ImageReplySanitizer.visibleReply(raw, "林见夏"))
    }

    @Test
    fun `keeps ordinary first person conversation intact`() {
        assertEquals("我是林见夏，当然记得你。", ImageReplySanitizer.visibleReply("我是林见夏，当然记得你。", "林见夏"))
    }
}
