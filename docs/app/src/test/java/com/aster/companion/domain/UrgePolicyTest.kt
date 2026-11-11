package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主动性节律：密度 → 忍耐时间 → 加随机。
 *
 * 关键性质：**只数"有没有消息"的桶，不数条数**（连发十条不算更热）；热络时忍得短、
 * 冷下来忍得长；随机可控（给固定采样序列就能钉死结果）。
 */
class UrgePolicyTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    /** 给定采样序列的随机源（循环取用）。 */
    private fun sampler(vararg values: Double): UrgePolicy.Sampler {
        var index = 0
        return UrgePolicy.Sampler { values[index++ % values.size] }
    }

    /** 中位数采样：既不是极端、也不抖动。 */
    private fun neutral() = sampler(0.5, 0.5)

    // ---- 密度 ----

    @Test
    fun `没有消息时密度为零`() {
        assertEquals(0.0, UrgePolicy.densityOf(emptyList(), now), 1e-9)
        assertEquals(0.0, UrgePolicy.densityOf(listOf(now - 30 * minute), now), 1e-9)
    }

    @Test
    fun `窗口边界之外的消息不算`() {
        assertEquals(0.0, UrgePolicy.densityOf(listOf(now - UrgePolicy.WINDOW_MS), now), 1e-9)
        assertTrue(UrgePolicy.densityOf(listOf(now - UrgePolicy.WINDOW_MS + 1), now) > 0.0)
    }

    /** 时钟偏移（消息时间在未来）不该被算成"极度活跃"。 */
    @Test
    fun `未来时间的消息被忽略`() {
        assertEquals(0.0, UrgePolicy.densityOf(listOf(now + minute), now), 1e-9)
    }

    @Test
    fun `同一个桶里连发多条只算一个桶`() {
        // 用正好等于 now 的时刻，衰减项才是 1，密度就恰好是"一个桶"的份额。
        val one = UrgePolicy.densityOf(listOf(now), now)
        val ten = UrgePolicy.densityOf(List(10) { now }, now)

        assertEquals(one, ten, 1e-9)
        assertEquals(1.0 / UrgePolicy.SATURATION_BUCKETS, one, 1e-9)
    }

    @Test
    fun `桶数达到饱和后封顶到一`() {
        val sixBuckets = (0 until 6).map { now - it * UrgePolicy.BUCKET_MS }
        val twelveBuckets = (0 until 12).map { now - it * UrgePolicy.BUCKET_MS }

        assertEquals(1.0, UrgePolicy.densityOf(sixBuckets, now), 1e-9)
        assertEquals(1.0, UrgePolicy.densityOf(twelveBuckets, now), 1e-9)
    }

    @Test
    fun `同样三个桶，越久以前越不活跃`() {
        // 两组都留在 15 分钟窗口内（否则不是"衰减"而是"不算"）。
        val fresh = UrgePolicy.densityOf((0 until 3).map { now - it * UrgePolicy.BUCKET_MS }, now)
        val stale = UrgePolicy.densityOf((0 until 3).map { now - 5 * minute - it * UrgePolicy.BUCKET_MS }, now)

        assertTrue("$fresh 应当大于 $stale", fresh > stale)
        assertEquals(0.5, fresh, 1e-9)
        assertEquals(0.5 * Math.pow(0.5, 5.0 / 45.0), stale, 1e-9)
    }

    // ---- 忍耐时间 ----

    @Test
    fun `不活跃时是她愿意等的上限，满活跃时是下限`() {
        for (frequency in UrgePolicy.Frequency.entries) {
            val idle = UrgePolicy.delayMinutes(0.0, frequency)
            val hot = UrgePolicy.delayMinutes(1.0, frequency)
            val half = UrgePolicy.delayMinutes(0.5, frequency)
            val middle = (idle + hot) / 2

            assertEquals(UrgePolicy.idleMinutes(frequency), idle, 1e-9)
            assertEquals(UrgePolicy.hotMinutes(frequency), hot, 1e-9)
            assertEquals(middle, half, 1e-9)
            assertTrue("$frequency 的 idle 必须大于 hot", idle > hot)
        }
    }

    @Test
    fun `越主动的档位两端都更短`() {
        assertTrue(UrgePolicy.idleMinutes(UrgePolicy.Frequency.HIGH) < UrgePolicy.idleMinutes(UrgePolicy.Frequency.MEDIUM))
        assertTrue(UrgePolicy.idleMinutes(UrgePolicy.Frequency.MEDIUM) < UrgePolicy.idleMinutes(UrgePolicy.Frequency.LOW))
    }

    @Test
    fun `密度被夹在零到一之间`() {
        assertEquals(UrgePolicy.idleMinutes(UrgePolicy.Frequency.MEDIUM), UrgePolicy.delayMinutes(-5.0, UrgePolicy.Frequency.MEDIUM), 1e-9)
        assertEquals(UrgePolicy.hotMinutes(UrgePolicy.Frequency.MEDIUM), UrgePolicy.delayMinutes(9.0, UrgePolicy.Frequency.MEDIUM), 1e-9)
    }

    // ---- 排程 ----

    @Test
    fun `中位数采样下排出的就是原始的忍耐时间`() {
        val scheduled = UrgePolicy.schedule(UrgePolicy.State(), now, emptyList(), sampler = neutral())

        assertEquals(60.0, scheduled.lastDelayMinutes, 1e-9)
        assertEquals(now + 60 * minute, scheduled.nextAt)
    }

    @Test
    fun `热络时排得更近`() {
        val busy = (0 until 6).map { now - it * UrgePolicy.BUCKET_MS }
        val scheduled = UrgePolicy.schedule(UrgePolicy.State(), now, busy, sampler = neutral())

        assertEquals(15.0, scheduled.lastDelayMinutes, 1e-9)
    }

    @Test
    fun `抖动在正负比例之内`() {
        val late = UrgePolicy.schedule(UrgePolicy.State(), now, emptyList(), sampler = sampler(0.5, 1.0))
        val early = UrgePolicy.schedule(UrgePolicy.State(), now, emptyList(), sampler = sampler(0.5, 0.0))
        val ratio = UrgePolicy.Config().jitterRatio

        assertEquals(60.0 * (1 + ratio), late.lastDelayMinutes, 1e-9)
        assertEquals(60.0 * (1 - ratio), early.lastDelayMinutes, 1e-9)
    }

    @Test
    fun `偶发极端会把等待缩到四分之一`() {
        val extreme = UrgePolicy.schedule(UrgePolicy.State(), now, emptyList(), sampler = sampler(0.01, 0.5))

        assertEquals(60.0 * UrgePolicy.EXTREME_RATIO, extreme.lastDelayMinutes, 1e-9)
    }

    @Test
    fun `极端概率为零时永远不触发`() {
        val config = UrgePolicy.Config(extremeChance = 0.0)
        val scheduled = UrgePolicy.schedule(UrgePolicy.State(), now, emptyList(), config, sampler(0.0, 0.5))

        assertEquals(60.0, scheduled.lastDelayMinutes, 1e-9)
    }

    @Test
    fun `任何档位下延迟都不低于下限`() {
        for (frequency in UrgePolicy.Frequency.entries) {
            val scheduled = UrgePolicy.schedule(
                UrgePolicy.State(),
                now,
                (0 until 12).map { now - it * UrgePolicy.BUCKET_MS },
                UrgePolicy.Config(frequency = frequency),
                sampler(0.01, 0.0)
            )
            assertTrue(
                "$frequency 排出了 ${scheduled.lastDelayMinutes} 分钟",
                scheduled.lastDelayMinutes >= UrgePolicy.MIN_DELAY_MINUTES
            )
        }
    }

    // ---- 到期与记账 ----

    @Test
    fun `没排过期时算作可以做`() {
        assertTrue(UrgePolicy.isDue(UrgePolicy.State(), now))
    }

    @Test
    fun `排到未来就是还没到时候`() {
        val scheduled = UrgePolicy.schedule(UrgePolicy.State(), now, emptyList(), sampler = neutral())

        assertFalse(UrgePolicy.isDue(scheduled, now))
        assertTrue(UrgePolicy.isDue(scheduled, scheduled.nextAt))
    }

    @Test
    fun `开口之后记下时刻，但不改动排程`() {
        val scheduled = UrgePolicy.schedule(UrgePolicy.State(), now, emptyList(), sampler = neutral())
        val spoken = UrgePolicy.recordSpoke(scheduled, now + 5 * minute)

        assertEquals(now + 5 * minute, spoken.lastAt)
        assertEquals(scheduled.nextAt, spoken.nextAt)
    }

    // ---- 序列化与约定 ----

    @Test
    fun `状态可以 json 往返，坏数据退回默认值`() {
        val full = UrgePolicy.State(nextAt = now + minute, lastAt = now - minute, lastDelayMinutes = 12.5)

        assertEquals(full, UrgePolicy.State.fromJson(full.toJson()))
        assertEquals(UrgePolicy.State(), UrgePolicy.State.fromJson("不是 json"))
        assertEquals(UrgePolicy.State(), UrgePolicy.State.fromJson(null))
    }

    @Test
    fun `默认关闭且默认档位是中`() {
        val config = UrgePolicy.Config()

        assertFalse("默认必须不动旧行为", config.enabled)
        assertEquals(UrgePolicy.Frequency.MEDIUM, config.frequency)
        assertEquals(45, config.halfLifeMinutes)
    }

    @Test
    fun `scope 与主键名是落库约定，必须稳定`() {
        assertEquals("URGE", UrgePolicy.SCOPE)
        assertEquals("urge:thread-1", UrgePolicy.rowId("thread-1"))
    }
}
