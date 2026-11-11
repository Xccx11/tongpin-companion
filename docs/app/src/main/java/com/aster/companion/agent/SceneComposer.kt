package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.lite.LiteProductConfig
import android.util.Log
import javax.inject.Inject

/** 场景**提示词组装**：把决策结果 + 规则 + 外貌拼成最终 tag（簇 B）。 */
@javax.inject.Singleton
class SceneComposer @Inject constructor(
    private val dao: com.aster.companion.data.CompanionDao,
    private val transport: SceneTransport,
    private val cipher: com.aster.companion.data.CompanionSecretCipher,
    private val imageRuleResolver: ImageGenerationRuleResolver,
    private val renderPromptBuilder: CharacterRenderPromptBuilder,
) {
    private val situationReader = SceneSituationReader(dao = dao)
    private val inputsReader = SceneInputsReader(dao = dao, imageRuleResolver = imageRuleResolver)
    private val WHITESPACE = Regex("\\s+")
    private val TAG = "ScenePrompt"

    private val appearanceTranscriber = SceneAppearanceTranscriber(transport = transport, imageRuleResolver = imageRuleResolver, renderPromptBuilder = renderPromptBuilder)

    suspend fun buildTagPrompt(
        characterId: String,
        intent: String,
        recent: List<String>,
        useBackgroundGate: Boolean = false,
        viewpoint: Boolean = false,
        wantReason: Boolean = false,
        shot: CharacterPhotoShotPolicy.PhotoShot? = null,
        inputs: ScenePromptCoordinator.ScenePromptInputs?
    ): ScenePromptCoordinator.Draft {
        // 同 `decideShot`：运行时兜底，见那里的说明。
        val resolved = (inputs ?: inputsReader.inputsFor(characterId)) as? ScenePromptCoordinator.ScenePromptInputs
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
            appearanceAnchor = appearanceTranscriber.transcribeAppearance(profile, apiKey, character.appearancePrompt, useBackgroundGate),
            // 连拍一致性：上一次真正送去出图的最终提示词，让这张的穿着/所在场景/光线与上一张接得上。
            lastScene = dao.worldStates(characterId, "LAST_SCENE")
                .maxByOrNull { it.updatedAt }
                ?.content.orEmpty(),
            recentMessages = recent,
            intent = intent,
            currentSituation = situationReader.situationOf(characterId),
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
            val raw = transport.chatOnce(profile, apiKey, system, user, useBackgroundGate)
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
                val first = ScenePromptCoordinator.Draft(
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
}
