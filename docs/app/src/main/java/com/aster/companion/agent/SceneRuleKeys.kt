package com.aster.companion.agent

/**
 * 「形态 → 远程规则的 key / API 池匹配标记」的**唯一映射表**。
 *
 * 以前这些映射散在 [ImageGenerationRuleResolver] 的三个私有函数里，各写一遍 when：
 * 结果 `sceneRuleKey(CHINESE_NATURAL)` 指向 z 那套规则，而 `poolRule(CHINESE_NATURAL)`
 * 却按 krea2 的标记去匹配池条目 —— "用 Z-Image 规则"却去找名字带 krea2 的 API。
 * 映射集中在这里之后，测试能直接盯住它们的一致性。
 *
 * 两个自然语言形态**不是同一套规则**：
 * - [ScenePromptFormat.ENGLISH_NATURAL]（Krea 2）：`image.sceneRule.krea2`，规则书中文写、要求产出英文；
 * - [ScenePromptFormat.CHINESE_NATURAL]（z-image / lumina / qwen-image）：`image.sceneRule.z`，中文自然语言。
 */
internal object SceneRuleKeys {

    fun sceneRule(format: ScenePromptFormat): String = when (format) {
        ScenePromptFormat.NATURAL_LANGUAGE -> "image.sceneRule.z"
        ScenePromptFormat.ENGLISH_NATURAL -> "image.sceneRule.krea2"
        ScenePromptFormat.CHINESE_NATURAL -> "image.sceneRule.z"
        ScenePromptFormat.ENGLISH_TAGS -> "image.sceneRule.wai"
    }

    fun refine(format: ScenePromptFormat, pass: Int): String = when (format) {
        ScenePromptFormat.NATURAL_LANGUAGE -> "image.sceneRule.z.pass$pass"
        ScenePromptFormat.ENGLISH_NATURAL -> "image.sceneRule.krea2.pass$pass"
        ScenePromptFormat.CHINESE_NATURAL -> "image.sceneRule.z.pass$pass"
        ScenePromptFormat.ENGLISH_TAGS -> "image.sceneRule.wai.pass$pass"
    }

    fun verify(format: ScenePromptFormat): String = when (format) {
        ScenePromptFormat.NATURAL_LANGUAGE -> "image.sceneVerify.z"
        ScenePromptFormat.ENGLISH_NATURAL -> "image.sceneVerify.krea2"
        ScenePromptFormat.CHINESE_NATURAL -> "image.sceneVerify.z"
        ScenePromptFormat.ENGLISH_TAGS -> "image.sceneVerify.wai"
    }

    /** API 池里按模型名匹配专属规则用的标记；必须和 [SceneRuleKeys.sceneRule] 同系。 */
    fun poolMarkers(format: ScenePromptFormat): List<String> = when (format) {
        ScenePromptFormat.NATURAL_LANGUAGE -> Z_IMAGE_MARKERS
        ScenePromptFormat.ENGLISH_NATURAL -> KREA2_MARKERS
        ScenePromptFormat.CHINESE_NATURAL -> Z_IMAGE_MARKERS
        ScenePromptFormat.ENGLISH_TAGS -> TAG_MARKERS
    }

    val Z_IMAGE_MARKERS = listOf("z-image", "z_image", "zimage", "lumina", "qwen-image", "qwen_image")
    val KREA2_MARKERS = listOf("krea2", "krea-2", "krea_2")
    val TAG_MARKERS = listOf("wai", "illustrious", "sdxl")
}
