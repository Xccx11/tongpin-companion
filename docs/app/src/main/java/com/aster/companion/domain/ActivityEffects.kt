package com.aster.companion.domain

/**
 * v2.1 共同活动效果计算（代码负责数值，AI 不参与）。
 * 活动完成 → 关系变化（项目书 §十一：activity_quality * emotional_value * personality_match）+ 状态影响。
 */
data class ActivityEffect(
    val affectionDelta: Int,
    val trustDelta: Int,
    val understandingDelta: Int,
    val conflictDelta: Int,
    val stressDelta: Int,
    val energyDelta: Int,
    val lonelinessDelta: Int
)

object ActivityEffects {

    /** 活动质量基础值（按类型）。 */
    fun quality(action: String): Int = when (action) {
        "watch_tv" -> 3
        "stroll" -> 3
        "game" -> 3
        "comfort" -> 3
        "chat" -> 2
        "meal_remind" -> 2
        "help" -> 2
        "care" -> 2
        "vent" -> 2
        else -> 1
    }

    /**
     * 计算活动效果。
     * @param action 活动类型
     * @param moodPositive 参与方当前心情是否积极（开心/满足等 → 情绪加成）
     */
    fun calculate(action: String, moodPositive: Boolean = false): ActivityEffect {
        val q = quality(action)
        // emotional_value：心情积极则 1.2 倍，否则 1.0；personality_match 简化 1.0
        val emotional = if (moodPositive) 1.2 else 1.0
        val base = (q * emotional).toInt()
        return when (action) {
            // 娱乐：放松（压力降）但消耗（能量微降）
            "watch_tv", "stroll", "game" -> ActivityEffect(
                affectionDelta = base, trustDelta = base - 1, understandingDelta = base - 1,
                conflictDelta = 0, stressDelta = -10, energyDelta = -3, lonelinessDelta = -15
            )
            // 情绪：释放压力
            "chat", "vent", "comfort" -> ActivityEffect(
                affectionDelta = base, trustDelta = base, understandingDelta = base + 1,
                conflictDelta = 0, stressDelta = -15, energyDelta = -2, lonelinessDelta = -20
            )
            // 家庭：补充能量
            "meal_remind", "help", "care" -> ActivityEffect(
                affectionDelta = base, trustDelta = base, understandingDelta = base,
                conflictDelta = 0, stressDelta = -5, energyDelta = +8, lonelinessDelta = -10
            )
            else -> ActivityEffect(0, 0, 0, 0, 0, 0, 0)
        }
    }
}
