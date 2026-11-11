package com.aster.companion.agent

/**
 * 生图提示词的「形态」：不同模型的文本编码器吃的东西不一样。
 *
 * - SDXL / Illustrious 系（wai 等）：英文逗号分隔 tag 串，编码器只认关键词；
 * - z-image / lumina / qwen-image 系：Qwen 系编码器，吃**中文自然语言**；
 * - Krea 2 系：Qwen3-VL 编码器，吃**自然语言长句**，中英文都认。
 *
 * 这决定了四件事：给 Scene 模型哪份规则、外貌要不要转写/转成哪种语言、
 * 提示词怎么切分与清洗（tag 串按逗号切，自然语言不切）、以及卡片上的提示文案。
 */
enum class ScenePromptFormat {
    /** 英文 tag 串。 */
    ENGLISH_TAGS,

    /** 中文（或英文）自然语言描述。 */
    NATURAL_LANGUAGE,

    /** 纯英文自然语言长句。 */
    ENGLISH_NATURAL,

    /**
     * 中文自然语言描述（z-image / lumina / qwen-image 专用）。
     *
     * 这一系的文本编码器是 Qwen 系、吃中文自然语言，而且**规则就是 `image.sceneRule.z` 那套**
     * （写实摄影、禁光禁脸红）。与 [NATURAL_LANGUAGE] 分开只是为了保留一个"当前没有模型走"的枚举位。
     */
    CHINESE_NATURAL;

    /** 是不是「自然语言」形态（句子而不是 tag 串）——切分、清洗、拼接都按这个分支走。 */
    val isNaturalLanguage: Boolean get() = this != ENGLISH_TAGS

    /**
     * **写给模型的指令**（不是最终提示词）该不该用中文。
     *
     * z-image / lumina 和 Krea 2 的文本编码器都是 Qwen 系，认中文；
     * 英文 tag 形态（Illustrious 等）不行，必须发英文指令。
     * 这跟"最终提示词用什么语言"是同一件事的两面，所以放在形态上统一定义，别各处各判一遍。
     */
    val wantsChineseInstructions: Boolean
        get() = this == NATURAL_LANGUAGE || this == CHINESE_NATURAL

    /**
     * 提示词里是否可以出现中文。
     *
     * z-image / lumina 和 **Krea 2** 的文本编码器都是 Qwen 系（Krea 2 用的是 Qwen3-VL），**认得中文**；
     * 只有 SDXL / Illustrious 那类英文 tag 模型不行（所以那种形态下要剥掉中文）。
     */
    val allowsChinese: Boolean get() = this != ENGLISH_TAGS

    companion object {
        /**
         * 按图像模型名判断形态；认不出来按英文 tag 处理。
         *
         * **设置页的「生图规则集」优先**：用户显式选了 Krea 2 / Z-Image / WAI 就用那套，
         * 这样换成官方图像 API 或没见过的模型名时，也不会莫名其妙掉到 tag 规则去。
         */
        fun forModel(modelName: String): ScenePromptFormat {
            com.aster.companion.lite.ImageRuleFormatOverride.current()?.let { return it }
            val name = modelName.trim().lowercase()
            // Krea 2 走英文自然语言（官方 prompting.md 的示例、expansion.txt 全是英文，
            // 它对英文的遵循度明显高于中文）；z-image 系仍走中文自然语言。
            return when {
                KREA2_MARKERS.any { name.contains(it) } -> ENGLISH_NATURAL
                ZIMAGE_MARKERS.any { name.contains(it) } -> CHINESE_NATURAL
                NATURAL_LANGUAGE_MARKERS.any { name.contains(it) } -> NATURAL_LANGUAGE
                else -> ENGLISH_TAGS
            }
        }

        /**
         * 走**英文自然语言 + `image.sceneRule.krea2en`** 的模型。
         *
         * Krea 2 的官方文档（prompting.md 的示例、给 LLM 的 expansion.txt）**全是英文**，
         * 实测英文提示词的遵循度明显高于中文，所以它单独走英文这一套。
         */
        private val KREA2_MARKERS = listOf("krea2", "krea-2", "krea_2")

        /**
         * 走**中文自然语言 + `image.sceneRule.z`** 的模型（z-image / lumina / qwen-image）。
         *
         * 它们的编码器同样是 Qwen 系、认中文，所以输入保持中文；规则用 z 那套（写实摄影、
         * 不写光线、禁脸红），**不是** Krea 2 那套 —— Krea 2 的规则要求产出英文，两者不能混用。
         */
        private val ZIMAGE_MARKERS = listOf(
            "z-image", "z_image", "zimage", "lumina", "qwen-image", "qwen_image",
        )

        /** 目前没有模型走这条（保留枚举和 `image.sceneRule.z`，方便随时切回去）。 */
        private val NATURAL_LANGUAGE_MARKERS = emptyList<String>()
    }
}
