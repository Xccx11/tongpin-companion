package com.aster.companion.lite

/**
 * 私聊 / 朋友圈「发照片」的一次请求（计划书 §5.4）。
 *
 * 生命周期：[ScenePhotoStage.PLANNING] → [ScenePhotoStage.READY]（可预览、可改标签）
 * → 用户确认 → [ScenePhotoStage.GENERATING]；任一步失败进入 [ScenePhotoStage.FAILED]，可手动重试。
 */
enum class ScenePhotoStage { PLANNING, READY, GENERATING, FAILED }

data class ScenePhotoRequest(
    val threadId: String,
    val characterId: String,
    /** 朋友圈配图时的动态 id；非空表示确认后走 generate_moment_image 而不是私聊出图。 */
    val momentPostId: String? = null,
    /** 用户这次想要的画面（可为空，由 Scene 流水线按上下文补全）。 */
    val intent: String = "",
    /** 模型产出的画面描述（未拼人物锚点），[prompt] 由它拼出来。 */
    val sceneText: String = "",
    /** 模型产出并拼好的渲染提示词，READY 之后可被用户编辑。 */
    val prompt: String = "",
    /** 用户是否手改过 [prompt]；改过就以用户写的为准，不再自动补人物锚点。 */
    val promptEdited: Boolean = false,
    /**
     * 她自己决定要拍的画面（一句中文，单独一行显示给用户）。
     * 只给人看，**绝不进渲染 tag**。
     */
    val shot: String? = null,
    /**
     * 她为什么想让你看这一张（一句中文，单独一行显示给用户）。
     * 只给人看，**绝不进渲染 tag**。
     */
    val reason: String? = null,
    val stage: ScenePhotoStage = ScenePhotoStage.PLANNING,
    /** Scene 实际尝试次数。 */
    val attempts: Int = 0,
    /** 出图前的提醒（外貌为空/中文、场景写成中文、外貌里混了当刻画面词等），仅提示不改内容。 */
    val notes: List<String> = emptyList(),
    /** FAILED 时的原因文案。 */
    val error: String? = null
) {
    /** 是否已经拿到可出图的 tag。 */
    val hasPrompt: Boolean get() = prompt.isNotBlank()
}

/** 状态卡的固定四态文案（计划书 §7.2）。 */
object ScenePhotoStageCopy {
    fun label(stage: ScenePhotoStage): String = when (stage) {
        ScenePhotoStage.PLANNING -> "整理画面"
        ScenePhotoStage.READY -> "确认这张照片"
        ScenePhotoStage.GENERATING -> "生成中"
        ScenePhotoStage.FAILED -> "生成失败"
    }

    /**
     * 失败时**优先显示真实原因**。
     *
     * 以前固定是"点重试再试一次"：用户反复重试，却永远不知道问题在哪——实测就是这个坑
     * 让人以为"发照片功能坏了"（真实原因：「图片提示词」没配，回退到对话模型而它拒答了这类内容）。
     * 有 [error] 就用它，没有再退回那句通用文案。
     */
    fun hint(stage: ScenePhotoStage, error: String? = null): String = when (stage) {
        ScenePhotoStage.PLANNING -> "她自己决定要拍什么…"
        ScenePhotoStage.READY -> "先看清楚要画什么，确认后再生成"
        ScenePhotoStage.GENERATING -> "正在出图，先不着急"
        ScenePhotoStage.FAILED -> error?.trim()?.takeIf { it.isNotBlank() } ?: "点重试再试一次"
    }
}
