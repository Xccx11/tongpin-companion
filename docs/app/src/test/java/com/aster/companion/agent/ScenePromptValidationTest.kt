package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 出图前的**最后一道闸**（A4）：用户改过的 tag 也必须过同一套校验。
 *
 * 重点守两件事：
 * 1. 正常画面描述**通过**（不能把该出的图拦掉）；
 * 2. **外貌锚点被剥离**：用户亲手写的外貌里出现 "the user" 这类词，
 *    不应该因为命中启发式规则就整张图出不来（见 ScenePromptValidation 的 KDoc）。
 */
class ScenePromptValidationTest {

    private fun validate(prompt: String, appearance: String = "") =
        ScenePromptValidation.validate(prompt, appearance)

    @Test
    fun `plain visual tags are accepted`() {
        val result = validate("masterpiece, 1girl, standing by the window, warm afternoon light")
        assertTrue("正常画面描述应通过，reasons=${result.reasons}", result.accepted)
        assertEquals(emptyList<String>(), result.reasons)
    }

    @Test
    fun `appearance anchor is stripped before validation`() {
        // 模型/用户写的外貌里带 "the user's girlfriend" —— 规则不该因此判不合格
        val result = validate(
            prompt = "1girl, smiling, cafe",
            appearance = "the user's girlfriend, long black hair, school uniform",
        )
        assertTrue("外貌锚点应被剥离，reasons=${result.reasons}", result.accepted)
    }

    @Test
    fun `self-talk style prompt is rejected`() {
        val result = validate("I am 宫雨琴. I want to send you a photo.")
        assertFalse("自言自语不是画面描述，应被拦下", result.accepted)
        assertTrue("应给出原因", result.reasons.isNotEmpty())
    }

    @Test
    fun `empty prompt is rejected`() {
        val result = validate("")
        assertFalse(result.accepted)
    }
}