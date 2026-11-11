package com.aster.companion.agent

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验轮要同时拿到「画面草稿」（需求方）和「已经写好的提示词」（待核对），
 * 并且按当前形态要求输出语言——否则它会把英文改成中文、或反过来。
 */
class SceneVerifierTest {

    @Test
    fun `校验轮的 system 带规则与外貌，user 带草稿与待核对提示词`() {
        val prompt = SceneVerifier.prompt(
            rule = "【校验规则占位】",
            draft = "她蜷缩在男人怀里，已经全脱了",
            appearance = "一位22岁左右的东亚少女",
            candidate = "She lies on the bed wearing a white shirt.",
            format = ScenePromptFormat.ENGLISH_NATURAL
        )

        assertTrue(prompt.system.contains("【校验规则占位】"))
        assertTrue(prompt.system.contains("一位22岁左右的东亚少女"))
        assertTrue(prompt.system.contains("英文自然语言长句"))
        assertTrue(prompt.user.contains("她蜷缩在男人怀里，已经全脱了"))
        assertTrue(prompt.user.contains("She lies on the bed wearing a white shirt."))
    }

    @Test
    fun `中文形态要求输出中文`() {
        val prompt = SceneVerifier.prompt(
            rule = "【校验规则占位】",
            draft = "草稿",
            appearance = "",
            candidate = "提示词",
            format = ScenePromptFormat.CHINESE_NATURAL
        )

        assertTrue(prompt.system.contains("中文自然语言"))
        assertTrue(!prompt.system.contains("一位22岁"))
    }

    @Test
    fun `内置兜底规则覆盖四类错误`() {
        val rule = SceneVerifier.defaultRule(ScenePromptFormat.ENGLISH_NATURAL)

        assertTrue(rule.isNotBlank())
        assertTrue(rule.contains("全脱"))
        assertTrue(rule.contains("景别"))
        assertTrue(rule.contains("名字"))
        assertTrue(rule.contains("心情词"))
    }
}
