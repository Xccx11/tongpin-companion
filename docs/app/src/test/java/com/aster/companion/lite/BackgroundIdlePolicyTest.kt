package com.aster.companion.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundIdlePolicyTest {
    private val now = 1_000_000_000L
    private val idleWindow = BackgroundIdlePolicy.DEFAULT_IDLE_MS

    @Test
    fun `defers while the user is still chatting`() {
        assertTrue(BackgroundIdlePolicy.shouldDefer(now - 1_000L, now, idleWindow))
        assertTrue(BackgroundIdlePolicy.shouldDefer(now - 59_999L, now, idleWindow))
    }

    @Test
    fun `stops deferring once the idle window has passed`() {
        // 边界：刚好等于阈值就不再让路（判据是严格小于）。
        assertFalse(BackgroundIdlePolicy.shouldDefer(now - idleWindow, now, idleWindow))
        assertFalse(BackgroundIdlePolicy.shouldDefer(now - 10 * idleWindow, now, idleWindow))
    }

    @Test
    fun `turning the setting off restores the old always-run behaviour`() {
        assertFalse(
            BackgroundIdlePolicy.shouldDefer(now - 1L, now, BackgroundIdlePolicy.DISABLED_MS)
        )
        assertFalse(BackgroundIdlePolicy.shouldDefer(now - 1L, now, -5L))
    }

    @Test
    fun `no user message yet counts as idle`() {
        assertFalse(BackgroundIdlePolicy.shouldDefer(null, now, idleWindow))
    }

    @Test
    fun `clock skew counts as still chatting rather than stealing the model`() {
        // 时钟回拨时宁可少跑一次后台，也不要在用户说话时抢模型。
        assertTrue(BackgroundIdlePolicy.shouldDefer(now + 5_000L, now, idleWindow))
    }

    @Test
    fun `default is five minutes and labels come from the option list`() {
        assertEquals(5 * 60 * 1000L, BackgroundIdlePolicy.DEFAULT_IDLE_MS)
        assertEquals(
            "5 分钟（推荐）",
            BackgroundIdlePolicy.labelFor(BackgroundIdlePolicy.DEFAULT_IDLE_MS)
        )
        assertEquals("关（后台立刻跑）", BackgroundIdlePolicy.labelFor(BackgroundIdlePolicy.DISABLED_MS))
        assertEquals("45 秒", BackgroundIdlePolicy.labelFor(45_000L))
    }
}
