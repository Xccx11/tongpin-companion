package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStateFormatTest {

    private val good = """
        人物：夜鸦=用户的室友
        称谓：夜鸦→用户=哥
        地点时间：家里，晚上十点
        已完成：一起吃了面；她换了睡衣
        未完成：周末一起看电影
        关键事实：她怕打雷
    """.trimIndent()

    @Test
    fun `accepts a well formed state and keeps every field`() {
        val result = ConversationStateFormat.validate(good)
        assertTrue(result.reason, result.ok)
        ConversationStateFormat.FIELDS.forEach { field ->
            assertTrue("缺字段 $field", result.text.contains("$field："))
        }
        assertEquals(6, result.text.lines().size)
    }

    @Test
    fun `fills empty fields with the empty mark`() {
        val result = ConversationStateFormat.validate(
            """
            人物：夜鸦
            称谓：无
            地点时间：无
            已完成：无
            未完成：无
            关键事实：无
            """.trimIndent()
        )
        assertTrue(result.ok)
        // 人物有内容，其余 5 个字段为空 → 补成「无」。
        assertEquals(5, result.text.lines().count { it.endsWith("：无") })
        assertTrue(result.text.contains("人物：夜鸦"))
    }

    @Test
    fun `rejects prose summaries from the old schema`() {
        val legacy = "他们聊了周末的电影，她答应一起去看。氛围很轻松，她还提到怕打雷。"
        val result = ConversationStateFormat.validate(legacy)
        assertFalse(result.ok)
        assertFalse(ConversationStateFormat.isUsable(legacy))
        assertNull(ConversationStateFormat.validate(good).reason)
    }

    @Test
    fun `rejects dialogue asterisk actions and out of order fields`() {
        assertFalse(ConversationStateFormat.validate(good.replace("夜鸦=用户的室友", "*她笑了笑*")).ok)
        assertFalse(ConversationStateFormat.validate(good.replace("家里，晚上十点", "“我在家”")).ok)
        val reordered = ConversationStateFormat.FIELDS.reversed().joinToString("\n") { "$it：无" }
        assertFalse(ConversationStateFormat.validate(reordered).ok)
    }

    @Test
    fun `rejects output that is missing a field`() {
        val missing = good.lines().dropLast(1).joinToString("\n")
        val result = ConversationStateFormat.validate(missing)
        assertFalse(result.ok)
        assertNotNull(result.reason)
    }

    @Test
    fun `trims long items and caps how many per field`() {
        val longItem = "很长的一句话".repeat(20)
        val manyItems = (1..12).joinToString("；") { "第${it}件事" }
        val result = ConversationStateFormat.validate(
            """
            人物：$manyItems
            称谓：无
            地点时间：$longItem
            已完成：无
            未完成：无
            关键事实：无
            """.trimIndent()
        )
        assertTrue(result.reason, result.ok)
        val people = result.text.lineSequence().first { it.startsWith("人物：") }
        assertEquals(ConversationStateFormat.MAX_PINNED_ITEMS, people.removePrefix("人物：").split("；").size)
        val location = result.text.lineSequence().first { it.startsWith("地点时间：") }
        assertEquals(ConversationStateFormat.MAX_ITEM_CHARS, location.removePrefix("地点时间：").length)
    }

    @Test
    fun `pinned fields keep more items than the rolling ones`() {
        val items = (1..7).joinToString("；") { "事${it}" }
        val result = ConversationStateFormat.validate(
            """
            人物：$items
            称谓：无
            地点时间：无
            已完成：$items
            未完成：无
            关键事实：$items
            """.trimIndent()
        )
        assertTrue(result.reason, result.ok)
        assertEquals(7, result.text.lineSequence().first { it.startsWith("人物：") }.removePrefix("人物：").split("；").size)
        assertEquals(
            ConversationStateFormat.MAX_ITEMS_PER_FIELD,
            result.text.lineSequence().first { it.startsWith("已完成：") }.removePrefix("已完成：").split("；").size
        )
    }

    @Test
    fun `render produces the block that goes into the chat system`() {
        val rendered = ConversationStateFormat.render(good)
        assertTrue(rendered.startsWith("当前状态"))
        assertTrue(rendered.contains("称谓：夜鸦→用户=哥"))
    }

    /** prompt 里必须点明"你是记录器不是角色"，否则 RP 模型会把状态写成小说。 */
    @Test
    fun `updater prompt forbids roleplay and pins the field order`() {
        val prompt = ConversationStateFormat.SYSTEM_PROMPT
        assertTrue(prompt.contains("状态记录器"))
        assertTrue(prompt.contains("不要扮演任何角色"))
        ConversationStateFormat.FIELDS.forEach { assertTrue("缺字段说明 $it", prompt.contains("$it：")) }
    }

    // ---- 可选的第 7 行：情绪位移（氛围惯性）。它**不进**落库文本，只作为信号被摘出来。----

    private fun withShift(line: String) = good + "\n" + line

    @Test
    fun `accepts the optional shift line without letting it into the stored state`() {
        val result = ConversationStateFormat.validate(withShift("${ConversationStateFormat.SHIFT_FIELD}：-2"))
        assertTrue(result.reason, result.ok)
        // 摘出来了，而且落库文本仍然只有 6 个字段（旧数据/注入逻辑都不受影响）。
        assertEquals(-2, result.shift)
        assertEquals(6, result.text.lines().size)
        assertFalse(result.text.contains(ConversationStateFormat.SHIFT_FIELD))
    }

    @Test
    fun `no shift line means no movement`() {
        val result = ConversationStateFormat.validate(good)
        assertTrue(result.ok)
        assertNull(result.shift)
    }

    @Test
    fun `an unreadable shift line does not fail the whole update`() {
        val result = ConversationStateFormat.validate(withShift("${ConversationStateFormat.SHIFT_FIELD}：没变"))
        assertTrue(result.reason, result.ok)
        assertNull(result.shift)
        assertEquals(6, result.text.lines().size)
    }

    @Test
    fun `an out of range shift is clamped instead of rejected`() {
        assertEquals(5, ConversationStateFormat.validate(withShift("${ConversationStateFormat.SHIFT_FIELD}：9")).shift)
        assertEquals(-5, ConversationStateFormat.validate(withShift("${ConversationStateFormat.SHIFT_FIELD}：-42")).shift)
        assertEquals(3, ConversationStateFormat.validate(withShift("${ConversationStateFormat.SHIFT_FIELD}: +3")).shift)
    }

    @Test
    fun `a seventh line that is not the shift field is still rejected`() {
        assertFalse(ConversationStateFormat.validate(withShift("心情：还行")).ok)
    }

    /** prompt 必须把这一行说清楚，否则模型会把它混进某个字段里、或者干脆不输出。 */
    @Test
    fun `the prompt asks for the shift line and explains both directions`() {
        val prompt = ConversationStateFormat.SYSTEM_PROMPT
        assertTrue(prompt.contains("${ConversationStateFormat.SHIFT_FIELD}："))
        // 方向语义必须与 EmotionalOffsetPolicy 一致：正=更严肃/更收敛，负=更轻松/更活跃。
        assertTrue(prompt.contains("更严肃"))
        assertTrue(prompt.contains("更轻松"))
    }
}
