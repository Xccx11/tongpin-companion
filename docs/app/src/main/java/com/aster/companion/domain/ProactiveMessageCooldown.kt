package com.aster.companion.domain

/** Prevents queued sensing events from turning into a burst of companion messages. */
object ProactiveMessageCooldown {
    // 自主化：主动消息冷却缩短到 90 秒，角色更愿意自己开口。
    const val INTERVAL_MS = 90 * 1000L

    fun maySend(now: Long, lastCompanionMessageAt: Long?, urgent: Boolean): Boolean =
        urgent || lastCompanionMessageAt == null || now - lastCompanionMessageAt >= INTERVAL_MS

    fun isUrgent(source: String, summary: String): Boolean =
        source.startsWith("notification:") && listOf("已送达", "放入驿站", "已入驿站", "快递柜").any(summary::contains)
}
