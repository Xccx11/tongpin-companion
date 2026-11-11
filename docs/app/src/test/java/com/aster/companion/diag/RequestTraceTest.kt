package com.aster.companion.diag

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RequestTrace 的行为测试（A4 示范：测**行为**，不测文案）。
 */
class RequestTraceTest {

    @Before
    fun setUp() {
        RequestTrace.configureForTest(capacity = 4)
    }

    @After
    fun tearDown() {
        RequestTrace.clearForTest()
        RequestTrace.configureForTest(capacity = 64)
    }

    @Test
    fun `keeps only the newest entries when capacity is exceeded`() {
        repeat(6) { i -> RequestTrace.record("/v1/images/generations", durationMs = (i + 1) * 10L, ok = true) }

        val snapshot = RequestTrace.snapshot()
        assertEquals(4, snapshot.size)
        // 最旧的两条（10ms / 20ms）应已被丢弃，剩余 30/40/50/60
        assertEquals(listOf(30L, 40L, 50L, 60L), snapshot.map { it.durationMs })
    }

    @Test
    fun `summary counts failures and percentiles`() {
        RequestTrace.record("/a", 10, true)
        RequestTrace.record("/b", 20, false)
        RequestTrace.record("/c", 30, true)
        RequestTrace.record("/d", 100, true)

        val s = RequestTrace.summary()
        assertEquals(4, s.total)
        assertEquals(1, s.failures)
        assertEquals(40L, s.averageMs)          // (10+20+30+100)/4
        assertTrue("p95 应接近最大值", s.p95Ms >= 30L)
    }

    @Test
    fun `records negative duration as zero`() {
        RequestTrace.record("/x", -5, true)
        assertEquals(0L, RequestTrace.snapshot().single().durationMs)
    }

    @Test
    fun `empty trace reports zeros`() {
        val s = RequestTrace.summary()
        assertEquals(0, s.total)
        assertEquals(0L, s.averageMs)
        assertEquals(0L, s.p95Ms)
    }
}