package com.aster.companion.agent

import com.aster.companion.lite.LiteProductConfig

/**
 * Scene 流水线的 prompt 组装。
 *
 * 输出形态由 [format] 决定：
 * - [ScenePromptFormat.ENGLISH_TAGS]：SDXL 系（wai / illustrious）的英文 tag 串；
 * - [ScenePromptFormat.NATURAL_LANGUAGE]：z-image / lumina 系的中文自然语言描述，
 *   按官方顺序 [主体]+[动作/姿势]+[环境/场景]+[风格/媒介]+[技术细节]+[构图] 组织。
 *
 * 人物外貌锚点由 App 原样拼进 renderer prompt，**不由本流水线改写或复述**。
 *
 * 角色自己发照片时（[Briefing.viewpoint] = true）额外要求：这张照片要**以她的视角**写出来——
 * 她此刻在哪、穿着什么、正在做什么、故意想让你看什么，而不是复述聊过什么。
 */
object ScenePromptBuilder {

    data class Briefing(
        val characterName: String,
        /**
         * 她的性格设定。**穿搭、动作、神态都要由这个推出来**——
         * 没有它，模型只能靠长相硬猜，就会出现"随便抓一件衣服"。
         */
        val personality: String = "",
        val appearancePrompt: String,
        val recentMessages: List<String>,
        val intent: String,
        val currentSituation: String = "",
        /** true：这是角色自己此刻拍给用户的一张照片，要按她的视角写。 */
        val viewpoint: Boolean = false,
        /** 她自己已经决定要拍的画面（中文）。非空时本步只负责把她的决定转成提示词。 */
        val decidedShot: String? = null,
        /** 她自己给出的「为什么想让你看」（中文）。 */
        val decidedWhy: String? = null,
        /** 她自己说的拍法，决定画面视角；默认前置自拍。 */
        val shotType: CharacterPhotoShotPolicy.PhotoShotType = CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER,
        /**
         * 她的长相要点（已剔掉当刻内容的锚点）。
         *
         * 交给模型写「主体」用：允许它按这张照片重新组织措辞，而不是原样粘贴，
         * 但必须还是同一个人、关键特征不能丢。
         */
        val appearanceAnchor: String = "",
        /**
         * 她上一张照片的最终画面（上次真正送去出图的提示词）。
         * 非空时要求这一张的穿着/所在场景与上一张接得上，避免每张都换一身衣服。
         */
        val lastScene: String = ""
    )

    /** Scene 专用 system：只补画面细节，不加风格质量词、不新增画面元素。 */
    fun system(
        characterName: String,
        savedRule: String = ImagePromptDebugConfig.DEFAULT_RULE,
        format: ScenePromptFormat = ScenePromptFormat.ENGLISH_TAGS
    ): String = buildString {
        // 给模型下指令的**文字**：自然语言系（z-image / Krea 2）用中文那份规则，英文 tag 系用英文那份。
        // 曾经 Krea 2 被判成英文形态、只能拿英文规则；现在它走 CHINESE_NATURAL，用中文规则。
        val fallback = if (format.wantsChineseInstructions) {
            ImagePromptDebugConfig.DEFAULT_Z_IMAGE_RULE
        } else {
            ImagePromptDebugConfig.DEFAULT_RULE
        }
        appendLine(savedRule.trim().ifBlank { fallback })
        appendLine(
            if (format.wantsChineseInstructions) {
                "画面里必须是她本人（不是别人）：用简报里给的长相要点来写主体，" +
                    "可以按这张照片重新组织措辞，但不能换成别人、不能漏掉关键特征。" +
                    "**整段不要写她的名字**——写实模型不认识人名，名字只会干扰画面。"
            } else {
                "The shot must show THIS character: build the subject from the appearance anchor in the brief. " +
                    "Reorder freely for this shot, but never swap the person or drop traits. " +
                    "Do NOT write her name anywhere — the renderer does not know names and they only pollute the image."
            }
        )
    }.trim()

