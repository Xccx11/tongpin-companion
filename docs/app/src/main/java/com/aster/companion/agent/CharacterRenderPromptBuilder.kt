package com.aster.companion.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 角色出图提示词的统一拼装口：**场景描述 → 渲染提示词**。
 *
 * 为什么要有这个类：形态（英文 tag / 中文自然语言）必须**全链路一致**——
 * 卡片预览、确认出图、真正渲染三处若各拼一套，就会出现「预览是中文、渲染又把 tag 拼回去」这种前后不一致。
 *
 * 画面主体由上游模型自己判断：这里不再猜主体、也不再硬拼构图前缀。
 *
 * 形态（[ScenePromptFormat]）决定怎么拼：
 * - 英文 tag 模型（wai / illustrious / sdxl）：用用户填的外貌原文；
 * - 自然语言模型（z-image / lumina）：同样用外貌原文（谁写的中文就是中文）。
 */
@Singleton
class CharacterRenderPromptBuilder @Inject constructor(
    private val ruleResolver: ImageGenerationRuleResolver
) {
    /** 当前图像模型的提示词形态（调用方已经算过一次时就不用再问）。 */
    suspend fun format(): ScenePromptFormat = ruleResolver.format()

    /**
     * 拼最终渲染提示词：上游模型已按十段写全（含长相），这里只按形态单行化。
     *
     * 外貌**必须**在这里接上：给 Scene 模型的规则明确写着「不许复述长相」
     * （见 `ImagePromptDebugConfig.DEFAULT_RULE` 的 `No identity restatement`），
     * 所以"让模型自己写长相"那条路是断的；[AppearanceAnchor.attach] 按提示词形态决定怎么接
     * （中文形态换行拼原文，英文 tag 形态单行逗号拼）。
     */
    suspend fun compose(scene: String, identity: String): String = compose(scene, format(), identity)

    /**
     * **方案 A（用户要求，2026-10-11）：长相由场景模型写进画面，代码只做「校验 + 兜底补」。**
     *
     * 以前这里**无条件**把外貌原文整段接在最前面，于是最终提示词永远是
     * 「第 1 段：用户外貌原文粘贴」+「第 2 段：模型自己写的场景」——**两段各描述一遍同一个人、
     * 用词还不一样**，渲染器只能把它们平均或挑一份（用户现象：**图片不合理**）。
     *
     * 现在：正文里**已经写了身份特征**（发色/发型/瞳色/脸型…）就**一个字都不接**；
     * 真的缺了才用 [identityFor] 补一句**压缩过的**身份要点（不是原文整段）。
     *
     * ⚠️ 配套还差一半：`ImagePromptDebugConfig.DEFAULT_RULE` 里那句 `No identity restatement`
     * 仍在**禁止**模型写长相——那句必须同时放开，模型才敢把身份写进画面（见交接文档"未完成"）。
     */
    fun compose(scene: String, format: ScenePromptFormat, identity: String = ""): String {
        val body = ImageGenerationPromptPolicy.singleLine(scene, format)
        if (identity.isBlank() || MENTIONS_IDENTITY.containsMatchIn(body)) return body
        return AppearanceAnchor.attach(body, identityFor(identity), format)
    }

    /** 正文是否已经写了身份特征 —— 只认**具体**特征词，不认泛泛的 "she/her"。 */
    private val MENTIONS_IDENTITY = Regex(
        "(?i)\\b(hair|braid|bangs|fringe|eyes?|eyelashes|lashes|pupils?|iris|eyebrows?|face|jawline|chin|" +
            "cheeks?|nose|lips?|skin|complexion|figure|build|breasts?|waist|hips?|thighs?)\\b" +
            "|\u5934\u53d1|\u53d1\u8272|\u53d1\u578b|\u773c\u775b|\u77b3\u8272|\u8138\u578b|\u4e0b\u988c|\u9f3b|\u5634\u5507|\u80a4\u8272|\u8eab\u6750"
    )

    /**
     * 只取「主体」那一段；预览与最终拼装都用它，保证前后一致。
     *
     * 外貌原文**一字不改**当长相要点用，不再送模型重写：重写那一步会顺手省掉细节（脸型、下颌线
     * 最容易丢），而且用户写中文时重写会把中文换成英文，出图提示词里就没有他写的那段锚点了。
     * 穿什么、做什么、在哪仍然由她此刻决定（见 [CharacterIdentityPromptPolicy]）。
     */
    fun identityFor(appearance: String): String =
        CharacterIdentityPromptPolicy.identityOnly(appearance)
}
