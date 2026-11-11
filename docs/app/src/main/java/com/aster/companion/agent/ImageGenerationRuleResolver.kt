package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.update.RemotePrompts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生图总规则选择器：每个图像 API（池里）可配自己的总规则——
 * 生成时按当前图像生成模型匹配池里的规则（wai 用英文 tag 规则、z-image 用中文自然语言规则），
 * 池里没配就回退全局「生图总规则」（设置里），再空则由调用方用内置。
 */
@Singleton
class ImageGenerationRuleResolver @Inject constructor(
    private val dao: CompanionDao,
    private val imagePromptDebugSettings: ImagePromptDebugSettings,
    private val remotePrompts: RemotePrompts
) {
    /** 当前图像生成模型对应的生图总规则；空表示没有（调用方回退内置）。 */
    suspend fun resolve(): String {
        val modelName = imageModelName()
        poolRule(ScenePromptFormat.forModel(modelName))?.let { return it }
        // 按形态分开的 wai / z-image 专属规则字段没有写入方（恒为空），所以这里只剩两种情况：
        // 形态认不出的模型直接用「生图总规则」原文，wai 系 / z-image 系走下面包装过的指令。
        val cfg = imagePromptDebugSettings.config
        if (cfg.enabled && cfg.globalInstructions.isNotBlank() && !isKnownFormatModel(modelName)) {
            return cfg.globalInstructions
        }
        // 都没有 → 全局总规则（兼容旧配置）
        return ImagePromptDebugInstructionComposer.forSceneModel(cfg)
    }

    /**
     * **Scene 流水线**要用的生图规则：按当前图像模型在「英文 tag 规则」和
     * 「z-image 中文自然语言规则」之间自动切换。
     *
     * 优先级：API 池里该模型的专属规则 → **内置默认规则**（或网关上同名远程提示词）；
     * 设置里的「生图总规则」是**附加要求**，永远叠在选中的规则之上，不会取代它。
     * （曾经它会把内置规范整个顶掉，于是模型收到的规则只剩那一句附加要求，输出自然不按规范。）
     *
     * [format] 由调用方在操作入口算好传进来，避免每次解析都回查图像模型。
     */
    suspend fun resolveSceneRule(format: ScenePromptFormat): String {
        // 顺手刷新网关上的远程提示词（内部节流），这样改提示词不用重装 App。
        runCatching { remotePrompts.refreshIfStale() }
        poolRule(format)?.let { return it }
        val cfg = imagePromptDebugSettings.config
        // 远程值优先于内置默认：网关改了提示词，这里跟手就是新的。
        // 自然语言形态（z-image / Krea 2）都用中文自然语言兜底；英文 tag 形态才用英文兜底。
        val rule = remotePrompts.get(sceneRuleKey(format)) ?: if (format.isNaturalLanguage) {
            ImagePromptDebugConfig.DEFAULT_Z_IMAGE_RULE
        } else {
            ImagePromptDebugConfig.DEFAULT_RULE
        }
        val extra = if (cfg.enabled) cfg.globalInstructions.trim() else ""
        return if (extra.isBlank()) rule else "$rule\n\n额外要求（必须遵守）：\n$extra"
    }

    /**
     * 按形态取远程生图规则的 key。
     *
     * krea2 与 z-image **各有一套规则**，具体映射见 [SceneRuleKeys]（那里是唯一的映射表）。
     */
    private fun sceneRuleKey(format: ScenePromptFormat): String = SceneRuleKeys.sceneRule(format)

    /** 递进细化（第 [pass] 轮）要用的规则：远程优先，拿不到用内置默认。 */
    suspend fun resolveSceneRefineRule(pass: Int, format: ScenePromptFormat): String =
        remotePrompts.get(SceneRuleKeys.refine(format, pass))
            ?: ScenePromptRefiner.defaultRule(format, pass)

    /**
     * 出图前**校验轮**要用的规则：远程优先（`image.sceneVerify.*`），拿不到用内置默认。
     *
     * 为什么要单独一轮：Scene 模型会违背草稿里明写的东西——实测把「已经全脱了」写成穿着白衬衫，
     * 把全身的景别写成 `close-up`，把草稿里的第二个人整个弄丢。最后一轮专门用来纠错。
     */
    suspend fun resolveSceneVerifyRule(format: ScenePromptFormat): String =
        remotePrompts.get(SceneRuleKeys.verify(format)) ?: SceneVerifier.defaultRule(format)

    /**
     * 外貌转写规则：把用户写的外貌档案转成**一句地道的英文长相描述**。
     *
     * 这是 9/13 的做法——那一版出图提示词里固定带着
     * `a young East Asian woman, slim narrow eyes, soft flat facial features, a small nose`
     * 这样的东亚特征锚点，就是这一步产出的。远程优先，拿不到用内置默认。
     */
    suspend fun resolveAppearanceTranscribeRule(): String {
        runCatching { remotePrompts.refreshIfStale() }
        return remotePrompts.get("image.appearanceTranscribe.krea2")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_APPEARANCE_TRANSCRIBE_RULE
    }

    /** 远程拿不到时的兜底；与网关上的 `image.appearanceTranscribe.krea2`（9/13 版 + 禁篡改）同义。 */
    private val DEFAULT_APPEARANCE_TRANSCRIBE_RULE =
        """【**最高优先级：用户写的外貌一个字都不许改**】
下面这段是用户亲手定的，是「她是谁」的唯一依据。你的活只是**把它变成英文**：
- **逐项都要在**：用户写了几项就写几项，少一项就是篡改。
- **不许改数值**：年龄、身高、尺寸、颜色、材质——写的是多少就是多少。
- **不许换词**：不许换成「更好听 / 更常见」的近义词，不许把具体说法泛化成 "delicate features" / "nice face" / "attractive" 这种空话。
- **不许美化、不许削弱、不许补充**：不加用户没写的特征，也不删用户写的东西。
- **只做语言转换**：一个特征对应一个英文说法，绝不意译、绝不概括。
违反上面任何一条，就是在篡改她的长相——这是**最严重的错误**。
你把角色外貌写成一句地道的英文自然语言描述，**只写长相**：多大、脸型、眼睛、头发、皮肤、身材。**不要添加原文里没有的特征**（不加肤质、毛孔、光泽这类用户没写的描述）。人物是东亚女性：开头就要写清 `a young East Asian woman`，五官必须是东亚特征——眼睛偏细长、面部轮廓偏平缓、鼻子小巧、黑色或深棕色头发与瞳色。**绝对不要**写成欧美面孔（高鼻梁、深眼窝、立体骨相）。
**逐项对照，输入里有的一项都不能省**：脸型、眼睛、头发、肤色、身材。
**脸型必须写、而且要用具体词**（oval / round / square / heart-shaped / long / pointed 之类），不许含糊成 "delicate face" / "nice face" 这种——那就等于没写。
**不要写暴露状态、相关部位或生理状态**：nipples / areola / pussy / erect / wet 这类词一个都不许出现——那些属于每张画面自己的内容，不该写进长相里。
不要写穿什么、做什么、表情、视角、背景。不要写脸红（blush）。只输出这一句纯英文，不要解释。"""

    /**
     * 服饰提取规则（在「画面草稿」和「最终提示词」之间单独跑一轮）。
     *
     * 为什么要独立一轮：衣服的信息散在聊天记录里，让「写画面描述」那一轮顺带带上，
     * 它就会顺手丢（实测出现过上下文说了白衬衫包臀裙、草稿里一件都没有的情况）。
     * 这一轮只干一件事，注意力集中，产出结构化的四行，下一步照抄即可。
     */
    suspend fun resolveOutfitExtractRule(): String {
        runCatching { remotePrompts.refreshIfStale() }
        return remotePrompts.get("image.outfitExtract.krea2")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_OUTFIT_EXTRACT_RULE
    }

    /** 远程拿不到时的兜底规则；和网关上的 `image.outfitExtract.krea2` 保持一致。 */
    private val DEFAULT_OUTFIT_EXTRACT_RULE = """你只管一件事：**提取她此刻的穿着状态**。不写画面、不写动作、不写表情、不写环境。

    输入给你三样：(a) 你们最近的对话，(b) 已经写好的画面描述，(c) 她的固定人设。

    **按这个格式输出，四行，一行一项，不要多写别的：**

    原本穿的是：<上下文中提到的每一件衣物 —— 颜色 + 材质 + 款式，一件不落；完全没提到就写「无」>
    此刻的状态：<现在的穿着状态 —— 完整穿着 / 敞开着 / 褪到腰间 / 滑下一侧肩 / 只剩○○ / 已经全脱了>
    怎么变的：<如果上下文说了是谁弄的、什么时候弄的，就写；没说就写「未提及」>
    没提到的：<上下文和人设都没交代的部分，写「无」；有就写清楚缺什么>

    【几条硬规矩】
    - **上下文里出现过的衣物，一件都不许漏、不许改颜色材质**。
    - **「此刻的状态」是最重要的一行**：上下文说她已经脱了 / 褪到一半，必须写出来。
    - **不许凭空加衣服**：上下文和人设都没有的衣物，一个字都不许编。
    - **不许用模糊词**：不写「内衣」「情趣装」这类概括，要写具体是什么。
    - **都不确定就写「无」/「未提及」**，不要猜。

    只输出那四行，不要前言、不要解释、不要 markdown。"""

    /** 当前图像模型对应的提示词形态（调用方已经算过一次时就不用再问）。 */
    suspend fun format(): ScenePromptFormat = ScenePromptFormat.forModel(imageModelName())

    private suspend fun imageModelName(): String =
        dao.modelProfile(ImageGenerationCoordinator.IMAGE_GENERATION_MODEL)
            ?.modelName.orEmpty().lowercase()

    /** 池里配置的专属规则：**按形态找同名系的条**（z-image 形态找 z-image / lumina / qwen-image 条，krea2 形态找 krea2 条）。 */
    private suspend fun poolRule(format: ScenePromptFormat): String? {
        val markers = SceneRuleKeys.poolMarkers(format)
        val pool = dao.allImageApiPool()
        return pool
            .firstOrNull { entry ->
                entry.enabled && entry.generationRule.isNotBlank() &&
                    markers.any { marker -> entry.modelName.lowercase().contains(marker) }
            }
            ?.generationRule
    }

    /** 形态认得出的模型（wai / illustrious / sdxl / z-image / lumina / qwen-image / krea2 系）。 */
    private fun isKnownFormatModel(modelName: String): Boolean =
        isNaturalLanguageModel(modelName) || isTagModel(modelName)

    /** z-image / lumina / qwen-image 和 Krea 2 都是自然语言系（Krea 2 走英文、其余走中文）。 */
    private fun isNaturalLanguageModel(modelName: String): Boolean =
        SceneRuleKeys.Z_IMAGE_MARKERS.any { modelName.contains(it) } ||
            SceneRuleKeys.KREA2_MARKERS.any { modelName.contains(it) }

    private fun isTagModel(modelName: String): Boolean =
        SceneRuleKeys.TAG_MARKERS.any { modelName.contains(it) }
}
