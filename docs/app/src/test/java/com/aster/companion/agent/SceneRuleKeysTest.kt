package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 形态 ↔ 远程规则 key / API 池标记的一致性。
 *
 * 回归背景：这两个映射曾经分别写在两个私有函数里，结果 `CHINESE_NATURAL` 取的是 z 那套规则、
 * 池匹配却按 krea2 的名字找 —— "用 Z-Image 规则"却去匹配另一个模型的池条目。
 */
class SceneRuleKeysTest {

    @Test
    fun `Krea2 与 z-image 各用自己那套规则 key`() {
        assertEquals("image.sceneRule.krea2", SceneRuleKeys.sceneRule(ScenePromptFormat.ENGLISH_NATURAL))
        assertEquals("image.sceneRule.z", SceneRuleKeys.sceneRule(ScenePromptFormat.CHINESE_NATURAL))
        assertEquals("image.sceneRule.wai", SceneRuleKeys.sceneRule(ScenePromptFormat.ENGLISH_TAGS))
    }

    @Test
    fun `细化与校验轮的 key 跟着同一形态走`() {
        assertEquals("image.sceneRule.krea2.pass2", SceneRuleKeys.refine(ScenePromptFormat.ENGLISH_NATURAL, 2))
        assertEquals("image.sceneRule.z.pass3", SceneRuleKeys.refine(ScenePromptFormat.CHINESE_NATURAL, 3))
        assertEquals("image.sceneVerify.krea2", SceneRuleKeys.verify(ScenePromptFormat.ENGLISH_NATURAL))
        assertEquals("image.sceneVerify.z", SceneRuleKeys.verify(ScenePromptFormat.CHINESE_NATURAL))
        assertEquals("image.sceneVerify.wai", SceneRuleKeys.verify(ScenePromptFormat.ENGLISH_TAGS))
    }

    @Test
    fun `池标记必须和规则 key 同系`() {
        // 生产代码会先把模型名 lowercase 再匹配（见 ImageGenerationRuleResolver.poolRule），
        // 所以这里也按小写样本断言。
        fun matches(format: ScenePromptFormat, modelName: String): Boolean =
            SceneRuleKeys.poolMarkers(format).any { modelName.lowercase().contains(it) }

        // krea2 形态只认 krea2 系模型
        assertTrue(matches(ScenePromptFormat.ENGLISH_NATURAL, "krea2-turbo-uncensored-v1.1"))
        assertFalse(matches(ScenePromptFormat.ENGLISH_NATURAL, "z-image-turbo"))
        // z-image 形态认 z-image / lumina / qwen-image，且**不**去匹配 krea2
        assertTrue(matches(ScenePromptFormat.CHINESE_NATURAL, "z-image-turbo-nsfw"))
        assertTrue(matches(ScenePromptFormat.CHINESE_NATURAL, "Lumina2"))
        assertTrue(matches(ScenePromptFormat.CHINESE_NATURAL, "qwen-image-edit"))
        assertFalse(matches(ScenePromptFormat.CHINESE_NATURAL, "krea2-turbo-uncensored-v1.1"))
        // 英文 tag 形态认 wai / illustrious / sdxl
        assertTrue(matches(ScenePromptFormat.ENGLISH_TAGS, "waiNSFWIllustrious_v90"))
    }

    @Test
    fun `每个形态都有规则 key 与池标记，不会漏分支`() {
        ScenePromptFormat.entries.forEach { format ->
            assertTrue("$format 缺规则 key", SceneRuleKeys.sceneRule(format).startsWith("image.sceneRule."))
            assertTrue("$format 缺校验 key", SceneRuleKeys.verify(format).startsWith("image.sceneVerify."))
            assertTrue("$format 缺池标记", SceneRuleKeys.poolMarkers(format).isNotEmpty())
        }
    }
}
