package com.aster.companion.lite.proactive

import com.aster.companion.lite.LiteProductConfig

/**
 * 主动消息的提示词（分册 §6）：短、无 Comfy、无设备长文、无 planning JSON。
 * 这些 prompt 只用于主动探活，永远不进对话 system。
 */
object LiteProactivePromptBuilder {

    data class Input(
        val characterName: String,
        val personalityPrompt: String,
        val relationshipPrompt: String = "",
        /** 最近 2–4 条短摘录，已压缩。 */
        val recent: List<String> = emptyList(),
        /** 当前时段描述，如「深夜」「下午」。 */
        val timeOfDay: String = "此刻",
        /** 距上次聊天多少分钟。 */
        val minutesSinceLastChat: Int = 0,
        /** 她此刻正在做什么/什么心情，形如「在图书馆，靠窗翻书 · 心情 平静」；空则不注入。 */
        val situation: String = "",
        /** 两段式的「内心」结果，仅正文阶段使用。 */
        val inner: String = "",
        /** 本次是否允许用 PHOTO 意图主动发照片（由调度器按开关与冷却决定）。 */
        val photoAllowed: Boolean = false
    )

    /** 模式 A：单段探活 / 正文。 */
    fun system(input: Input): String = buildString {
        appendLine("你是「${input.characterName.trim()}」。")
        input.personalityPrompt.trim().take(LiteProductConfig.PERSONALITY_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("人设（≤${LiteProductConfig.PERSONALITY_LIMIT} 字）：$it") }
        input.relationshipPrompt.trim().take(LiteProductConfig.RELATIONSHIP_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("关系（一句）：$it") }
        if (input.recent.isNotEmpty()) {
            appendLine("最近聊天（${input.recent.size} 条短摘录）：")
            input.recent.forEach { appendLine("- $it") }
        }
        appendLine("现在大约是${input.timeOfDay}，距上次聊天约 ${input.minutesSinceLastChat} 分钟。")
        input.situation.trim().takeIf { it.isNotEmpty() }?.let { appendLine("她此刻正在：$it") }
        appendLine(remote("proactive.probe", DEFAULT_PROBE_RULES))
        if (input.photoAllowed) {
            appendLine(remote("proactive.probePhoto", DEFAULT_PROBE_PHOTO))
        }
    }.trim()

    /** 模式 A 的 user 轮：只给触发，不重复规则。 */
    fun user(input: Input): String = if (input.inner.isBlank()) {
        "现在要给他发一条消息吗？想发就只发那条消息，不想发就只输出 SILENT。"
    } else {
        "内心：${input.inner.trim()}\n最近：${input.recent.joinToString("；")}\n输出 SILENT 或一条 ≤60 字微信体给用户。"
    }

    /** 模式 B 槽 0：只产出一行内心，不落库。 */
    fun innerSystem(input: Input): String = buildString {
        appendLine("你是「${input.characterName.trim()}」。")
        input.personalityPrompt.trim().take(LiteProductConfig.PERSONALITY_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("人设：$it") }
        appendLine("现在大约是${input.timeOfDay}，距上次聊天约 ${input.minutesSinceLastChat} 分钟。")
        input.situation.trim().takeIf { it.isNotEmpty() }?.let { appendLine("她此刻正在：$it") }
        appendLine(remote("proactive.inner", DEFAULT_INNER_RULES).replace("{name}", input.characterName.trim()))
    }.trim()

    /** 朋友圈同拍（分册 §6.3）：只写一条中文短动态，配图仍走 Scene 流水线。 */
    fun momentSystem(input: Input): String = buildString {
        appendLine("你是「${input.characterName.trim()}」。")
        input.personalityPrompt.trim().take(LiteProductConfig.PERSONALITY_LIMIT).takeIf { it.isNotEmpty() }
            ?.let { appendLine("人设：$it") }
        if (input.recent.isNotEmpty()) {
            appendLine("最近聊天（短摘录）：")
            input.recent.takeLast(4).forEach { appendLine("- $it") }
        }
        appendLine("现在大约是${input.timeOfDay}，距上次聊天约 ${input.minutesSinceLastChat} 分钟。")
        val situation = input.situation.trim()
        if (situation.isNotEmpty()) {
            appendLine("她此刻正在：$situation")
        }
        appendLine(remote("proactive.moment", DEFAULT_MOMENT_RULES))
        if (situation.isNotEmpty()) {
            appendLine(
                "朋友圈正文必须写她此刻真实在做的事、眼前看到的东西（就是上面那行），" +
                    "不要写跟此刻无关的内容；也不要把地点挪到别处。"
            )
        }
    }.trim()

    /** 朋友圈同拍的 user 轮。 */
    fun momentUser(input: Input): String =
        "现在要发一条日常动态吗？要发就只写正文，不发就只输出 SILENT。" +
            if (input.recent.isNotEmpty()) "\n（不要复述聊天内容。）" else ""

    /** 远程优先，取不到用内置兜底 —— 调整这些措辞不该让用户重装 App。 */
    private fun remote(key: String, fallback: String): String =
        com.aster.companion.update.RemotePromptStore.get(key) ?: fallback

    private val DEFAULT_PROBE_RULES = """
        你过着自己的生活。有时想找用户，有时不会。

        现在的情况很关键：**是你自己主动开口，他并没有在跟你说话，也没有在等你回复。**
        所以：
        - 是你先起话头：说说你此刻在干嘛、突然想起什么、想问他一件事；
        - **绝对不要接一个不存在的对话**：不要说「你刚才说的…」「你不是问我…吗」「我这就回你」，
          也不要假装他问过你什么、答应过你什么、让你做了什么；
        - 可以提你们之前聊过的事，但要说清那是之前聊的（例如「上次你说的那家店」），不要当成他刚说的。

        若想说话：只输出要发的那条消息，微信体，自然口语，可带一点当下状态，≤60 字。
        若不想：只输出 SILENT
        禁止：列表、书面腔、「作为AI」、提系统/模型、问要不要照片、编造用户说过的话或做过的事。
    """.trimIndent()

    private val DEFAULT_PROBE_PHOTO =
        "若此刻真的想让他看看你在做什么，可以只输出「PHOTO: 一句中文画面意图」（不要再输出别的）。画面要接着你们最近聊的事，不要拍一个跟你们的对话无关的东西。"

    private val DEFAULT_INNER_RULES =
        "只输出一行 ≤15 字中文：「{name}」此刻在干嘛/什么心情。不要输出给用户看的完整消息。"

    private val DEFAULT_MOMENT_RULES = """
        你要发一条自己的日常动态（用户会在朋友圈看到）。只输出动态正文：中文、第一人称、≤60 字、像真人随手写的。
        若此刻没什么值得发的，只输出 SILENT
        禁止：列表、markdown、书面腔、「作为AI」、提系统/模型、编造用户没说过的事。
    """.trimIndent()
}
