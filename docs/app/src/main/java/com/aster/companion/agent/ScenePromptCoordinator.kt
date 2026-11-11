package com.aster.companion.agent

import android.util.Log
import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.lite.LiteProductConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Scene 流水线的协调器（计划书 §5）：人物锚点 + 最近简报 + 本次意图 → 渲染提示词。
 *
 * 输出形态跟着**当前图像模型**走：wai/illustrious 出英文 tag 串，z-image/lumina 出中文自然语言
 * （[ImageGenerationRuleResolver.resolveSceneRule] + [ScenePromptFormat]）。
 *
 * 约束：
 * - 只用对话模型写提示词，不占显存出图；
 * - 输出不合格自动重试 ≤ [LiteProductConfig.SCENE_MAX_ATTEMPTS] 次，仍失败则抛错交给状态卡手动重试；
 * - 结果只交给出图链路，**不写回聊天记录**（计划书 §5.4）。
 * - 用户点「发照片」时**不**走后台 LLM 闸，避免被探活占满后一直「整理画面」。
 */
@Singleton
class ScenePromptCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val inputsReader: SceneInputsReader,
    private val situationReader: SceneSituationReader,
    private val client: OpenAiCompatibleClient,
    private val transport: SceneTransport,
    private val decider: SceneDecider,
    private val composer: SceneComposer,
    private val appearanceTranscriber: SceneAppearanceTranscriber,
    private val cipher: CompanionSecretCipher,
    private val imageRuleResolver: ImageGenerationRuleResolver,
    private val renderPromptBuilder: CharacterRenderPromptBuilder
) {

    data class Draft(
        /** 校验通过的提示词（英文 tag 串或中文自然语言），可让用户预览/编辑后再出图。 */
        val prompt: String,
        /** 实际尝试次数（1 表示一次通过）。 */
        val attempts: Int,
        /** 她为什么想让你看这一张（一句中文）；只给人看，不进 tag。 */
        val reason: String? = null,
        /** 她自己决定要拍的画面（一句中文）；只给人看，不进 tag。 */
        val shot: String? = null,
        /** 她自己说的拍法决定的画面视角。 */
        val shotType: CharacterPhotoShotPolicy.PhotoShotType? = null,
        /** 出图前的提醒（按当前模型形态决定，例如「tag 写成中文」只对英文 tag 模型有意义）。 */
        val notes: List<String> = emptyList(),
        /** 实际跑成的细化轮数：0 表示只跑了第 1 轮。 */
        val refinedPasses: Int = 0
    )

    /**
     * 一次「发照片」操作入口取好的三样东西：角色行、对话模型 profile、提示词形态。
     *
     * 目的只是**在一次操作里传一次**，让下游不必各自回查数据库；它不是缓存，也不跨操作存活。
     */
    data class ScenePromptInputs(
        val character: CharacterEntity,
        /** 可能没配；缺失时下游按原来的报错处理。 */
        val conversationProfile: ModelProfileEntity?,
        val format: ScenePromptFormat
    )

    /**
     * 操作入口调一次：角色行 + 对话模型 profile + 提示词形态各取一次。
     * 角色取不到时返回 null（等价于原来的「Character unavailable」）。
     */


    /** 从私聊线程取最近上下文，按 [LiteProductConfig.SCENE_RECENT_MESSAGES] 轮压缩成简报。 */

    /**
     * 第一步：她自己决定这次拍什么。
     *
     * 以她此刻真实的生活状态为主（在做什么、心情、在哪、时段），聊天只作参考；
     * 返回 null 表示这次没想清楚，调用方回退到单步流程。
     * [inputs] 由操作入口取好传进来时就不再回查数据库。
     */

    /**
     * 她此刻的处境：**在做什么 · 在哪 · 什么心情 · 身上什么状态**。
     *
     * 这里必须带上**地点**。少了它，模型只知道她在「看剧」，却不知道是在家、在朋友家、
     * 还是在宿舍——出的图就会出现「人是对的、场景是编的」。这正是「照片不像她的生活」的根源：
     * 她明明在图书馆，图里却是一间谁也认不出的房间。
     */

    /**
     * 时段 + **具体时刻**。
     *
     * 只给「下午」是不够的：15:00 和 19:00 她的处境差得很远（一个大概在外面，一个在家吃饭），
     * 照片的地点、手上在做的事都该跟着变。
     */

    /**
     * 私聊「发照片」入口：线程上下文自动压缩。
     * [useBackgroundGate] 仅后台主动探活时为 true；用户点击必须 false。
     * [viewpoint] 为 true 时按角色视角写这张她要发的照片；[wantReason] 为 true 时多要一行「为什么」。
     * [shot] 非空表示她自己已经决定拍什么了，本步只把她的决定转成 tag。
     * [inputs] 由操作入口取好传进来时就不再回查数据库。
     */
    suspend fun buildTagPromptForThread(
        threadId: String,
        characterId: String,
        intent: String,
        useBackgroundGate: Boolean = false,
        viewpoint: Boolean = false,
        wantReason: Boolean = false,
        shot: CharacterPhotoShotPolicy.PhotoShot? = null,
        inputs: ScenePromptInputs?
    ): Result<Draft> = runCatching {
        buildTagPrompt(characterId, intent, decider.recentBrief(threadId), useBackgroundGate, viewpoint, wantReason, shot, inputs)
    }

    /** 纯逻辑入口：给定简报直接产出 tag 串，便于单测与朋友圈复用。 */

    /**
     * 单独一轮：从上下文里提取她此刻的穿着状态。
     *
     * 产出四行结构化文本（原本穿的是 / 此刻的状态 / 怎么变的 / 没提到的），
     * 附加到草稿末尾交给细化轮。**失败或空串时返回空，调用方原样用草稿**——
     * 不重试、不兜底，符合项目「失败就明确失败」的约定。
     */
    /**
     * 把用户写的外貌档案**转写成一句地道的英文长相描述**（9/13 的做法）。
     *
     * 规则走 `image.appearanceTranscribe.krea2`：逐项对照一项不能省、脸型必须用具体词、
     * **不许添加原文没有的特征**（不加肤质、毛孔、光泽这类词），并写明东亚五官特征——
     * 9/13 那批出图提示词里的 `slim narrow eyes, soft flat facial features, a small nose`
     * 就是这一步产出的。
     *
     * 转写失败（超时／空回复）时**退回原文**：绝不因为多出来的这一步把长相弄丢。
     */


    /**
     * 递进细化：在第 1 轮已合格的 [base] 上再跑 [LiteProductConfig.SCENE_REFINE_PASSES] 轮，一轮比一轮细。
     * 任一轮无回复、返回空或校验不过，就沿用上一轮的结果并**立刻停掉后续轮次**（不重试、不兜底）。
     */

    /**
     * 出图前的校验轮：拿成品提示词对第 1 轮的原始草稿逐条核对并修正。
     *
     * 校验轮自己失败（超时／空回复）时**沿用原稿**——绝不因为多出来的这一轮把已经写好的
     * 提示词弄丢（历史上按"校验不过就丢弃"处理过，上层表现为「照片没拍成」）。
     */




    /** 委托给 [SceneComposer]（外部调用点保持不变）。 */
    suspend fun buildTagPrompt(
        characterId: String,
        intent: String,
        recent: List<String>,
        useBackgroundGate: Boolean = false,
        viewpoint: Boolean = false,
        wantReason: Boolean = false,
        shot: CharacterPhotoShotPolicy.PhotoShot? = null,
        inputs: ScenePromptInputs?
    ): Draft =
        composer.buildTagPrompt(characterId, intent, recent, useBackgroundGate, viewpoint, wantReason, shot, inputs)

    /**
     * 用户在预览卡里改过 tag 后，仍需过同一套校验才能出图。
     *
     * **校验前先剥掉代码拼上去的外貌锚点**：那些启发式规则（自言自语特征、长度上限）针对的是
     * **模型写出来的画面段**；外貌是用户亲手写的，规则不适用——用户写「the user's girlfriend」
     * 不该因为命中 `the user` 就整张图出不来。
     */


    /** 委托给 [SceneDecider]（外部调用点保持不变）。 */
    suspend fun decideShot(
        threadId: String,
        characterId: String,
        herOwnWords: String = "",
        useBackgroundGate: Boolean = false,
        inputs: ScenePromptInputs?
    ): CharacterPhotoShotPolicy.PhotoShot? =
        decider.decideShot(threadId, characterId, herOwnWords, useBackgroundGate, inputs)

    fun validateEditedPrompt(
        prompt: String,
        appearancePrompt: String,
        characterName: String = ""
    ): ComfyTagPromptPolicy.Validation =
        ScenePromptValidation.validate(prompt, appearancePrompt, characterName)

    private companion object {
        const val TAG = "ScenePrompt"
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * 模型回复里除了 tag 串，还会多一行「为什么想让你看」。
 * 这一行必须是中文（否则视为模型写错了，整段保留为 tag），且**永不进渲染 tag**。
 */