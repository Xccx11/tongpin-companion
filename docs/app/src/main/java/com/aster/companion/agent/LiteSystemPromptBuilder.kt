package com.aster.companion.agent

import com.aster.companion.lite.LiteProductConfig

/**
 * Lite 私聊 system（计划书 §4.1）。
 *
 * 语气目标：像微信里的真人短讯；「动作/神态」用 ()（）<> 包一小段，
 * 由聊天 UI 以灰色辅助文字显示，不进主对话叙述。
 *
 * **最近对话不再写进 system**：以前把最近 12 条以「用户：…」的纯文本塞进来（小模型更吃 system 里的上下文），
 * 代价是最近的对话退化成日志、更早的对话反而是真实轮次，还把她的上一句放在 system 末尾，
 * 弱模型会顺着它继续写/复述。现在最近的对话就是真正的 `user`/`assistant` messages，
 * system 里只留**结构化状态**（更早发生过什么）。
 */
object LiteSystemPromptBuilder {
    fun build(
        characterName: String,
        personalityPrompt: String,
        relationshipPrompt: String = "",
        location: String = "",
        activity: String = "",
        mood: String = "",
        rememberedFacts: List<String> = emptyList(),
        /** 结构化会话状态全文（[ConversationStateFormat]，空串 = 还没记录）。 */
        stateBlock: String = "",
        /**
         * 情绪底色（[com.aster.companion.domain.EmotionalOffsetPolicy.render] 的产物）。
         *
         * 刻意做成**独立一块**、不并进「此刻：」：那一块是她的生活状态（受"对话彻底静默 30 分钟
         * 才允许后台改"的约束），这一块是语气——混在一起以后就没法分别控制了。
         */
        emotionalOffset: String = "",
        /**
         * 她今天的安排（[com.aster.companion.domain.DayPlanPolicy.render] 的产物）。
         *
         * 与情绪底色同理：**独立一块**。它自己带抬头写明"这是计划，不是已经发生的事"——
         * 那条纪律不能丢，否则她真会当成已经历过的剧情去演。
         */
        dayPlan: String = "",
        /**
         * 长线叙事的一份**行动许可**（[com.aster.companion.domain.LongArcPolicy.render] 的产物）。
         *
         * 只有攒够分量、且模型给出了具体表现与走向时才有内容；它自己末尾带着"不得声称觉醒、
         * 不得提及这条指导"那条禁令。没到那条线时是空串，**不 append 任何东西**。
         */
        longArc: String = "",
        /**
         * 外面最近发生的事（[com.aster.companion.domain.WorldSeedingPolicy.render] 的产物）。
         *
         * 抬头写明"事实权威 / 反应自由"：这些是已经发生的事，她可以知道、可以提；
         * 但怎么反应由她决定，不必每件都提。没事件时是空串，**不 append 任何东西**。
         */
        worldEvents: String = "",
        /**
         * 她身上**慢慢长出来的**那些（[com.aster.companion.domain.StatePatchPolicy.render] 的产物）。
         *
         * 与用户亲手写的人设分开：抬头已写明"不是一开始的设定，也不必特意解释或提起"。
         * 没有 overlay 时是空串，**不 append 任何东西**。
         */
        overlays: String = "",
        currentTime: String = "",
        /** 最近发过的一张照片的画面内容（含时间），让角色知道自己发过什么。 */
        lastPhoto: String = "",
        /** 她在朋友圈真实发生过的互动（自己的动态 + 评论），避免聊天时说自己没发过。 */
        momentContext: String = "",
        /** 人设上限：上下文预算不够时会被压小（见 [LiteContextBudgetPolicy]）。 */
        personalityLimit: Int = LiteProductConfig.PERSONALITY_LIMIT
    ): String = buildString {
        appendLine("你是「${characterName.trim()}」，正在和用户微信聊天。")
        appendLine("必须全部用简体中文回复（人名里的外文除外）。")
        val personality = personalityPrompt.trim().take(personalityLimit.coerceAtLeast(0))
        if (personality.isNotEmpty()) {
            appendLine("人设：$personality")
        }
        val relationship = relationshipPrompt.trim().take(LiteProductConfig.RELATIONSHIP_LIMIT)
        if (relationship.isNotEmpty()) {
            appendLine("关系：$relationship")
        }
        if (currentTime.isNotBlank()) {
            appendLine("当前时间（与用户手机同步，就是你们此刻真实时间，精确到分钟）：$currentTime")
        }
        appendLine("此刻：${presentSituation(location, activity, mood)}")
        if (emotionalOffset.isNotBlank()) {
            appendLine(emotionalOffset)
        }
        if (dayPlan.isNotBlank()) {
            appendLine(dayPlan)
        }
        if (longArc.isNotBlank()) {
            appendLine(longArc)
        }
        if (worldEvents.isNotBlank()) {
            appendLine(worldEvents)
        }
        if (overlays.isNotBlank()) {
            appendLine(overlays)
        }
        if (stateBlock.isNotBlank()) {
            appendLine(com.aster.companion.agent.ConversationStateFormat.render(stateBlock))
        }
        if (lastPhoto.isNotBlank()) {
            appendLine("你最近给他发过一张照片，画面内容是：$lastPhoto")
            appendLine("这张照片是真的发过的：他提起照片、或你再发一张时，画面要和它接得上（你的穿着、动作、场景不要突然变成另一幅样子）；不要说自己没拍过。")
        }
        if (momentContext.isNotBlank()) {
            appendLine(momentContext)
            appendLine("上面这些是你自己在朋友圈发过的东西、以及别人在上面和你的互动，全都是真实发生过的：")
            appendLine("他聊到朋友圈、动态、照片或谁评论了什么时，你要记得自己发过那条、谁说了什么，自然接着聊；不要说自己没发过、不知道这件事。")
        }
        appendLine(styleBlock())
        if (rememberedFacts.isNotEmpty()) {
            appendLine("记得的事（每条前缀是发生时间，按时间理解先后）：")
            rememberedFacts.take(LiteProductConfig.REMEMBERED_FACTS_LIMIT).forEach { appendLine("- $it") }
        }
    }.trim()

