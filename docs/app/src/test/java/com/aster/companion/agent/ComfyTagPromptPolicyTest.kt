package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyTagPromptPolicyTest {

    private val tagPrompt =
        "1girl, solo, long silver hair, golden tiara, kitchen, cooking, smile, looking at viewer, warm lighting, anime style, masterpiece, best quality"

    @Test
    fun `accepts a comma separated tag list`() {
        val validation = ComfyTagPromptPolicy.validate(tagPrompt)
        assertTrue(validation.accepted)
        assertEquals(tagPrompt, ComfyTagPromptPolicy.finalPromptOrNull(tagPrompt))
    }

    @Test
    fun `accepts narrative sentences and chinese after relaxing limits`() {
        assertTrue(
            ComfyTagPromptPolicy
                .validate("A beautiful girl is standing in the kitchen and smiling.")
                .accepted
        )
        assertTrue(ComfyTagPromptPolicy.validate("1girl, 长发, 微笑").accepted)
        assertNotNull(ComfyTagPromptPolicy.finalPromptOrNull("solo"))
    }

    @Test
    fun `rejects json markdown fences and empty`() {
        assertFalse(ComfyTagPromptPolicy.validate("{\"scene\": \"1girl, solo\"}").accepted)
        assertFalse(ComfyTagPromptPolicy.validate("```\n1girl, solo\n```").accepted)
        assertFalse(ComfyTagPromptPolicy.validate("   ").accepted)
    }

    @Test
    fun `rejects an overlong prompt`() {
        // 上限抬到 6_000（给「极度详细的标签串」留空间），超限样本同步抬到它之上。
        val long = "x".repeat(6_500)
        val validation = ComfyTagPromptPolicy.validate(long)
        assertFalse(validation.accepted)
        assertTrue(validation.reasons.contains("too_long"))
    }

    @Test
    fun `去掉模型照抄的占位符`() {
        // 真实数据里出现过：[outfit from previous context] 被原样写进 tag，渲染器读不了。
        val cleaned = ComfyTagPromptPolicy.sanitizeScene(
            "1girl, solo, wearing [outfit from previous context], sitting on a chair"
        )
        assertEquals("1girl, solo, sitting on a chair", cleaned)
    }

    @Test
    fun `去掉场景里的中文但保留权重括号`() {
        val cleaned = ComfyTagPromptPolicy.sanitizeScene(
            "1girl, 宫雨琴, holding phone, (red winter coat:1.2), 卧室"
        )
        assertEquals("1girl, holding phone, (red winter coat:1.2)", cleaned)
    }

    @Test
    fun `删掉脸红类分句但不动器官描写`() {
        val cleaned = ComfyTagPromptPolicy.finalPolish(
            "Her face shows extreme pleasure, with flushed cheeks, and her eyes are wet."
        )
        assertFalse("脸红类词必须被删掉", cleaned.lowercase().contains("flush"))
        assertTrue("其余内容要留下", cleaned.contains("Her face shows extreme pleasure"))
        assertTrue("其余内容要留下", cleaned.contains("her eyes are wet"))
        // 器官描写里的 flushed 不是脸红，不能误删。
        assertTrue(
            ComfyTagPromptPolicy.finalPolish("the glans flushed dark").contains("flushed")
        )
    }

    @Test
    fun `男性称谓一律写成 an Asian young man 与 an Asian man's`() {
        val cleaned = ComfyTagPromptPolicy.finalPolish(
            "The man's thick thighs are on either side of her, his hands hold her waist, he leans over her."
        )
        // 所有格：his / the man's -> an Asian man's
        assertEquals(2, Regex("an Asian man's", RegexOption.IGNORE_CASE).findAll(cleaned).count())
        // 主格：he -> an Asian young man
        assertEquals(1, Regex("an Asian young man", RegexOption.IGNORE_CASE).findAll(cleaned).count())
        assertFalse(
            "不许留下没有身份的统称",
            Regex("\\b(?:his|he|him)\\b", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
        )
        // 她本人的 she/her 不受影响；只有她一个人的画面不许凭空多出男性。
        assertEquals(
            "She kneels on the floor, her hands on her thighs.",
            ComfyTagPromptPolicy.finalPolish("She kneels on the floor, her hands on her thighs.")
        )
    }

    @Test
    fun `清理后为空的场景视为不合格`() {
        val cleaned = ComfyTagPromptPolicy.sanitizeScene("[outfit from previous context]")
        assertTrue(cleaned.isEmpty())
        assertFalse(ComfyTagPromptPolicy.validate(cleaned).accepted)
    }
}
