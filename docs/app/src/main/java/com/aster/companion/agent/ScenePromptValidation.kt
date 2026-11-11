package com.aster.companion.agent

/**
 * 用户改过 tag 后的**统一校验**（纯函数，无状态）。
 *
 * 为什么独立出来：校验规则是"出图前最后一道闸"，和"怎么调模型/怎么写提示词"无关；
 * 放在这里可以被单测直接覆盖，也不会被协调器的依赖变化牵连。
 *
 * 实现要点：校验前先剥掉代码拼上去的**外貌锚点** —— 那些启发式规则（自言自语特征、长度上限）
 * 针对的是模型写出来的画面段；外貌是用户亲手写的，规则不适用。
 */
internal object ScenePromptValidation {

    fun validate(
        prompt: String,
        appearancePrompt: String,
        characterName: String = "",
    ): ComfyTagPromptPolicy.Validation =
        ComfyTagPromptPolicy.validate(
            AppearanceAnchor.strip(prompt, appearancePrompt),
            appearancePrompt,
            characterName,
        )
}