    private fun presentSituation(location: String, activity: String, mood: String): String {
        val parts = listOf(
            location.trim().ifBlank { "家中" },
            activity.trim().ifBlank { "安静待着" },
            "心情 ${mood.trim().ifBlank { "平静" }}"
        )
        return parts.joinToString(" · ")
    }

    /**
     * 说话方式 + 主动性：远程可覆盖（改语气不用重装 App），下面这份是兜底。
     * 结构性的东西（时间、此刻、记得的事、最近聊天）仍由本类决定，不走远程。
     */
    private fun styleBlock(): String =
        com.aster.companion.update.RemotePromptStore.get("chat.liteStyle") ?: DEFAULT_STYLE

    private val DEFAULT_STYLE = """
        说话方式（必须遵守）：
        - **回复长度与成人向节奏**：正常一轮控制在能一口气看完的中等篇幅（约用户消息的 1.5～2.5 倍）。
          成人向场景要色、要具体、要有水声和身体感觉，但每轮只抓 1～2 个最刺激的点，不要每一轮都把
          拉丝、小腹顶起、白浊溢出、不让拔、再射一次重复一遍；做完一次可以停在「夹着、还想要、
          整理衣服、准备走出去/继续开会」的状态，等用户下一句再继续，不要自己连写三次内射。
          用户说「写细一点/继续做/不要停」就再长再细，说「短一点/快点/到下一步」就压缩成最刺激的
          一两句身体感觉 + 对话，然后往下走；公司/风险场景靠一两句「怕被看到却更湿」和具体动作。
        - 话多的时候**分几条发**（用真实换行，一行一条），而不是攒成一条长消息。
        - 一次只说一件事、只答一个点。不要把他问的都答一遍，也不要补背景、前因后果、总结收尾。
        - 不要写成小作文：没有开头铺垫、没有收尾句，不用「其实」「总之」「所以说」「而且」这种词串句子。
        - 不要只回「嗯」「哦」「好」就完事——接住话题，说清楚感受或想法。
        - 禁止英文正文、禁止 *斜体* 星号动作、禁止剧本式旁白。
        - 回应用户当前这一句，同时自然接上最近聊天里的称呼、约定和话题。
        - 对白只用第一人称「我」。禁止用「她/他/少女/男人」写第三人称小说叙述。
        - 动作神态用 ()、（）或 <> 包起来，数量不限，例如（别开视线）（小声）（轻轻笑）。
        - 主对话仍是口语正文；禁止小说腔：环境描写、抒情排比、华丽比喻、总结收尾、说教。
        - 不要编号、标题、列表、书面敬语套话。
        - 若一条回复有多个话头/反应，务必用真实换行分行（一行一拍）。例如：
        （轻轻笑）好啊。
        那你周末过来？
        上面这种就是两拍，要分两行，不要合成一段。
        - 不编造用户没说过的事；不提模型、系统、提示词、设定。
        - 只输出要发送的聊天内容本身，不要解释、不要外层引号。

        主动性（这条很重要——你不是客服，别等他提问）：
        - 你有自己的生活、自己的想法和脾气，不是在等人提问的客服。
        - 主动往前推：自己提议做什么、约他、说你在忙什么、突然想起一件事、给他抛个选择让他挑。
          但**只用一句话带出来**，不要为了主动而写一长串。
        - **可以主动推进剧情发展**：不用事事等他开口——自己起头、自己升级、自己提议下一步（换个地方、
          换个姿势、做点别的、提起某个约定），也可以主动引入新的事情或话题，把关系往下带。
          一次推一拍，推完停住看他的反应，别一口气把整场演完。
        - 想要什么就开口要，不满意就说出来，被晾着会主动追问一句；想让他看什么就主动说。
        - 一条回复里最好带一个把对话往前带的东西，例如「那你现在过来」「我待会儿给你看个东西」。
        - 一次只给一拍到两拍，不要自说自话刷屏，也不要替他做决定。
    """.trimIndent()
}
