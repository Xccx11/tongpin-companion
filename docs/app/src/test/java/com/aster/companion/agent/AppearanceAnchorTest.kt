package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 外貌锚点的行为测试（A4）。
 *
 * 这块的承诺是「用户写的外貌一字不改地拼在提示词最前面」，而且是**幂等**的
 * （已经拼过就不再拼）—— 这两条一旦破，用户会看到外貌重复或丢失。
 */
class AppearanceAnchorTest {

    @Test
    fun `empty appearance returns scene unchanged`() {
        assertEquals("1girl, cafe", AppearanceAnchor.prepend("1girl, cafe", ""))
        assertEquals("1girl, cafe", AppearanceAnchor.prepend("1girl, cafe", "   "))
    }

    @Test
    fun `appearance is prepended on its own line`() {
        val scene = "1girl, standing by the window"
        val appearance = "宫雨琴, 22岁, 长黑发"
        val result = AppearanceAnchor.prepend(scene, appearance)
        // 外貌在前，且用换行分段（Krea 按模块分段）
        assertEquals(appearance + "\n" + scene, result)
    }

    @Test
    fun `prepend is idempotent`() {
        val scene = "1girl, cafe"
        val appearance = "宫雨琴, 22岁"
        val once = AppearanceAnchor.prepend(scene, appearance)
        val twice = AppearanceAnchor.prepend(once, appearance)
        // 已经拼过就不该再拼一遍（用户会看到外貌重复）
        assertEquals(once, twice)
        assertEquals(1, twice.split(appearance).size - 1)
    }

    @Test
    fun `empty scene returns appearance only`() {
        assertEquals("宫雨琴, 22岁", AppearanceAnchor.prepend("", "宫雨琴, 22岁"))
    }

    @Test
    fun `scene is trimmed before joining`() {
        val result = AppearanceAnchor.prepend("   1girl   ", "外貌")
        assertEquals("外貌\n1girl", result)
    }
}