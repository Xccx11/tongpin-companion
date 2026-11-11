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
    private val client: OpenAiCompatibleClient,
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
    suspend fun inputsFor(characterId: String): ScenePromptInputs? {
        val character = dao.character(characterId) ?: return null
        return ScenePromptInputs(
            character = character,
            conversationProfile = ImagePromptModel.resolve(dao),
            format = imageRuleResolver.format()
        )
    }


    /** 从私聊线程取最近上下文，按 [LiteProductConfig.SCENE_RECENT_MESSAGES] 轮压缩成简报。 */
    suspend fun recentBrief(threadId: String): List<String> = dao.recentMessages(threadId, LiteProductConfig.SCENE_RECENT_MESSAGES)
        .asReversed()
        .mapNotNull { message ->
            val body = message.body.trim().replace(WHITESPACE, " ")
            if (body.isEmpty() || message.id.startsWith("stream:")) return@mapNotNull null
            if (message.fromUser) {
                "User: ${body.take(LiteProductConfig.SCENE_BRIEF_CHARS)}"
            } else {
                val speaker = dao.character(message.characterId.orEmpty())?.name?.trim().orEmpty().ifBlank { "Character" }
                "$speaker: ${body.take(LiteProductConfig.SCENE_BRIEF_CHARS)}"
            }
        }

    /**
     * 第一步：她自己决定这次拍什么。
     *
     * 以她此刻真实的生活状态为主（在做什么、心情、在哪、时段），聊天只作参考；
     * 返回 null 表示这次没想清楚，调用方回退到单步流程。
     * [inputs] 由操作入口取好传进来时就不再回查数据库。
     */
    suspend fun decideShot(
        threadId: String,
        characterId: String,
        herOwnWords: String = "",
        useBackgroundGate: Boolean = false,
        inputs: ScenePromptInputs?
    ): CharacterPhotoShotPolicy.PhotoShot? {
        // ⚠️ 真机上 `resolved` 曾**以 null 到达下一行**（崩溃栈点名 `ScenePromptInputs.character`
        // 在 null 上被读）。源码的 `?:` 链在语义上兜住了，但这条链是 suspend + 返回 `Result`
        // （value class）的签名，实测在装箱/拆箱边界上会出现"类型上非空、运行时是 null"。
        // 这里再夹一道**运行时**检查：`as?` 遇到真 null 就返回 null —— 这次拍摄直接放弃，
        // 而不是 NPE 闪退。
        val resolved = (inputs ?: inputsFor(characterId)) as? ScenePromptInputs ?: return null
        val character = resolved.character
        val profile = resolved.conversationProfile ?: return null
        if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return null
        val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
        val input = CharacterPhotoShotPolicy.Input(
            characterName = character.name,
            personalityPrompt = character.personalityPrompt,
            relationshipPrompt = character.relationshipPrompt,
            currentSituation = situationOf(characterId),
            timeOfDay = timeOfDay(),
            recent = recentBrief(threadId),
            herOwnWords = herOwnWords
        )
        val raw = chatOnce(
            profile = profile,
            apiKey = apiKey,
            system = CharacterPhotoShotPolicy.system(input),
            user = CharacterPhotoShotPolicy.user(),
            useBackgroundGate = useBackgroundGate
        ) ?: return null
        return CharacterPhotoShotPolicy.parse(raw)
    }

    /**
     * 她此刻的处境：**在做什么 · 在哪 · 什么心情 · 身上什么状态**。
     *
     * 这里必须带上**地点**。少了它，模型只知道她在「看剧」，却不知道是在家、在朋友家、
     * 还是在宿舍——出的图就会出现「人是对的、场景是编的」。这正是「照片不像她的生活」的根源：
     * 她明明在图书馆，图里却是一间谁也认不出的房间。
     */
    private suspend fun situationOf(characterId: String): String {
        val living = dao.livingState(characterId)
        val place = living?.locationId?.takeIf { it.isNotBlank() }
            ?.let { dao.worldLocation(it)?.displayName ?: it }
        val detail = listOfNotNull(
            living?.currentActivity?.takeIf { it.isNotBlank() },
            place?.let { "在$it" },
            living?.mood?.takeIf { it.isNotBlank() }?.let { "心情 $it" }
        ).joinToString(" · ")
        // 这段是**背景**，不是这次的拍摄指令。原先拼成"她此刻正在：XXX"，
        // 生活引擎排的活动看起来最像重点，于是压过了对话内容——
        // 她那边走生活时间线，和你们聊的内容没关系，照它出图就出戏。
        if (detail.isBlank()) return ""
        return "她的生活背景（仅供参考，不要照搬；和你们正在聊的内容对不上时，以聊天内容为准）：$detail"
    }

    /**
     * 时段 + **具体时刻**。
     *
     * 只给「下午」是不够的：15:00 和 19:00 她的处境差得很远（一个大概在外面，一个在家吃饭），
     * 照片的地点、手上在做的事都该跟着变。
     */
    private fun timeOfDay(): String {
        val now = java.time.LocalTime.now()
        val period = when (now.hour) {
            in 5..8 -> "清晨"
            in 9..11 -> "上午"
            in 12..13 -> "中午"
            in 14..17 -> "下午"
            in 18..22 -> "晚上"
            else -> "深夜"
        }
        return "%s %02d:%02d".format(period, now.hour, now.minute)
    }

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
        buildTagPrompt(characterId, intent, recentBrief(threadId), useBackgroundGate, viewpoint, wantReason, shot, inputs)
    }

    /** 纯逻辑入口：给定简报直接产出 tag 串，便于单测与朋友圈复用。 */
    suspend fun buildTagPrompt(
        characterId: String,
        intent: String,
        recent: List<String>,
        useBackgroundGate: Boolean = false,
        viewpoint: Boolean = false,
        wantReason: Boolean = false,
        shot: CharacterPhotoShotPolicy.PhotoShot? = null,
        inputs: ScenePromptInputs?
    ): Draft {
        // 同 `decideShot`：运行时兜底，见那里的说明。
        val resolved = (inputs ?: inputsFor(characterId)) as? ScenePromptInputs
            ?: error("Character unavailable")
        val character = resolved.character
        val profile = requireNotNull(resolved.conversationProfile) {
            "Conversation model is not configured"
        }
        require(profile.enabled && !profile.encryptedApiKey.isNullOrBlank()) { "Conversation model is disabled" }
        val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
        // 提示词形态跟着当前图像模型走：wai 系出英文 tag，z-image 系出中文自然语言。
        val format = resolved.format
        val system = ScenePromptBuilder.system(
            characterName = character.name,
            savedRule = imageRuleResolver.resolveSceneRule(format),
            format = format
        )
        val briefing = ScenePromptBuilder.Briefing(
            characterName = character.name,
            // 她的性格：穿搭/动作/神态的推理依据，缺了它模型只能靠长相硬猜。
            personality = character.personalityPrompt,
            appearancePrompt = character.appearancePrompt,
            // 长相要点 = 把用户档案**转写成一句英文长相描述**（9/13 的做法）。
            // 9/13 那版出图提示词里固定带着 `a young East Asian woman, slim narrow eyes,
            // soft flat facial features, a small nose` 这种东亚特征锚点，就是这一步产出的；
            // 后来改成「原文一字不改拼上去」，现在按用户要求回到转写这一路。
            appearanceAnchor = transcribeAppearance(profile, apiKey, character.appearancePrompt, useBackgroundGate),
            // 连拍一致性：上一次真正送去出图的最终提示词，让这张的穿着/所在场景/光线与上一张接得上。
            lastScene = dao.worldStates(characterId, "LAST_SCENE")
                .maxByOrNull { it.updatedAt }
                ?.content.orEmpty(),
            recentMessages = recent,
            intent = intent,
            currentSituation = situationOf(characterId),
            viewpoint = viewpoint,
            decidedShot = shot?.shot,
            decidedWhy = shot?.why,
            shotType = shot?.type ?: CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER
        )
        // 她自己已经给出「为什么」时就不再向模型多要一行。
        val user = ScenePromptBuilder.user(briefing, wantReason && shot?.why == null, format)
        var lastReasons: List<String> = emptyList()
        // 中文只留给 NATURAL_LANGUAGE（z-image 系，靠 Qwen 编码器吃中文）；Krea 2 是英文自然语言，
        // 放行中文会让「小琴真好看」这种中文碎句混进提示词。refine() 沿用这个值，所以只算一处。
        // 只有英文 tag 模型才需要剥掉中文；z-image / Krea 2 的编码器是 Qwen 系，中文要留着。
        val stripCjk = !format.allowsChinese
        // 自然语言形态（z-image / Krea 2）都是**按模块分段的纯文本**，清洗时必须保着换行。
        val keepLineBreaks = format.isNaturalLanguage
        // 到底走的是哪个模型/哪个地址，值得每次都记一行：配了「图片提示词」却不生效这类问题，
        // 靠这行日志一眼就能看出来（实测踩过：这条链曾写死用对话模型，用户配了也白配）。
        Log.i(
            TAG,
            "scene prompt model kind=${profile.kind} url=${profile.baseUrl} " +
                "model=${profile.modelName} timeout=${profile.timeoutSeconds}s format=$format"
        )
        var lastNullAttempt: String? = null
        for (attempt in 1..LiteProductConfig.SCENE_MAX_ATTEMPTS) {
            val startedAt = System.currentTimeMillis()
            val raw = chatOnce(profile, apiKey, system, user, useBackgroundGate)
            if (raw == null) {
                // ⚠️ 这里以前写的是 `?: error("Scene model timeout or empty reply")` ——
                // **第 1 次拿不到就整个抛出，`SCENE_MAX_ATTEMPTS` 这个重试循环等于白写**
                //（用户现象：永远"点重试再试一次"，从不给自己第二次机会）。
                // 现在只记下原因并继续下一轮；两次都不行才由循环末尾统一抛错。
                lastNullAttempt = "第 $attempt 次没拿到内容（超时或空回复），耗时 " +
                    "${System.currentTimeMillis() - startedAt}ms"
                Log.w(TAG, "场景提示词 $lastNullAttempt")
                continue
            }
            val split = PhotoReplyLines.split(raw, format)
            // 占位符两种模型都读不了；中文只留给 z-image 系（NATURAL_LANGUAGE），其余形态剥掉。
            val cleaned = ComfyTagPromptPolicy.sanitizeScene(
                prompt = ComfyTagPromptPolicy.stripFences(split.tags),
                stripCjk = stripCjk,
                keepLineBreaks = keepLineBreaks
            )
            // 硬过滤拍摄器材：照片是「她眼睛看到的画面」，出现手机/相机会把构图切开。
            // 注意：**不要在这一轮补景别**——这里产出的还是中文草稿，补进去的景别句会与中文
            // 混排、污染草稿（实测紧接着 pass2 就把「28-year-old」「close-up」这些错误写进最终
            // 提示词）。景别由 pass2 的规则负责，代码兜底放在最终提示词那一步。
            // 器材清洗之后再做一遍**确定性体检**：抹禁令词（光线/阴影/blush）、
            // 解"该行为词 vs 遮挡衣物"的冲突、把含糊的器官说法换成具体词、归一景别。
            // 这一步不调模型、纯规则，命中什么都记在 notes 里便于核对。
            val scrubbed = SceneExposureScrubPolicy.scrub(SceneDeviceSuppression.strip(cleaned, format), format)
            if (scrubbed.notes.isNotEmpty()) {
                android.util.Log.i("TongpinSceneRule", "提示词体检：${scrubbed.notes.joinToString("；")}")
            }
            // **用户要求：返回什么就给什么。**
            // 清洗是为了好看，不是关卡。一旦它把模型给的东西整段删空——最典型的就是
            // "模型回中文、而当前格式只收英文 tag"，`stripCjk` 会把整段抹掉——就退回
            // **未清洗的原文**（只去过代码围栏）。宁可提示词形态不完美，
            // 也不要"每次都 reject、照片根本出不来"。
            val candidate = ComfyTagPromptPolicy.stripFences(split.tags).trim()
            val validation = ComfyTagPromptPolicy.validate(candidate, character.appearancePrompt, character.name)
            // 校验只当参考，不再拦人：只要拿到非空提示词就用。
            // 历史上这里 accepted=false 会直接 error()，把整轮提示词静默废掉，上层就表现为「照片没拍成」。
            if (candidate.isNotBlank()) {
                if (!validation.accepted) {
                    Log.w(TAG, "round1 validation not accepted, using anyway: ${validation.reasons.joinToString(",")}")
                }
                val first = Draft(
                    prompt = candidate,
                    attempts = attempt,
                    reason = shot?.why ?: split.why,
                    shot = shot?.shot,
                    shotType = shot?.type,
                    notes = listOfNotNull(
                        // 「中文读不懂」只对英文 tag 模型成立；z-image 本来就该写中文。
                        if (stripCjk) ComfyTagPromptPolicy.sceneLanguageWarning(candidate) else null
                    )
                )
                // 回到 9/13 的出图流程：**一轮出图**。
                // 9/13 那版没有服饰提取轮、没有递进细化轮、没有出图前校验——
                // 提示词就是这一轮的英文输出（含画质锚点、东亚五官、景别、结尾咒语）。
                // extractOutfit / refine / verify 三个函数都保留着，随时可以再接回来。
                //
                // 但**景别必须兜一道**（用户要「从头看到脚」）：模型爱写 medium / close-up /
                // medium full-body，出图模型只取 medium 就裁成半身——实测连续 6 张没一张全身。
                return first.copy(
                    prompt = SceneFraming.ensure(
                        first.prompt,
                        first.shotType ?: CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER,
                        format
                    )
                )
            }
            lastReasons = validation.reasons
        }
        // 出错了必须说清**它在用哪个模型**。
        //
        // 以前的静默回退害人不浅：「图片提示词」没配 → 悄悄用对话模型 → 那个模型拒答这类内容
        // （回空，不是超时）→ 用户只看到一句没头没尾的 reject，反复以为"发照片功能坏了"。
        // 实测：回退用的 `gemini-3.7-flash-high` 2.9 秒就回空，而 60 秒的超时远没到。
        val reasons = listOfNotNull(
            lastReasons.joinToString(",").takeIf { it.isNotBlank() },
            lastNullAttempt
        ).joinToString("；").ifBlank { "模型没有给出任何内容" }
        val modelTrace = "模型=${profile.modelName} url=${profile.baseUrl} profile=${profile.kind}"
        val advice = if (profile.kind != LiteProductConfig.IMAGE_PROMPT_MODEL) {
            "「图片提示词」这一项没配，所以回退到了对话模型，而它拒答了这类内容。" +
                "到 设置 → 模型 → 图片提示词 配一个能写这类内容的模型即可（实测 deepseek-v4-pro 可以）。"
        } else {
            "「图片提示词」的模型没给出内容（空回复或超时）：换一个模型，或把这一项的超时调大。"
        }
        error("写不出出图提示词：$reasons（$modelTrace）。$advice")
    }

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
    private suspend fun transcribeAppearance(
        profile: ModelProfileEntity,
        apiKey: String,
        appearance: String,
        useBackgroundGate: Boolean
    ): String {
        val anchor = appearance.trim()
        if (anchor.isEmpty()) return anchor
        val rule = runCatching { imageRuleResolver.resolveAppearanceTranscribeRule() }
            .getOrElse { renderPromptBuilder.identityFor(anchor) }
        val reply = chatOnce(
            profile,
            apiKey,
            rule,
            "【她的外貌设定（用户亲手写的，逐项对照，一项都不能省）】\n$anchor",
            useBackgroundGate
        )
        val translated = reply?.let { ComfyTagPromptPolicy.stripFences(it).trim() }.orEmpty()
        if (translated.isBlank()) {
            Log.w(TAG, "appearance transcribe failed, falling back to the raw setting")
            return anchor
        }
        Log.i(TAG, "appearance transcribed: ${translated.take(120)}")
        return translated
    }


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

    private suspend fun chatOnce(
        profile: ModelProfileEntity,
        apiKey: String,
        system: String,
        user: String,
        useBackgroundGate: Boolean
    ): String? {
        val block = suspend {
            withTimeoutOrNull(SCENE_CHAT_TIMEOUT_MS) {
                client.chat(profile, apiKey, system, user).getOrNull()
            }
        }
        return if (useBackgroundGate) {
            com.aster.companion.lite.LiteBackgroundLlmGate.withPermit(block)
        } else {
            block()
        }
    }

    /**
     * 用户在预览卡里改过 tag 后，仍需过同一套校验才能出图。
     *
     * **校验前先剥掉代码拼上去的外貌锚点**：那些启发式规则（自言自语特征、长度上限）针对的是
     * **模型写出来的画面段**；外貌是用户亲手写的，规则不适用——用户写「the user's girlfriend」
     * 不该因为命中 `the user` 就整张图出不来。
     */
    fun validateEditedPrompt(
        prompt: String,
        appearancePrompt: String,
        characterName: String = ""
    ): ComfyTagPromptPolicy.Validation =
        ComfyTagPromptPolicy.validate(
            AppearanceAnchor.strip(prompt, appearancePrompt),
            appearancePrompt,
            characterName
        )

    private companion object {
        const val TAG = "ScenePrompt"
        const val SCENE_CHAT_TIMEOUT_MS = 90_000L
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * 模型回复里除了 tag 串，还会多一行「为什么想让你看」。
 * 这一行必须是中文（否则视为模型写错了，整段保留为 tag），且**永不进渲染 tag**。
 */
internal object PhotoReplyLines {

    data class Lines(val tags: String, val why: String?)

    fun split(raw: String, format: ScenePromptFormat = ScenePromptFormat.ENGLISH_TAGS): Lines {
        val match = WHY_LINE.find(raw) ?: return Lines(ImageGenerationPromptPolicy.singleLine(raw, format), null)
        val candidate = match.groupValues[1].trim().take(REASON_MAX_CHARS)
        if (!candidate.any { it.code in CJK_RANGE }) {
            return Lines(ImageGenerationPromptPolicy.singleLine(raw, format), null)
        }
        // 正文压成一行：tag 形态按逗号切，自然语言形态只压空白。
        val tags = ImageGenerationPromptPolicy.singleLine(raw.removeRange(match.range), format)
        return Lines(tags, candidate)
    }

    /** 「为什么」这一行最长留多少字（只给人看）。 */
    private const val REASON_MAX_CHARS = 60

    private val CJK_RANGE = 0x3400..0x9FFF

    /** WHY: / WHY： / 为什么： / 原因： 开头的整行。 */
    private val WHY_LINE = Regex("(?im)^[ \\t]*(?:WHY|为什么|原因)[ \\t]*[:：][ \\t]*(.+?)[ \\t]*$")
}
