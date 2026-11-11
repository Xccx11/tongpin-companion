package com.aster.companion.proactive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「他说了晚安」按下的主动联系不能是永久的。
 *
 * 回归背景：暂停只靠「我醒了 / 有空了 / 忙完了」这一串词才会解除。用户说完晚安、
 * 第二天只说一句「早」，她就再也不主动找他了 —— 看起来像功能坏了。
 */
class ProactivePauseExpiryTest {

    private val hour = 60 * 60 * 1000L
    private val now = 1_700_000_000_000L

    @Test
    fun `一夜过去就自动放开`() {
        assertFalse(ProactivePauseExpiry.isExpired(pausedAt = now - 7 * hour, now = now))
        assertTrue(ProactivePauseExpiry.isExpired(pausedAt = now - 8 * hour, now = now))
        assertTrue(ProactivePauseExpiry.isExpired(pausedAt = now - 20 * hour, now = now))
    }

    @Test
    fun `刚按下不久不会放开`() {
        assertFalse(ProactivePauseExpiry.isExpired(pausedAt = now - 60_000L, now = now))
        assertFalse(ProactivePauseExpiry.isExpired(pausedAt = now, now = now))
    }

    @Test
    fun `没有记录的老数据保持原行为`() {
        // 0 = 没有时间戳（升级前就存在的暂停记录）：不自动放开，等用户自己说「我醒了」。
        assertFalse(ProactivePauseExpiry.isExpired(pausedAt = 0L, now = now))
    }

    @Test
    fun `时长可以调`() {
        assertTrue(ProactivePauseExpiry.isExpired(pausedAt = now - 2 * hour, now = now, maxPauseMs = hour))
        assertFalse(ProactivePauseExpiry.isExpired(pausedAt = now - 30 * 60_000L, now = now, maxPauseMs = hour))
    }
}
