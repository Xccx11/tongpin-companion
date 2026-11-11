package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯逻辑的行为测试（A4）：文本解析 + 时间描述。
 *
 * 这两块没有任何依赖（不读库、不发请求），所以最适合用来把"提示词解析"的语义钉住：
 * 以后改解析逻辑时，这些断言会立刻告诉你"WHY 行被吃掉了"或"分段错了"。
 */
class SceneTextParsingTest {

    // ---------- PhotoReplyLines.split ----------

    @Test
    fun `splits tags and WHY line`() {
        val raw = """
            masterpiece, best quality, 1girl, sunset
            WHY: 因为今天光线很好
        """.trimIndent()

        val lines = PhotoReplyLines.split(raw)

        assertEquals("masterpiece, best quality, 1girl, sunset", lines.tags)
        assertEquals("因为今天光线很好", lines.why)
    }

    @Test
    fun `accepts chinese WHY marker`() {
        val lines = PhotoReplyLines.split("1girl, 窗边\n为什么：想让你看看外面的雨")
        assertEquals("1girl, 窗边", lines.tags)
        assertEquals("想让你看看外面的雨", lines.why)
    }

    @Test
    fun `no WHY line leaves why null`() {
        val lines = PhotoReplyLines.split("1girl, cafe, warm light")
        assertEquals("1girl, cafe, warm light", lines.tags)
        assertNull(lines.why)
    }

    @Test
    fun `blank input yields empty tags`() {
        val lines = PhotoReplyLines.split("   ")
        assertEquals("", lines.tags)
        assertNull(lines.why)
    }

    // ---------- SceneClock.timeOfDay ----------

    @Test
    fun `timeOfDay returns a period plus hh mm`() {
        val text = timeOfDay()
        // 形如 "晚上 21:03"：必须以中文时段开头，并带 HH:MM
        assertTrue("实际输出: $text", text.contains(":"))
        assertTrue(
            "实际输出: $text",
            listOf("清晨", "上午", "中午", "下午", "晚上", "深夜").any { text.startsWith(it) },
        )
        assertTrue("实际输出: $text", Regex("""\d{2}:\d{2}$""").containsMatchIn(text))
    }
}