    /** Scene 专用 user 简报：只给场景上下文，不把外貌交给模型重写。 */
    fun user(
        briefing: Briefing,
        wantReason: Boolean = false,
        format: ScenePromptFormat = ScenePromptFormat.ENGLISH_TAGS
    ): String = buildString {
        // 【语言】给模型下指令的**文字**用哪种语言：自然语言系（z-image / Krea 2）用中文，英文 tag 系用英文。
        // 注意这和上面的 fallback 是同一个判断，别只改一边——Krea 2 曾经就在这儿被发英文指令。
        val chineseInstruction = format.wantsChineseInstructions
        // 【形态】输出是句子还是 tag 串 / 要不要带某一段——这与上面的语言判断是两回事，不能合并。
        val natural = format.isNaturalLanguage
        // 长相要点**不放在这里**：它挪到简报最末尾（见本函数结尾）——模型对紧邻"开始生成"的内容
        // 最敏感，放在开头会被后面"此刻在哪 / 她的决定 / 上一张画面"几段挤掉注意力，导致整段漏写长相。
        // 【她的长相】第 1 轮就要拿到，这是草稿的输入依据之一（用户定义的三步流水线：
        // 第 1 轮 = 上下文 + 角色需求 + 角色外貌）。**细化轮已停用（一轮出图），所以这一轮就要写全**。
        // 用 appearanceAnchor（转写后的长相描述）而不是原文：9/13 那批出图提示词里的
        // `slim narrow eyes, soft flat facial features, a small nose` 就是转写加上去的；
        // 直接拼原文的话，用户写的中文档案会原样进英文提示词。转写失败时 transcribeAppearance
        // 已经把 anchor 退回成原文，所以这里不必再兜一次。
        val appearance = briefing.appearanceAnchor.ifBlank { briefing.appearancePrompt }
        if (appearance.isNotBlank()) {
            appendLine(
                if (chineseInstruction) {
                    "她的固定长相（后面每一步都必须以这份为准，不许改、不许美化、不许换人）：" +
                        appearance.trim()
                } else {
                    "Her fixed appearance (every later step must follow this exactly - never re-invent it, " +
                        "never beautify it, never turn her into someone else): " + appearance.trim()
                }
            )
        }
        // 【她是谁】穿搭 / 动作 / 神态的推理依据。放在最前面，让模型先建立"这是个什么人"，
        // 后面所有具体描写都从这个人身上长出来，而不是从模板里抓。
        if (briefing.personality.isNotBlank()) {
            appendLine(
                if (chineseInstruction) {
                    "她这个人：${briefing.personality.trim()}" +
                        "**她此刻穿什么、怎么穿、什么动作神情，都要从她这个人和她此刻的场合推出来**——" +
                        "先想她是什么性格、什么年龄、过着什么日子、现在在哪做什么、几点、冷不冷，" +
                        "再问自己「她这样的人，这个场合会怎么穿、怎么动」，然后照着写。" +
                        "**穿搭要成套**：上装、下装、内衣鞋袜、配饰该有什么就写什么，风格要和她对得上。"
                } else {
                    "Who she is: ${briefing.personality.trim()} " +
                        "**What she wears, how she wears it, and how she moves must be derived from this person and this situation** - " +
                        "first work out her character, her age, how she lives, where she is, what she is doing, what time it is, " +
                        "then ask what someone like her would wear and how she would move here, and write that. " +
                        "**The outfit must be a complete set** - top, bottom, underwear, footwear, accessories as they apply, in her own style."
                }
            )
        }
        // 连拍一致性：她上一张的画面（穿着/场景）要和这一张接得上，不要每张都换一身衣服。
        // 注意：**穿着默认沿用上一张**（换地方不等于换衣服）——只有明确换了衣服、或那套在当下场合
        // 根本不可能时才重新决定。原先写的是「只在同一个地方时才延续」，等于鼓励每换场景就换一身。
        if (briefing.lastScene.isNotBlank()) {
            appendLine(
                if (chineseInstruction) {
                    "她上一张照片的画面是：${briefing.lastScene.trim()}。" +
                        "**场景、动作、构图必须按这一次的要求来，不许照搬上一张**。" +
                        "**但穿着要接着上一张那套穿**——除非上下文明确说她换了衣服（洗完澡、回家换上睡衣、" +
                        "出门前换了外套），否则款式、颜色、材质都保持一致；**换了地方不等于换了衣服**，" +
                        "她出门前穿什么，出门以后还是什么，只允许「外面加了一件外套」这类延续性的变化。" +
                        "只有在没有上一张、或她确实换了衣服、或那套在当下场合根本不可能（睡衣不能穿去商场）时，" +
                        "才重新决定穿什么。在外面（任何有别人的公共场合）绝不能把家里的睡衣或内衣带出门。"
                } else {
                    "Her previous photo showed: ${briefing.lastScene.trim()}. " +
                        "**The scene, the action and the framing MUST follow this request — do not copy the previous shot.** " +
                        "**But KEEP THE SAME OUTFIT as the previous photo** — unless the context clearly says she changed " +
                        "clothes (just showered, came home and put on pyjamas, changed before going out), keep the same " +
                        "garment, colour and material. **Moving to a different place is NOT changing clothes**: what she " +
                        "put on before leaving is what she is still wearing, and a jacket thrown over it is fine while a " +
                        "completely new outfit is not. Only pick a fresh outfit when there is no previous photo, when she " +
                        "explicitly changed, or when the old one is impossible in this setting (pyjamas cannot go to a mall). " +
                        "Never carry pyjamas or underwear out of the home (any public place with other people around)."
                }
            )
        }
        if (briefing.currentSituation.isNotBlank()) {
            // 硬约束，不是参考：场景必须落在她此刻所在的地方，否则会出现"她刚说在图书馆、画面却在商场"。
            appendLine(
                if (chineseInstruction) {
                    "她此刻正在：${briefing.currentSituation.trim()}。" +
                        "**画面的场景必须是这里、正在做这件事，不许换成别的场所**——" +
                        "她说在图书馆，画面就不能在商场；也不许把地点挪到别处。"
                } else {
                    "Right now she is: ${briefing.currentSituation.trim()}. " +
                        "The scene MUST be exactly this place and this activity — never a different location " +
                        "(if she says she is in the library, the image must not be in a mall). Do not relocate her."
                }
            )
        }
        val recent = briefing.recentMessages
            .filter { it.isNotBlank() }
            .takeLast(LiteProductConfig.SCENE_RECENT_MESSAGES)
        val decided = briefing.decidedShot?.trim().orEmpty()
        when {
            // 她自己已经决定了拍什么：本步只把她的决定写成画面描述，不许改成别的画面。
            decided.isNotEmpty() -> {
                appendLine("She has already decided what to photograph herself.")
                appendLine("Her decision (Chinese, her own point of view): SHOT: $decided")
                briefing.decidedWhy?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { appendLine("Her reason (Chinese): $it") }
                // 她的决定之外，她对用户说的原话里可能还有具体要求，不能丢。
                briefing.intent.trim().takeIf { it.isNotEmpty() && it != decided }?.let {
                    appendLine(
                        if (chineseInstruction) {
                            "她对用户说的原话（这里提到的画面要求也必须满足，和上面的决定冲突时以原话为准）：$it"
                        } else {
                            "What she actually said to the user (any shot requirement in it is binding too; if it conflicts with the decision above, what she said wins): $it"
                        }
                    )
                }
                appendLine(
                    if (chineseInstruction) {
                        "把她的决定写成渲染用的画面描述，保留她选的每一个元素，不要换成别的画面。"
                    } else {
                        "Translate HER decision into the renderer tag list. Keep every element she chose; do not replace her choice with something else."
                    }
                )
                appendLine("Only add the action, pose, expression, setting and camera angle needed to render that exact shot — fill in the surroundings she is actually in.")
                appendLine(framing(briefing.shotType, format))
            }
            briefing.viewpoint -> {
                appendLine("Write the photo ${briefing.characterName.trim()} is taking and sending right now, from her own point of view.")
                appendLine("Base every detail on her right now: where she is, what she is wearing, what she is doing this minute, her expression and mood.")
                appendLine("Show only what is inside the frame she is sending. Do not narrate the conversation, the relationship, or the reason.")
                appendLine(framing(briefing.shotType, format))
            }
            else -> Unit
        }
        // **表情必须写足、写具体**（用户要求，2026-10-11）。
        //
        // 以前"表情"只在上面以**一个词**（expression）的形式出现过，于是模型交回
        // `eyes half-lidded and glossy`、`expression tense` 这种**一眼看不出来**的含蓄说法——
        // 渲染出来脸上几乎没有表情（用户现象：表情不明显）。
        // 这里放在各分支**之外**，所以三种形态都吃到这条规则。
        // 用户结论（2026-10-11）：**渲染器只画"大而明确"的东西**，所以提示词里的表情必须
        // 写到**浮夸、大幅度**才画得出来；含蓄的写法等于没写。
        // 上一版规则里我自己给了 `faint`（微弱）这个档位 —— 那是错的，等于在鼓励含蓄
        //（实测出图提示词里就出现了 `a faint tired softness`）。现在把它列进禁用词。
        appendLine(
            "EXPRESSION RULE: her face is the main subject of the picture. " +
                "The renderer only draws what is BIG and explicit, so write the expression at MAXIMUM amplitude — " +
                "exaggerate it deliberately, the way a caricaturist would. " +
                "Describe the physical deformation of the face, not a mood: " +
                "mouth wide open, teeth bared, jaw dropped, tongue out, lips pulled back, lower lip bitten hard, " +
                "eyebrows raised high or knitted hard together, eyes squeezed shut or wide and staring, " +
                "nose scrunched, tears streaming down her face, cheeks flushed and shining, " +
                "head thrown back or buried in her hands. " +
                "Pick the strongest three or four of these and state them as plain facts about this exact picture. " +
                "Allowed intensity words: strong / extreme / overwhelming / uncontrollable. " +
                "BANNED as failures (they render as a blank doll face): faint, slight, subtle, soft, barely, " +
                "a hint of, half-smile, expression tense, eyes half-lidded, a faint tired softness. " +
                "If the moment carries any emotion at all, write it BIG: aroused, embarrassed, crying, angry, " +
                "startled, mocking or exhausted — her face must show it from across the room. " +
                "When in doubt, exaggerate more, never less."
        )
        if (recent.isNotEmpty()) {
            appendLine(
                if (chineseInstruction) {
                    "最近对话——这里面他明确说了要拍什么、拍哪里、穿什么，就必须出现在画面里；其余只当背景："
                } else {
                    "Recent conversation — any explicit request in here (what to shoot, where, wearing what) is binding and must appear in the picture; the rest is only background:"
                }
            )
            recent.forEach { appendLine(it.trim().replace(WHITESPACE, " ").take(LiteProductConfig.SCENE_BRIEF_CHARS)) }
        }
        when {
            decided.isNotEmpty() -> Unit
            briefing.viewpoint -> {
                appendLine("What she just said, in her own words: ${briefing.intent.trim().ifBlank { "想给你看看我现在的样子" }}")
                appendLine("Her explicit request in that line overrides the recent conversation whenever they disagree.")
            }
            else -> appendLine("Photo request: ${briefing.intent.trim().ifBlank { "角色此刻想给用户看的一张照片" }}")
        }
        appendLine(
            if (natural) {
                // 远程优先：网关改了这句，出图立刻跟着变；Krea 2 用英文那一份。
                com.aster.companion.update.RemotePromptStore.get(
                    if (format == ScenePromptFormat.ENGLISH_NATURAL ||
                        format == ScenePromptFormat.CHINESE_NATURAL
                    ) {
                        "image.sceneBuildTail.krea2"
                    } else {
                        "image.sceneBuildTail"
                    }
                )
                    ?: run {
                        // 远程 key 缺席时的兜底文案也要分语言：Krea 2 拿到中文兜底会直接跑偏。
                        if (chineseInstruction) {
                            "这一轮只写「这张图要什么」：她在做什么、在哪里、此刻穿什么（什么状态）、想让人看到什么，2-4 句中文自然语言。" +
                                "**不要写她固定的长相**（脸型、眼睛、头发、皮肤、身材）——那是固定的，下一轮才铺开；**穿着要写**。" +
                                "不要输出英文 tag 串。"
                        } else {
                            "This round writes only WHAT the picture is about: what she is doing, where she is, and what she wants it to convey. " +
                                "Write 2-4 sentences of plain English. " +
                                "Do NOT describe her fixed looks (face, hair, eyes, skin, build) - those are fixed and are filled in at the next step. " +
                                "DO say what she is wearing right now and its state - clothing belongs to this moment. " +
                                "Do not output a comma-separated tag list."
                        }
                    }
            } else {
                "Return only the scene positive prompt text (no identity restatement). Break the outfit into concrete pieces " +
                    "(garment, colour, material, how it sits right now) and break the action down to individual limbs; never summarise."
            }
        )
        if (wantReason) {
            appendLine("Then output one more line, exactly in this shape, in Chinese, at most 20 characters:")
            appendLine("WHY: 她此刻为什么想让对方看到这一张")
            appendLine("The WHY line is for the user to read only. Never put it into the prompt text.")
        }
        // 【长相不在这里要求模型写】曾经为了补「细化轮停跑」的缺口，这里要求模型"这一轮必须写长相"。
        // 但外貌**已经由代码原文拼在整段最前面**（[AppearanceAnchor] 存在的理由就是模型转写会丢细节），
        // 于是等于要求模型把同一份长相再复述一遍 —— 实测最终提示词里约四成是逐字重复的长相文本，
        // 而且两段各带各的构图（锚点写 standing、场景写 lying on a bed），互相打架。
        // 现在这条要求撤掉：**长相由代码给，模型只写画面**（网关规则侧同步改成"不要复述长相"）。
    }.trim()

