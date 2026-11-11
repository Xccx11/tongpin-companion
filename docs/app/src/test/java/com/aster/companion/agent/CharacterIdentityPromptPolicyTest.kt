package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外貌锚点只负责「她长什么样」：穿什么、做什么、在哪由她自己那一刻决定。
 * 否则每张图都会被外貌里写死的旗袍/袜子/表情/光线钉成同一个样子。
 */
class CharacterIdentityPromptPolicyTest {

    private val appearance =
        "masterpiece, best quality, 2.5D cel shading, 1girl, solo, mature east asian woman, " +
            "long narrow fox eyes, light gray irises, thin eyeliner, pale fair skin, large breasts, " +
            "silver-white high ponytail, silver tassel hair crown, black and gold halter cheongsam, " +
            "golden pattern, black sheer pantyhose, aloof cold expression, crossed legs, warm indoor lighting"

    @Test
    fun `只保留长相`() {
        val identity = CharacterIdentityPromptPolicy.identityOnly(appearance)
        // 长相与画风留下
        assertTrue(identity.contains("1girl"))
        assertTrue(identity.contains("silver-white high ponytail"))
        assertTrue(identity.contains("light gray irises"))
        assertTrue(identity.contains("pale fair skin"))
        assertTrue("身材属于长相，不能剔", identity.contains("large breasts"))
        assertTrue("画风要留（造型一致性靠它）", identity.contains("2.5D cel shading"))
        // 当刻内容剔掉
        assertFalse("服装要剔", identity.contains("cheongsam"))
        assertFalse(identity.contains("pantyhose"))
        assertFalse("姿势要剔", identity.contains("crossed legs"))
        assertFalse("表情要剔", identity.contains("aloof cold expression"))
        assertFalse("光线要剔", identity.contains("indoor lighting"))
    }

    @Test
    fun `剔掉过东西时给出提示，没剔则不给`() {
        assertNotNull(CharacterIdentityPromptPolicy.identityNote(appearance))
        assertNull(
            CharacterIdentityPromptPolicy.identityNote(
                "1girl, solo, long black hair, blue eyes, pale skin, slim body"
            )
        )
    }

    @Test
    fun `全部都是当刻内容时退回原文，避免锚点被清空`() {
        val onlyOutfit = "black cheongsam, black pantyhose"
        assertEquals(onlyOutfit, CharacterIdentityPromptPolicy.identityOnly(onlyOutfit))
    }

    @Test
    fun `中文外貌同样能剔当刻内容`() {
        val chinese = "二十岁女性，银色长直发，浅灰色眼睛，白皮肤，黑色旗袍，黑色丝袜，坐在窗边，室内暖光"
        val identity = CharacterIdentityPromptPolicy.identityOnly(chinese)
        assertTrue(identity.contains("银色长直发"))
        assertFalse(identity.contains("旗袍"))
        assertFalse(identity.contains("丝袜"))
        assertFalse(identity.contains("坐在窗边"))
    }

    /**
     * 用户把外貌写成**一整段话**（句子之间是句号，不是逗号）时，整段长相都必须留住。
     * 实测：这一段曾因为 `bra` 子串命中 "bratty" 被整段删掉，连句里的脸型/下颌线/下巴一起丢，
     * 出图就再也没有脸型描写了。
     */
    private val proseAppearance =
        "A photorealistic portrait of an 18-year-old bratty East Asian college student with a mesugaki vibe. " +
            "She has a slender oval face with a sharp V-shaped jawline and a delicate pointed chin. " +
            "Her big round eyes have dark brown irises with a cunning, teasing gleam, framed by long natural " +
            "eyelashes and smirking pink lips. Her skin is flawless, smooth, translucent, and fair."

    @Test
    fun `整句外貌不会被当刻词误删`() {
        val identity = CharacterIdentityPromptPolicy.identityOnly(proseAppearance)
        assertTrue("脸型必须留下", identity.contains("oval face"))
        assertTrue("下颌线必须留下", identity.contains("jawline"))
        assertTrue("下巴必须留下", identity.contains("pointed chin"))
        assertTrue("年龄与人设必须留下", identity.contains("bratty"))
        assertTrue("瞳色必须留下", identity.contains("dark brown irises"))
    }

    @Test
    fun `当刻词按整词匹配，不吃掉更长的词`() {
        assertFalse("bra 不该命中 bratty", CharacterIdentityPromptPolicy.isTransient("bratty"))
        assertFalse("hat 不该命中 that", CharacterIdentityPromptPolicy.isTransient("showing that"))
        assertTrue(CharacterIdentityPromptPolicy.isTransient("black sheer pantyhose"))
        assertTrue(CharacterIdentityPromptPolicy.isTransient("坐在窗边"))
    }

    private fun assertEquals(expected: String, actual: String) = org.junit.Assert.assertEquals(expected, actual)
}
