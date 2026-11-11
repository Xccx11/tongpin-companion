package com.aster.companion.agent

import com.aster.companion.data.CharacterLivingStateEntity

/**
 * 睡眠状态机：睡与醒完全由世界推演（模型）决定，这里只负责推导「计划醒来时间」。
 *
 * 不再读角色作息表 —— 作息写死在表里会跟模型推演脱节：到点了模型还在安排清醒活动，
 * 没到点模型想让她睡也会被否掉。她什么时候睡、睡多久，都听模型。
 */
object SleepStateMachine {
    /** 模型说了 SLEEP 但没给时长时的兜底睡眠时长（8 小时）。 */
    const val DEFAULT_SLEEP_MS = 8 * 60 * 60 * 1000L

    /** 兜底唤醒的最小睡眠时长（1 分钟），防止算出已经过去的时刻。 */
    const val MIN_SLEEP_MS = 60_000L

    /**
     * 计划醒来时间：
     * - 已有未到期的 sleepUntil → 沿用（下一轮推演会重新判断她该不该醒）；
     * - 否则按模型给的 sleepMinutes，没给就用兜底时长。
     */
    fun nextSleepUntil(state: CharacterLivingStateEntity?, now: Long, sleepMinutes: Long? = null): Long {
        val existing = state?.sleepUntil
        if (existing != null && existing > now) return existing
        val ms = sleepMinutes?.takeIf { it > 0 }?.let { it * 60_000L } ?: DEFAULT_SLEEP_MS
        return (now + ms).coerceAtLeast(now + MIN_SLEEP_MS)
    }

    /** 此刻是否在睡：只认模型推演落库的 sleepUntil。 */
    fun isSleeping(now: Long, sleepUntil: Long?): Boolean = sleepUntil != null && now < sleepUntil
}
