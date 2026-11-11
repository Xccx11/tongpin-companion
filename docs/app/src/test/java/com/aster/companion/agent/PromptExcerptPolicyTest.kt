package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词片段的边界安全截断。
 *
 * 回归目标：实测 `LAST_SCENE` 的 `take(600)` 切出过 `... She wear.` 这种半句话
 * （`She wears a cool refined no-makeup makeup look` 被拦腰砍断），残句直接进下一轮请求。
 */
class PromptExcerptPolicyTest {

    /** 线上真实那份提示词的开头（用来复现 `She wear.` 那次截断）。 */
    private val 实测提示词 = "A photorealistic smartphone full-body photo, high-definition realistic skin " +
        "texture, fine pores, natural skin tone transitions, clear and natural. From head to toe: " +
        "A 22-year-old East Asian young woman has long black hair, soft with natural slight waves, " +
        "many fine strands falling naturally, and light airy bangs. She has an oval face, a refined " +
        "sharp chin, and a clear jawline. Her eyes are large, deep dark brown, with slightly upturned " +
        "outer corners and long thick eyelashes. Her nose is small and straight. Her skin is fair, " +
        "translucent, delicate, smooth, and has a healthy radiant glow."

    @Test
    fun `短于上限时原样返回`() {
        assertEquals("short text", PromptExcerptPolicy.excerpt("short text", 100))
    }

    @Test
    fun `上限为零或负数给空串`() {
        assertEquals("", PromptExcerptPolicy.excerpt("some text", 0))
        assertEquals("", PromptExcerptPolicy.excerpt("some text", -5))
    }

    @Test
    fun `优先切在句末`() {
        val out = PromptExcerptPolicy.excerpt(实测提示词, 200)

        assertTrue("必须在句末收尾，实际=「$out」", out.endsWith("."))
        assertTrue(out.startsWith("A photorealistic"))
    }

    /** 这条就是 bug 本身的回归：绝不再切出 `She wear.` 这种半句话。 */
    @Test
    fun `不会再切出半句话 She wear`() {
        val out = PromptExcerptPolicy.excerpt(实测提示词, 600)

        assertFalse(out.contains("She wear"))
        assertFalse(out.endsWith("wear"))
        assertFalse(out.endsWith("wear."))
        assertTrue(out.endsWith("."))
    }

    @Test
    fun `没有句末时切在词末`() {
        val out = PromptExcerptPolicy.excerpt("alpha beta gamma delta epsilon zeta", 20)

        assertEquals("alpha beta gamma", out)
    }

    @Test
    fun `中文长句按句号切`() {
        val out = PromptExcerptPolicy.excerpt("她坐在窗边，天已经黑了。她还没有开灯，屋子里安静得能听见钟摆。", 20)

        assertEquals("她坐在窗边，天已经黑了。", out)
    }

    @Test
    fun `既没有句末也没有空格时硬截，但不留省略号`() {
        val out = PromptExcerptPolicy.excerpt("一二三四五六七八九十" + "甲乙丙丁戊己庚辛壬癸", 12)

        assertEquals(12, out.length)
        assertFalse(out.contains("..."))
        assertFalse(out.contains("…"))
    }

    /** 切点太靠前就宁可不切：截断不该退化成"只剩一个词"。 */
    @Test
    fun `句末离开头太近时不采用该切点`() {
        val out = PromptExcerptPolicy.excerpt("Hi. " + "x".repeat(100), 50)

        assertTrue("不该只剩 'Hi.'", out.length > 24)
    }
}