    /**
     * 视角写法：画面必须是**人的眼睛看到的**——她的眼睛（第一人称），或者旁边某个人的眼睛（第三人称在看）。
     *
     * 不是「相机拍出来的那种」：画面里不能有手机/相机/镜头/取景框，也不能出现"在拍照"这件事。
     * 实测连 `selfie` 这个 tag 都会把举手机的手臂画进来，所以这里刻意不用自拍类词。
     */
    private fun framing(
        type: CharacterPhotoShotPolicy.PhotoShotType,
        format: ScenePromptFormat
    ): String = when (format) {
        ScenePromptFormat.NATURAL_LANGUAGE,
        ScenePromptFormat.CHINESE_NATURAL -> when (type) {
            CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER ->
                "视角：这是**人的眼睛**看到的画面——要么就是她自己眼睛里的样子，要么像是旁边有个人正看着她。" +
                    "画面里绝对不能出现相机、手机、镜头、取景框、镜子，也不要写成「有人在举着相机拍她」。"
            CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_SCENE ->
                "视角：**人的眼睛**看到的画面——就是她眼前这一刻的样子，或者像是旁边的人看到的。" +
                    "画面里绝对不能出现相机、手机、镜头、取景框，也不要变成她的脸部特写。"
        }
        else -> when (type) {
            CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_HER ->
                "Viewpoint: a HUMAN EYE's view - either her own eyes, or as if someone standing right there is looking at her. " +
                    "Use tags like: candid, natural pose, natural light, gaze on what she is doing rather than at the camera (looking at viewer only when she is deliberately taking a selfie for him). " +
                    "Do NOT use selfie, holding phone, holding camera, camera, viewfinder, mirror: the picture must not " +
                    "contain a phone, a camera, a lens or any viewfinder, and must not depict the act of photographing."
            CharacterPhotoShotPolicy.PhotoShotType.EYES_ON_SCENE ->
                "Viewpoint: a HUMAN EYE's view - what her eyes take in right now, or what someone beside her sees. " +
                    "Use tags like: pov, first person view. " +
                    "The picture must not contain a phone, a camera, a lens or any viewfinder, " +
                    "and must not turn into a close portrait of her face."
        }
    }

    private val WHITESPACE = Regex("\\s+")

    /** 递给模型的长相要点上限：留得宽，让模型看到认人需要的关键特征再自行压缩。 */
    private const val APPEARANCE_ANCHOR_LIMIT = 1200
}
