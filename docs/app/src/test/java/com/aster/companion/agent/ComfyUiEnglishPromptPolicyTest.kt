package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyUiEnglishPromptPolicyTest {
    @Test
    fun `hard rule makes the character own a contextual English ComfyUI prompt`() {
        val rule = ComfyUiEnglishPromptPolicy.hardRule("夜鸦")

        assertTrue(rule.contains("HARD RULE"))
        assertTrue(rule.contains("English"))
        assertTrue(rule.contains("complete current context"))
        assertTrue(rule.contains("scene"))
        assertTrue(rule.contains("夜鸦"))
        assertFalse(rule.contains("gateway.*rewrite"))
        // 语言由**后面的场景规则**决定：对 krea2 那套是英文、对 z-image 那套是中文。
        // 以前这里硬写 "in English"，跟 z-image 规则书末尾「不要英文」直接打架。
        assertFalse("不能再把英文写死", rule.contains("prompt in English"))
        assertTrue("要说明中文形态也合法", rule.contains("Chinese"))
    }

    @Test
    fun `accepts any non empty prompt and only rejects json or fence wrappers`() {
        assertTrue(
            ComfyUiEnglishPromptPolicy.isAcceptable(
                "one young woman, long black hair, reading beside a warm window, soft cinematic light"
            )
        )
        // Lite 解除了中/英/tag 形态限制：中文草稿也要能入队（最终英文由 Scene 规则保证）。
        assertTrue(ComfyUiEnglishPromptPolicy.isAcceptable("黑色长发的女孩坐在窗边看书"))
        assertTrue(ComfyUiEnglishPromptPolicy.isAcceptable("# portrait\nblack hair"))
        assertTrue(ComfyUiEnglishPromptPolicy.isAcceptable("**portrait** black hair"))
        assertFalse(ComfyUiEnglishPromptPolicy.isAcceptable("```json\n{\"scene\":\"portrait\"}\n```"))
        assertFalse(ComfyUiEnglishPromptPolicy.isAcceptable("[\"portrait\", \"window light\"]"))
        assertFalse(ComfyUiEnglishPromptPolicy.isAcceptable("   "))
    }

    @Test
    fun `keeps the length bound while allowing chinese drafts`() {
        assertEquals(
            "one woman, warm window light",
            ComfyUiEnglishPromptPolicy.finalPromptOrNull(" one woman, warm window light ", 100)
        )
        // 中文草稿不再被判不合格——它会在细化轮被翻成英文。
        assertEquals("女孩坐在窗边", ComfyUiEnglishPromptPolicy.finalPromptOrNull("女孩坐在窗边", 100))
        assertNull(ComfyUiEnglishPromptPolicy.finalPromptOrNull("a".repeat(101), 100))
    }
}
