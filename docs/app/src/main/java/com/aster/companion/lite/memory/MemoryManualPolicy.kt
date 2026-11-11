package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind

/**
 * 手动记忆（记忆分册 §6）：长按消息「让她记住」。
 *
 * 手动与自动共用同一张表、同一套 Top-K；手动默认重要度更高（PREFERENCE/PLAN 视为 3）。
 */
object MemoryManualPolicy {

    /** 手动记忆的单条长度上限，与 L1 保持一致。 */
    const val MAX_FACT_CHARS = MemoryRollupPolicy.MAX_FACT_CHARS

    data class Draft(val kind: MemoryKind, val content: String)

    /**
     * 把一条聊天消息变成可以入 L2 的事实。
     * 去掉动作括号与多余空白；纯动作、纯图片占位或空消息不产生记忆。
     */
    fun fromMessage(messageBody: String): Draft? {
        val cleaned = sanitize(messageBody)
        if (cleaned.length < MIN_FACT_CHARS) return null
        return Draft(kind = MemoryKind.PREFERENCE, content = cleaned.take(MAX_FACT_CHARS))
    }

    fun sanitize(text: String): String = text
        .replace(ACTION_PAREN, " ")
        .replace(WHITESPACE, " ")
        .trim()

    /** 「不再提及」的切换：只在用户点的时候改 blocked 标记，不改内容。 */
    fun toggledBlocked(current: Boolean): Boolean = !current

    /** 列表展示用的一行文案。 */
    fun describe(fact: LiteMemoryFact, characterName: String = ""): String {
        val prefix = FACT_KIND_LABEL[fact.kind] ?: ""
        val body = fact.content.trim()
        val suffix = if (fact.blockedFromMention) "（已设为不再提及）" else ""
        val who = characterName.trim()
        return buildString {
            if (who.isNotEmpty()) append("$who · ")
            if (prefix.isNotEmpty()) append("$prefix ")
            append(body)
            append(suffix)
        }
    }

    private const val MIN_FACT_CHARS = 2
    private val ACTION_PAREN = Regex("[（(][^）)]{0,30}[）)]")
    private val WHITESPACE = Regex("\\s+")
    private val FACT_KIND_LABEL = mapOf(
        MemoryKind.PREFERENCE to "偏好",
        MemoryKind.PLAN to "计划",
        MemoryKind.EVENT to "经历",
        MemoryKind.RELATIONSHIP to "关系"
    )
}
