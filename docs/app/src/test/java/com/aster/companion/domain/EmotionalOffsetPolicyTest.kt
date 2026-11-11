package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情绪底色状态机（[EmotionalOffsetPolicy]）的纯逻辑测试。
 *
 * 契约：攒够才换、同向变结实、反向会消退、失败不丢累积、方向与强度**一律由宿主决定**。
 *
 * 第一组用例专门钉死**方向语义**——上游规格是"正数=更严肃/更收敛，负数=更轻松/更活跃"，
 * 这条曾经被写反过（把"更活跃"写成了"更退开"），必须有测试锁住。
 */
class EmotionalOffsetPolicyTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun offset(
        direction: EmotionalOffsetPolicy.Direction = EmotionalOffsetPolicy.Direction.SERIOUS,
        description: String = "（语气发紧）",
        intensity: Double = 1.2,
        generatedAt: Long = now
    ) = EmotionalOffsetPolicy.Offset(direction, description, intensity, generatedAt)

    private fun state(
        alterValue: Double = 0.0,
        weight: Double = 0.0,
        lastTriggerDirection: Int = 0,
        offset: EmotionalOffsetPolicy.Offset? = null,
        history: List<EmotionalOffsetPolicy.HistoryEntry> = emptyList(),
        pendingScopes: List<EmotionalOffsetPolicy.PendingScope> = emptyList(),
        turn: Long = 0L,
        lastAttemptAt: Long = 0L
    ) = EmotionalOffsetPolicy.State(
        alterValue = alterValue,
        weight = weight,
        lastTriggerDirection = lastTriggerDirection,
        offset = offset,
        history = history,
        pendingScopes = pendingScopes,
        turn = turn,
        lastUpdatedAt = now,
        lastAttemptAt = lastAttemptAt
    )

    // ---- 方向语义（最要紧的一组）----

    @Test
    fun `正数是更严肃收敛，负数是更轻松活跃`() {
        assertEquals(EmotionalOffsetPolicy.Direction.SERIOUS, EmotionalOffsetPolicy.directionOf(1))
        assertEquals(EmotionalOffsetPolicy.Direction.SERIOUS, EmotionalOffsetPolicy.directionOf(5))
        assertEquals(EmotionalOffsetPolicy.Direction.RELAXED, EmotionalOffsetPolicy.directionOf(-1))
        assertEquals(EmotionalOffsetPolicy.Direction.RELAXED, EmotionalOffsetPolicy.directionOf(-5))
        assertNull(EmotionalOffsetPolicy.directionOf(0))
    }

    @Test
    fun `方向词说的是严肃收敛与轻松活跃，不是亲密或冷淡`() {
        val serious = EmotionalOffsetPolicy.directionWord(EmotionalOffsetPolicy.Direction.SERIOUS)
        val relaxed = EmotionalOffsetPolicy.directionWord(EmotionalOffsetPolicy.Direction.RELAXED)
        assertTrue(serious.contains("严肃"))
        assertTrue(serious.contains("收敛"))
        assertTrue(relaxed.contains("轻松"))
        assertTrue(relaxed.contains("活跃"))
    }

    // ---- 阈值与强度 ----

    @Test
    fun `阈值随活跃度下降，但不低于基础值的一半`() {
        assertEquals(10.0, EmotionalOffsetPolicy.thresholdFor(0), 0.0001)
        assertEquals(9.1, EmotionalOffsetPolicy.thresholdFor(3), 0.0001)
        assertEquals(7.0, EmotionalOffsetPolicy.thresholdFor(10), 0.0001)
        assertEquals(7.0, EmotionalOffsetPolicy.thresholdFor(999), 0.0001)
        assertTrue(
            EmotionalOffsetPolicy.thresholdFor(999) >=
                EmotionalOffsetPolicy.BASE_THRESHOLD * EmotionalOffsetPolicy.MIN_THRESHOLD_RATIO
        )
    }

    @Test
    fun `强度是累计值相对阈值，并封顶到二`() {
        assertEquals(0.7, EmotionalOffsetPolicy.intensityOf(7.0, 10.0), 0.0001)
        assertEquals(1.0, EmotionalOffsetPolicy.intensityOf(10.0, 10.0), 0.0001)
        assertEquals(2.0, EmotionalOffsetPolicy.intensityOf(50.0, 10.0), 0.0001)
        assertEquals(0.0, EmotionalOffsetPolicy.intensityOf(5.0, 0.0), 0.0001)
    }

    @Test
    fun `强度格式化保留一位小数`() {
        assertEquals("1.2", EmotionalOffsetPolicy.formatIntensity(1.24))
        assertEquals("2.0", EmotionalOffsetPolicy.formatIntensity(9.0))
    }

    // ---- 吃变化量 ----

    @Test
    fun `缺失或零变化量原样返回`() {
        val base = state(alterValue = 3.0)
        assertEquals(base, EmotionalOffsetPolicy.observe(base, null, "c1", "user-message", now, 0))
        assertEquals(base, EmotionalOffsetPolicy.observe(base, 0, "c1", "user-message", now, 0))
    }

    @Test
    fun `变化量被夹进合法范围`() {
        val hi = EmotionalOffsetPolicy.observe(state(), 99, null, "user-message", now, 0)
        val lo = EmotionalOffsetPolicy.observe(state(), -99, null, "user-message", now, 0)
        assertEquals(5.0, hi.alterValue, 0.0001)
        assertEquals(-5.0, lo.alterValue, 0.0001)
    }

    @Test
    fun `带符号累积：同向相加反向相抵`() {
        var s = state()
        s = EmotionalOffsetPolicy.observe(s, 3, "c1", "user-message", now, 0)
        s = EmotionalOffsetPolicy.observe(s, 3, "c1", "user-message", now, 0)
        assertEquals(6.0, s.alterValue, 0.0001)
        s = EmotionalOffsetPolicy.observe(s, -2, "c1", "user-message", now, 0)
        assertEquals(4.0, s.alterValue, 0.0001)
    }

    @Test
    fun `评分轨迹记录轮次相位与参与者，并受长度上限约束`() {
        var s = state()
        repeat(3) { s = EmotionalOffsetPolicy.observe(s, 1, "c1", "user-message", now, 0) }
        assertEquals(3, s.history.size)
        assertEquals(1L, s.history.first().turn)
        assertEquals(3L, s.history.last().turn)
        assertEquals("user-message", s.history.last().phase)
        assertEquals("c1", s.history.last().participantId)
        assertEquals(3.0, s.history.last().alterValue, 0.0001)

        var long = state()
        repeat(EmotionalOffsetPolicy.HISTORY_LIMIT + 5) { long = EmotionalOffsetPolicy.observe(long, 1, "c1", "advance", now, 0) }
        assertEquals(EmotionalOffsetPolicy.HISTORY_LIMIT, long.history.size)
        assertEquals((EmotionalOffsetPolicy.HISTORY_LIMIT + 5).toLong(), long.history.last().turn)
        assertEquals((EmotionalOffsetPolicy.HISTORY_LIMIT + 5).toLong(), long.turn)
    }

    @Test
    fun `待分析累计按来源分桶`() {
        var s = state()
        s = EmotionalOffsetPolicy.observe(s, 2, "c1", "user-message", now, 0)
        s = EmotionalOffsetPolicy.observe(s, 3, "c2", "user-message", now, 0)
        s = EmotionalOffsetPolicy.observe(s, 1, "c1", "user-message", now, 0)
        assertEquals(2, s.pendingScopes.size)
        assertEquals(3.0, s.pendingScopes.first { it.participantId == "c1" }.alterValue, 0.0001)
        assertEquals(3.0, s.pendingScopes.first { it.participantId == "c2" }.alterValue, 0.0001)
        assertEquals(6.0, s.alterValue, 0.0001)
    }

    @Test
    fun `全局剧本的来源是空值，同样可以分桶`() {
        val s = EmotionalOffsetPolicy.observe(state(), 4, null, "advance", now, 0)
        assertEquals(1, s.pendingScopes.size)
        assertNull(s.pendingScopes.single().participantId)
        assertEquals(4.0, s.pendingScopes.single().alterValue, 0.0001)
    }

    // ---- 同向增强 / 反向消退 ----

    @Test
    fun `同向变化让底色更结实`() {
        val base = state(weight = 0.7, lastTriggerDirection = 1, offset = offset())
        val boosted = EmotionalOffsetPolicy.observe(base, 1, "c1", "user-message", now, 0)
        assertEquals(0.75, boosted.weight, 0.0001)
        assertNotNull(boosted.offset)
    }

    @Test
    fun `反向变化让底色消退但不立刻清除`() {
        val base = state(weight = 1.0, lastTriggerDirection = 1, offset = offset())
        val decayed = EmotionalOffsetPolicy.observe(base, -2, "c1", "user-message", now, 0)
        assertEquals(0.7, decayed.weight, 0.0001)
        assertNotNull(decayed.offset)
    }

    @Test
    fun `反向消退到下限以下清除底色，但累计值保留`() {
        val base = state(weight = 0.3, lastTriggerDirection = 1, offset = offset())
        val cleared = EmotionalOffsetPolicy.observe(base, -5, "c1", "user-message", now, 0)
        assertNull(cleared.offset)
        assertEquals(0.0, cleared.weight, 0.0001)
        // 反向位移仍留在累计值里，新方向可以自己攒到触发。
        assertEquals(-5.0, cleared.alterValue, 0.0001)
    }

    // ---- 触发 ----

    @Test
    fun `未达阈值不触发`() {
        assertNull(EmotionalOffsetPolicy.triggerFor(state(alterValue = 9.9), now, 0))
    }

    @Test
    fun `达阈值触发，且方向强度都由宿主算`() {
        val trigger = EmotionalOffsetPolicy.triggerFor(state(alterValue = 10.0), now, 0)
        assertNotNull(trigger)
        assertEquals(EmotionalOffsetPolicy.Direction.SERIOUS, trigger!!.direction)
        assertEquals(10.0, trigger.value, 0.0001)
        assertEquals(10.0, trigger.threshold, 0.0001)
        assertEquals(1.0, trigger.intensity, 0.0001)
    }

    @Test
    fun `负向累计触发的是轻松活跃`() {
        val trigger = EmotionalOffsetPolicy.triggerFor(state(alterValue = -12.0), now, 0)
        assertNotNull(trigger)
        assertEquals(EmotionalOffsetPolicy.Direction.RELAXED, trigger!!.direction)
        assertEquals(1.2, trigger.intensity, 0.0001)
    }

    @Test
    fun `失败退避窗口内不触发，窗口外可以再试`() {
        val cooling = state(alterValue = 10.0, lastAttemptAt = now - minute)
        assertNull(EmotionalOffsetPolicy.triggerFor(cooling, now, 0))
        val ready = state(alterValue = 10.0, lastAttemptAt = now - EmotionalOffsetPolicy.RETRY_BACKOFF_MS)
        assertNotNull(EmotionalOffsetPolicy.triggerFor(ready, now, 0))
    }

    @Test
    fun `已有底色时仍然可以因再次攒够而触发`() {
        val withOffset = state(alterValue = 11.0, weight = 1.0, lastTriggerDirection = 1, offset = offset())
        assertNotNull(EmotionalOffsetPolicy.triggerFor(withOffset, now, 0))
    }

    // ---- 生成结果 ----

    @Test
    fun `生成成功后换上底色并清零累计值与分桶`() {
        val trigger = EmotionalOffsetPolicy.triggerFor(state(alterValue = 12.0), now, 0)!!
        val done = EmotionalOffsetPolicy.onGenerated(
            state(alterValue = 12.0, pendingScopes = listOf(EmotionalOffsetPolicy.PendingScope("c1", 12.0))),
            trigger,
            "（语气发紧，话变短了）",
            now
        )
        assertEquals(0.0, done.alterValue, 0.0001)
        assertEquals(1.0, done.weight, 0.0001)
        assertEquals(1, done.lastTriggerDirection)
        assertTrue(done.pendingScopes.isEmpty())
        val result = requireNotNull(done.offset)
        assertEquals(EmotionalOffsetPolicy.Direction.SERIOUS, result.direction)
        assertEquals(1.2, result.intensity, 0.0001)
        assertEquals("（语气发紧，话变短了）", result.description)
    }

    @Test
    fun `方向与强度一律取自宿主，侧端只能给描写`() {
        // 侧端即使在一个负向累计上返回一句"轻松"的描写，方向也仍然由宿主决定。
        val trigger = EmotionalOffsetPolicy.triggerFor(state(alterValue = -12.0), now, 0)!!
        val done = EmotionalOffsetPolicy.onGenerated(state(alterValue = -12.0), trigger, "随便写的描写", now)
        assertEquals(-1, done.lastTriggerDirection)
        assertEquals(EmotionalOffsetPolicy.Direction.RELAXED, done.offset!!.direction)
        assertEquals(1.2, done.offset!!.intensity, 0.0001)
    }

    @Test
    fun `空描写视为失败：不清累计值`() {
        val trigger = EmotionalOffsetPolicy.triggerFor(state(alterValue = 12.0), now, 0)!!
        val failed = EmotionalOffsetPolicy.onGenerated(state(alterValue = 12.0), trigger, "   ", now)
        assertEquals(12.0, failed.alterValue, 0.0001)
        assertNull(failed.offset)
        assertEquals(now, failed.lastAttemptAt)
    }

    @Test
    fun `生成失败保留累计值与轨迹`() {
        val history = listOf(EmotionalOffsetPolicy.HistoryEntry(1L, "user-message", 3, 3.0, now, "c1"))
        val failed = EmotionalOffsetPolicy.onGenerationFailed(
            state(alterValue = 12.0, history = history),
            now
        )
        assertEquals(12.0, failed.alterValue, 0.0001)
        assertEquals(history, failed.history)
        assertEquals(now, failed.lastAttemptAt)
    }

    // ---- 注入 ----

    @Test
    fun `没有底色时 render 给空串`() {
        assertEquals("", EmotionalOffsetPolicy.render(state(), now))
        assertNull(EmotionalOffsetPolicy.activeOffset(state(), now))
    }

    @Test
    fun `render 同时给出方向与强度`() {
        val block = EmotionalOffsetPolicy.render(state(offset = offset()), now)
        assertTrue(block.contains("更严肃、更收敛"))
        assertTrue(block.contains("1.2"))
        assertTrue(block.contains("（语气发紧）"))
    }

    @Test
    fun `底色超过最大存活时长后等于没有`() {
        val old = state(offset = offset(generatedAt = now - EmotionalOffsetPolicy.MAX_OFFSET_AGE_MS - 1))
        assertNull(EmotionalOffsetPolicy.activeOffset(old, now))
        assertEquals("", EmotionalOffsetPolicy.render(old, now))
    }

    @Test
    fun `描写为空或未生成时刻的底色不算有效`() {
        assertNull(EmotionalOffsetPolicy.activeOffset(state(offset = offset(description = "  ")), now))
        assertNull(EmotionalOffsetPolicy.activeOffset(state(offset = offset(generatedAt = 0L)), now))
    }

    // ---- 解析与序列化 ----

    @Test
    fun `变化量解析取第一个整数并夹取`() {
        assertEquals(3, EmotionalOffsetPolicy.parseChange("+3"))
        assertEquals(-2, EmotionalOffsetPolicy.parseChange("变化：-2"))
        assertEquals(5, EmotionalOffsetPolicy.parseChange("9"))
        assertNull(EmotionalOffsetPolicy.parseChange("没有数字"))
        assertNull(EmotionalOffsetPolicy.parseChange(null))
        assertEquals(0, EmotionalOffsetPolicy.parseChangeOrZero("没有数字"))
    }

    @Test
    fun `描写只取第一行并截断`() {
        assertEquals("第一行", EmotionalOffsetPolicy.cleanDescription("  第一行  \n第二行"))
        assertEquals(
            EmotionalOffsetPolicy.MAX_DESCRIPTION_CHARS,
            EmotionalOffsetPolicy.cleanDescription("x".repeat(200)).length
        )
    }

    @Test
    fun `状态可以 json 往返，坏数据退回空状态`() {
        val full = state(
            alterValue = -2.5,
            weight = 0.6,
            lastTriggerDirection = -1,
            offset = offset(direction = EmotionalOffsetPolicy.Direction.RELAXED, description = "松了"),
            history = listOf(EmotionalOffsetPolicy.HistoryEntry(2L, "advance", -3, -2.5, now, null)),
            pendingScopes = listOf(EmotionalOffsetPolicy.PendingScope(null, -2.5)),
            turn = 2L
        )
        assertEquals(full, EmotionalOffsetPolicy.State.fromJson(full.toJson()))
        assertEquals(EmotionalOffsetPolicy.State(), EmotionalOffsetPolicy.State.fromJson("不是 json"))
        assertEquals(EmotionalOffsetPolicy.State(), EmotionalOffsetPolicy.State.fromJson(null))
    }

    @Test
    fun `旧字段 pending 读不进来时退回零累计，而不是崩`() {
        // 上一版把累计值叫 pending；改名后旧 JSON 被忽略，等价于清零一次。
        val legacy = """{"pending":42.0,"weight":0.9,"direction":1,"description":"旧底色"}"""
        val parsed = EmotionalOffsetPolicy.State.fromJson(legacy)
        assertEquals(0.0, parsed.alterValue, 0.0001)
        assertNull(parsed.offset)
    }
}
