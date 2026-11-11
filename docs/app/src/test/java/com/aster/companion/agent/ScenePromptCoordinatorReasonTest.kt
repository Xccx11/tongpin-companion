package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePromptCoordinatorReasonTest {

    @Test
    fun `拆出 WHY 行并且不进 tag`() {
        val raw = "1girl, sitting on sofa, wet hair, gray t-shirt, warm lamp light\nWHY: 刚洗完澡想让你看看我现在"
        val (tags, reason) = PhotoReplyLines.split(raw).let { it.tags to it.why }
        assertEquals("1girl, sitting on sofa, wet hair, gray t-shirt, warm lamp light", tags)
        assertEquals("刚洗完澡想让你看看我现在", reason)
        assertTrue("WHY 行不能混进渲染 tag", !tags.contains("WHY") && !tags.contains("刚洗完澡"))
    }

    @Test
    fun `中文冒号和「为什么」也认`() {
        val raw = "1girl, holding phone, rain on window\n为什么：外面下雨了想拍给你看"
        val (tags, reason) = PhotoReplyLines.split(raw).let { it.tags to it.why }
        assertEquals("1girl, holding phone, rain on window", tags)
        assertEquals("外面下雨了想拍给你看", reason)
    }

    @Test
    fun `WHY 写在 tag 前面也能拆`() {
        val raw = "WHY: 想让你看我的新裙子\n1girl, standing in front of mirror, new blue dress"
        val (tags, reason) = PhotoReplyLines.split(raw).let { it.tags to it.why }
        assertEquals("1girl, standing in front of mirror, new blue dress", tags)
        assertEquals("想让你看我的新裙子", reason)
    }

    @Test
    fun `没有 WHY 行时原样返回`() {
        val raw = "1girl, sitting by the window, warm afternoon light"
        val (tags, reason) = PhotoReplyLines.split(raw).let { it.tags to it.why }
        assertEquals(raw, tags)
        assertNull(reason)
    }

    @Test
    fun `WHY 行不是中文时当作模型写错，整段保留为 tag`() {
        val raw = "1girl, standing in the rain\nWHY: standing in the rain, neon lights"
        val (tags, reason) = PhotoReplyLines.split(raw).let { it.tags to it.why }
        assertNull(reason)
        assertTrue(tags.contains("WHY: standing in the rain, neon lights"))
    }

}
