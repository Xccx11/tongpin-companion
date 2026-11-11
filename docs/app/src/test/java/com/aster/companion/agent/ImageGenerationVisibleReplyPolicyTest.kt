package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGenerationVisibleReplyPolicyTest {
    @Test
    fun `keeps the character's natural reply before the private image command`() {
        val reply = ImageGenerationVisibleReplyPolicy.fromModel(
            rawCandidate = "好吧，给你挑一张刚拍的，别催我。\n我是林见夏，我想生成一张坐在书桌前的照片。",
            characterName = "林见夏"
        )

        assertEquals("好吧，给你挑一张刚拍的，别催我。", reply)
    }

    @Test
    fun `uses the natural message in a tool decision`() {
        val reply = ImageGenerationVisibleReplyPolicy.fromModel(
            rawCandidate = """{"decision":"tool_calls","message":"行，等我一下，我去给你拍。","tool_calls":[{"capability_id":"companion.generate_image","transport":"LOCAL","confirmation":"NONE","arguments":{"scene":"我是林见夏，我想拍一张照片"}}]}""",
            characterName = "林见夏"
        )

        assertEquals("行，等我一下，我去给你拍。", reply)
    }

    @Test
    fun `falls back only when the model supplied no visible reply`() {
        val reply = ImageGenerationVisibleReplyPolicy.fromModel(
            rawCandidate = "我是林见夏，我想拍一张照片。",
            characterName = "林见夏"
        )

        assertEquals("好呀，我去准备一张给你。你点一下确认，我就开始生成。", reply)
    }
}
