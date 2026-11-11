package com.aster.companion.domain

/**
 * 世界推演时长分类：决定角色「此刻状态」何时算完成、何时该变化。
 *
 * - INSTANT：瞬间动作，约 30 秒完成（如拿起手机、倒杯水）。
 * - TRANSITION：过渡动作，几分钟内完成（如从客厅走到卧室）。
 * - ONGOING：持续事，没有固定完成时间（如看书、散步），由下一次推演/事件驱动变化。
 * - SLEEP：睡眠，不按时长自动结束，何时醒由模型推演的 sleepUntil 决定。
 */
enum class DurationClass { INSTANT, TRANSITION, ONGOING, SLEEP }

object DurationPolicy {
    /** 瞬间动作的完成时长：30 秒。 */
    const val INSTANT_MS = 30_000L

    /** 过渡动作的完成时长上限：5 分钟。 */
    const val TRANSITION_MS = 5 * 60_000L

    /** 持续事最短持续时间：至少 30 分钟才认为「可能该变化」。 */
    const val ONGOING_MIN_MS = 30 * 60_000L

    /**
     * 该状态是否已达到可变化条件。
     * SLEEP 恒为 false：睡眠不按时长自动醒，何时醒由模型推演的 sleepUntil 决定。
     */
    fun isComplete(durationClass: DurationClass, startedAt: Long, now: Long): Boolean = when (durationClass) {
        DurationClass.INSTANT -> now - startedAt >= INSTANT_MS
        DurationClass.TRANSITION -> now - startedAt >= TRANSITION_MS
        DurationClass.ONGOING -> now - startedAt >= ONGOING_MIN_MS
        DurationClass.SLEEP -> false
    }

    /** 由已持续的时长反推最可能的时长分类（供推演判定使用）。 */
    fun classify(elapsedMs: Long): DurationClass = when {
        elapsedMs < INSTANT_MS -> DurationClass.INSTANT
        elapsedMs < TRANSITION_MS -> DurationClass.TRANSITION
        else -> DurationClass.ONGOING
    }
}
