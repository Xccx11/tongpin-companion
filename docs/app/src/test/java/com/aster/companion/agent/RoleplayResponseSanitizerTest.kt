package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayResponseSanitizerTest {
    @Test
    fun `preserves a leading stage direction for chat presentation`() {
        val raw = "(手机震动，看到是消息后本想秒回，又忍了几秒才拿起手机)……你还知道找我啊。"

        assertEquals(raw, RoleplayResponseSanitizer.clean(raw))
    }

    @Test fun `preserves an inline stage direction and drops blank lines`() {
        // sanitizer 会把连续空行压掉（聊天 UI 自己按行拆气泡），动作神态本身保留。
        assertEquals(
            "你怎么这么执着啊。\n（咬了咬嘴唇，手指在屏幕上悬了半天）\n行吧，就一次。",
            RoleplayResponseSanitizer.clean("你怎么这么执着啊。\n\n（咬了咬嘴唇，手指在屏幕上悬了半天）\n\n行吧，就一次。")
        )
    }
}
