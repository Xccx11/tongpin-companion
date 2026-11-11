package com.aster.companion.domain

/**
 * v2.1 行为库：候选行为必须来自这里（AI 不能创造规则）。
 * 每个行为按需求类型分组，DeepSeek 只能从候选中选择。
 */
data class CandidateAction(
    val id: String,
    val needType: String,        // social / entertainment / emotional / family
    val label: String,           // 行为名（群聊/状态里展示）
    val targetType: String       // character=角色 / user=用户
)

object ActionLibrary {
    /** 娱乐行为 */
    val ENTERTAINMENT = listOf(
        CandidateAction("watch_tv", "entertainment", "一起看电视", "character"),
        CandidateAction("game", "entertainment", "一起打游戏", "character"),
        CandidateAction("stroll", "entertainment", "一起散步", "character")
    )

    /** 情绪行为 */
    val EMOTIONAL = listOf(
        CandidateAction("chat", "emotional", "聊聊天", "character"),
        CandidateAction("vent", "emotional", "说说心里话", "character"),
        CandidateAction("comfort", "emotional", "安慰一下", "character")
    )

    /** 家庭行为 */
    val FAMILY = listOf(
        CandidateAction("meal_remind", "family", "提醒吃饭", "character"),
        CandidateAction("help", "family", "帮个忙", "character"),
        CandidateAction("care", "family", "关心一下", "character")
    )

    /** 社交行为 */
    val SOCIAL = listOf(
        CandidateAction("chat", "social", "聊聊天", "character"),
        CandidateAction("watch_tv", "social", "一起看电视", "character"),
        CandidateAction("stroll", "social", "一起散步", "character")
    )

    /** 按需求类型取候选行为。 */
    fun candidatesFor(needType: String): List<CandidateAction> = when (needType) {
        "entertainment" -> ENTERTAINMENT
        "emotional" -> EMOTIONAL
        "family" -> FAMILY
        "social" -> SOCIAL
        else -> emptyList()
    }

    /** 所有候选（去重）。 */
    val ALL: List<CandidateAction> =
        (ENTERTAINMENT + EMOTIONAL + FAMILY + SOCIAL).distinctBy { it.id to it.needType }
}
