package com.aster.companion.lite.proactive

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteProactivePolicyTest {

    private val settings = LiteProactiveSettings()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = ZonedDateTime.of(2026, 9, 11, 14, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun snapshot(
        lastMessageAt: Long,
        lastProactiveAt: Long = 0L,
        todayProactiveCount: Int = 0,
        active: Boolean = false,
        probed: Boolean = false
    ) = LiteProactiveThreadSnapshot(
        threadId = "thread",
        characterId = "lan",
        lastMessageAt = lastMessageAt,
        lastProactiveAt = lastProactiveAt,
        todayProactiveCount = todayProactiveCount,
        active = active,
        probedForCurrentIdle = probed
    )

    @Test
    fun `idle only starts after the quiet window`() {
        // 不写死秒数：静默期是可调参数（当前默认 2 分钟），写死会在调参后误报。
        assertNull(LiteProactivePolicy.idleAtOrNull(noon, noon + settings.idleQuietMs - 1, settings))
        assertEquals(
            noon + settings.idleQuietMs,
            LiteProactivePolicy.idleAtOrNull(noon, noon + settings.idleQuietMs, settings)
        )
    }

    @Test
    fun `afterglow waits the configured delay past idle`() {
        val idleAt = noon + settings.idleQuietMs

        assertNull(LiteProactivePolicy.afterglowAtOrNull(noon, idleAt - 1, settings))
        assertNull(
            LiteProactivePolicy.afterglowAtOrNull(noon, idleAt + settings.afterglowDelayMs - 1, settings)
        )
        assertEquals(
            idleAt + settings.afterglowDelayMs,
            LiteProactivePolicy.afterglowAtOrNull(noon, idleAt + settings.afterglowDelayMs, settings)
        )
    }

    @Test
    fun `a newer message always restarts the countdown`() {
        val laterMessage = noon + 3 * 60_000L

        assertNull(LiteProactivePolicy.afterglowAtOrNull(laterMessage, noon + 6 * 60_000L, settings))
    }

    @Test
    fun `probing is allowed only when nothing else owns the character`() {
        val eligible = noon + 7 * 60_000L

        assertTrue(LiteProactivePolicy.canProbe(snapshot(noon), eligible, settings, imageBusy = false, dailyTotal = 0, zone = zone))
        assertFalse(LiteProactivePolicy.canProbe(snapshot(noon), eligible, settings, imageBusy = true, dailyTotal = 0, zone = zone))
        assertFalse(LiteProactivePolicy.canProbe(snapshot(noon, active = true), eligible, settings, imageBusy = false, dailyTotal = 0, zone = zone))
        assertFalse(LiteProactivePolicy.canProbe(snapshot(noon, probed = true), eligible, settings, imageBusy = false, dailyTotal = 0, zone = zone))
    }

    @Test
    fun `cooldown and daily limits stop repeat messages`() {
        val eligible = noon + 7 * 60_000L

        assertFalse(
            LiteProactivePolicy.canProbe(
                snapshot(noon, lastProactiveAt = eligible - 60_000L),
                eligible,
                settings,
                imageBusy = false,
                dailyTotal = 1,
                zone = zone
            )
        )
        assertFalse(
            LiteProactivePolicy.canProbe(snapshot(noon), eligible, settings, imageBusy = false, dailyTotal = settings.dailyGlobalLimit, zone = zone)
        )
    }

    @Test
    fun `quiet hours suppress probing`() {
        val night = ZonedDateTime.of(2026, 9, 12, 2, 0, 0, 0, zone).toInstant().toEpochMilli()
        // 已经越过余韵时点：这一拍被拦下的原因只应该是安静时段。
        val nightLastMessage = night - 7 * 60_000L

        assertTrue(LiteProactivePolicy.inQuietHours(night, settings, zone))
        assertFalse(LiteProactivePolicy.inQuietHours(noon, settings, zone))
        assertFalse(
            LiteProactivePolicy.canProbe(
                snapshot(nightLastMessage),
                night,
                settings,
                imageBusy = false,
                dailyTotal = 0,
                zone = zone
            )
        )
    }

    @Test
    fun `candidates put the freshest conversation first`() {
        val older = snapshot(noon - 60 * 60_000L)
        val freshest = snapshot(noon)
        val middle = snapshot(noon - 30 * 60_000L)

        val ranked = LiteProactivePolicy.rankCandidates(listOf(older, middle, freshest), noon, settings)

        assertEquals(
            listOf(noon, noon - 30 * 60_000L, noon - 60 * 60_000L),
            ranked.map { it.lastMessageAt }
        )
    }

    @Test
    fun `two stage mode spends two slots per character`() {
        assertEquals(2, LiteProactivePolicy.parallelSlots(LiteProactiveSettings(twoStage = true)))
        assertEquals(4, LiteProactivePolicy.parallelSlots(LiteProactiveSettings(twoStage = false)))
    }

    @Test
    fun `planProbes drops blocked candidates and caps at the slot budget`() {
        val eligible = noon + 7 * 60_000L
        val snapshots = listOf(
            snapshot(noon),
            snapshot(noon - 60_000L, probed = true),
            snapshot(noon - 120_000L, lastProactiveAt = eligible - 60_000L),
            snapshot(noon - 180_000L),
            snapshot(noon - 240_000L)
        )

        val planned = LiteProactivePolicy.planProbes(
            snapshots = snapshots,
            now = eligible,
            settings = settings,
            imageBusy = false,
            dailyTotal = 0,
            zone = zone
        )

        // 两段式：4 槽 → 2 个角色；已探活与冷却中的线程被剔除。
        assertEquals(2, planned.size)
        assertEquals(listOf(noon, noon - 180_000L), planned.map { it.lastMessageAt })
    }

    @Test
    fun `image generation makes the whole tick stand down`() {
        val eligible = noon + 7 * 60_000L

        val planned = LiteProactivePolicy.planProbes(
            snapshots = listOf(snapshot(noon)),
            now = eligible,
            settings = settings,
            imageBusy = true,
            dailyTotal = 0,
            zone = zone
        )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun `persist budget caps how many probes reach the conversation`() {
        assertEquals(2, LiteProactivePolicy.persistBudget(5, settings))
        assertEquals(1, LiteProactivePolicy.persistBudget(1, settings))
        assertEquals(0, LiteProactivePolicy.persistBudget(0, settings))
        assertEquals(1, LiteProactivePolicy.persistBudget(3, LiteProactiveSettings(perTickLimit = 1)))
    }

    @Test
    fun `long idle heartbeat allows another probe after the interval`() {
        val eligible = noon + 7 * 60_000L
        val heartbeat = settings.copy(idleHeartbeatMs = 2 * 60 * 60_000L)
        val justProbed = snapshot(noon, probed = true).copy(lastProbeAt = eligible - 60_000L)
        val probedLongAgo = snapshot(noon, probed = true).copy(lastProbeAt = eligible - 3 * 60 * 60_000L)

        assertFalse(
            LiteProactivePolicy.canProbe(justProbed, eligible, heartbeat, imageBusy = false, dailyTotal = 0, zone = zone)
        )
        assertTrue(
            LiteProactivePolicy.canProbe(probedLongAgo, eligible, heartbeat, imageBusy = false, dailyTotal = 0, zone = zone)
        )
    }

    @Test
    fun `heartbeat off keeps a single probe per idle window`() {
        val eligible = noon + 7 * 60_000L
        val off = settings.copy(idleHeartbeatMs = 0L)
        val probedLongAgo = snapshot(noon, probed = true).copy(lastProbeAt = eligible - 10 * 60 * 60_000L)

        assertFalse(
            LiteProactivePolicy.canProbe(probedLongAgo, eligible, off, imageBusy = false, dailyTotal = 0, zone = zone)
        )
    }

    @Test
    fun `moment sharing respects cooldown quiet hours image work and the off switch`() {
        val night = ZonedDateTime.of(2026, 9, 12, 2, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertTrue(LiteProactivePolicy.canShareMoment(0L, noon, settings, imageBusy = false, zone = zone))
        assertFalse(
            LiteProactivePolicy.canShareMoment(noon - 60_000L, noon, settings, imageBusy = false, zone = zone)
        )
        assertFalse(
            LiteProactivePolicy.canShareMoment(0L, noon, settings.copy(momentsEnabled = false), imageBusy = false, zone = zone)
        )
        assertFalse(LiteProactivePolicy.canShareMoment(0L, noon, settings, imageBusy = true, zone = zone))
        assertFalse(LiteProactivePolicy.canShareMoment(0L, night, settings, imageBusy = false, zone = zone))
        assertTrue(
            LiteProactivePolicy.canShareMoment(noon - 8 * 60 * 60_000L, noon, settings, imageBusy = false, zone = zone)
        )
    }

    @Test
    fun `a character sleeping on their own schedule is left alone`() {
        val eligible = noon + 7 * 60_000L

        assertFalse(
            LiteProactivePolicy.canProbe(
                snapshot(noon).copy(sleeping = true),
                eligible,
                settings,
                imageBusy = false,
                dailyTotal = 0,
                zone = zone
            )
        )
        assertTrue(
            LiteProactivePolicy.canProbe(snapshot(noon), eligible, settings, imageBusy = false, dailyTotal = 0, zone = zone)
        )
    }

    @Test
    fun `他说过晚安之后这一拍完全不参与主动陪伴`() {
        // 回归背景：这两个开关原本只有"事件驱动"那条路在看，Lite 主动陪伴这条路一个都没看 ——
        // 于是用户说完「我去睡了」她当晚照样来找他，在会话管理里关掉「主动联系」也照样来。
        val awake = snapshot(noon)
        val paused = awake.copy(characterId = "qiu", paused = true)
        val switchedOff = awake.copy(characterId = "lan2", proactiveEnabled = false)

        val kept = LiteProactivePolicy.filterEligible(listOf(awake, paused, switchedOff))

        assertEquals(listOf("lan"), kept.map { it.characterId })
        // 被按下 / 被关掉的角色连探活资格都不该有。
        val eligible = noon + 7 * 60_000L
        assertTrue(LiteProactivePolicy.canProbe(awake, eligible, settings, imageBusy = false, dailyTotal = 0, zone = zone))
        assertFalse(LiteProactivePolicy.canProbe(paused, eligible, settings, imageBusy = false, dailyTotal = 0, zone = zone))
        assertFalse(
            LiteProactivePolicy.canProbe(switchedOff, eligible, settings, imageBusy = false, dailyTotal = 0, zone = zone)
        )
    }
}
