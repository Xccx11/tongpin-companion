package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 投递台账的状态机。
 *
 * 核心是**终态不可逆**：已经出现在聊天里的回复永远不许改回"失败"——改回去就可能重发一条
 * 一模一样的；被守卫挡下的也不许反悔。只有 `FAILED` 能回到 `PENDING` 重试，
 * 而且**必须清掉 `completedAt`**。
 */
class DeliveryLedgerPolicyTest {

    private val now = 1_700_000_000_000L

    private val pending = DeliveryLedgerPolicy.State.PENDING
    private val delivered = DeliveryLedgerPolicy.State.DELIVERED
    private val failed = DeliveryLedgerPolicy.State.FAILED
    private val cancelled = DeliveryLedgerPolicy.State.CANCELLED
    private val allStates = DeliveryLedgerPolicy.State.entries

    private fun entry(
        state: DeliveryLedgerPolicy.State = DeliveryLedgerPolicy.State.PENDING,
        completedAt: Long = 0L,
        id: String = "m1"
    ) = DeliveryLedgerPolicy.Entry(
        messageId = id,
        threadId = "t1",
        state = state,
        createdAt = now - 1000,
        completedAt = completedAt
    )

    // ---- 合法迁移 ----

    @Test
    fun `待投递可以走向任何一个结论`() {
        assertEquals(delivered, DeliveryLedgerPolicy.transition(pending, delivered))
        assertEquals(failed, DeliveryLedgerPolicy.transition(pending, failed))
        assertEquals(cancelled, DeliveryLedgerPolicy.transition(pending, cancelled))
    }

    @Test
    fun `待投递不能停在待投递`() {
        assertNull(DeliveryLedgerPolicy.transition(pending, pending))
    }

    @Test
    fun `失败可以重试，也可以放弃`() {
        assertEquals(pending, DeliveryLedgerPolicy.transition(failed, pending))
        assertEquals(cancelled, DeliveryLedgerPolicy.transition(failed, cancelled))
    }

    /** 失败**不能**直接变成"已送达"：没真的落库就不许记成她说了话。 */
    @Test
    fun `失败不能直接变成已送达`() {
        assertNull(DeliveryLedgerPolicy.transition(failed, delivered))
        assertNull(DeliveryLedgerPolicy.transition(failed, failed))
    }

    @Test
    fun `已送达是终态，任何迁移都不合法`() {
        for (to in allStates) {
            assertNull("DELIVERED → $to 必须非法", DeliveryLedgerPolicy.transition(delivered, to))
        }
    }

    @Test
    fun `已取消是终态，任何迁移都不合法`() {
        for (to in allStates) {
            assertNull("CANCELLED → $to 必须非法", DeliveryLedgerPolicy.transition(cancelled, to))
        }
    }

    @Test
    fun `终态判定`() {
        assertTrue(DeliveryLedgerPolicy.isTerminal(delivered))
        assertTrue(DeliveryLedgerPolicy.isTerminal(cancelled))
        assertFalse(DeliveryLedgerPolicy.isTerminal(pending))
        assertFalse(DeliveryLedgerPolicy.isTerminal(failed))
    }

    // ---- 应用迁移 ----

    @Test
    fun `推到终态会落下完成时刻`() {
        val done = DeliveryLedgerPolicy.apply(entry(), delivered, now, reason = "已落库")

        assertEquals(delivered, done.state)
        assertEquals(now, done.completedAt)
        assertEquals("已落库", done.reason)
    }

    @Test
    fun `重试时必须清掉完成时刻`() {
        val previouslyFailed = entry(failed, completedAt = now - 5000)
        val retried = DeliveryLedgerPolicy.apply(previouslyFailed, pending, now, reason = "重试")

        assertEquals(pending, retried.state)
        assertEquals("上次什么时候结束的不能污染这一轮", 0L, retried.completedAt)
    }

    /** 非法迁移返回**原样**：投递路径上不该因为账本问题炸掉一次回复。 */
    @Test
    fun `非法迁移原样返回`() {
        val alreadyDelivered = entry(delivered, completedAt = now)

        assertEquals(alreadyDelivered, DeliveryLedgerPolicy.apply(alreadyDelivered, failed, now + 1000))
        assertEquals(alreadyDelivered, DeliveryLedgerPolicy.apply(alreadyDelivered, cancelled, now + 1000))
    }

    // ---- 裁剪 ----

    @Test
    fun `未终结的一条都不许丢，终态只留最近若干条`() {
        val entries = listOf(
            entry(pending, id = "pending"),
            entry(failed, id = "failed", completedAt = 100),
            entry(delivered, id = "old", completedAt = 1000),
            entry(delivered, id = "mid", completedAt = 2000),
            entry(delivered, id = "new", completedAt = 3000)
        )

        val keep = DeliveryLedgerPolicy.retainedIds(entries, limit = 2)

        assertTrue(keep.containsAll(listOf("pending", "failed", "new", "mid")))
        assertFalse("最旧的终态该被裁掉", keep.contains("old"))
    }

    @Test
    fun `上限为零时仍然保留未终结的`() {
        val entries = listOf(
            entry(pending, id = "pending"),
            entry(delivered, id = "done", completedAt = 1000)
        )

        val keep = DeliveryLedgerPolicy.retainedIds(entries, limit = 0)

        assertEquals(setOf("pending"), keep)
    }

    // ---- 序列化与主键 ----

    @Test
    fun `台账行可以 json 往返，坏数据丢行而不是崩`() {
        val full = entry(cancelled, completedAt = now, id = "m9").copy(
            segmentIndex = 1,
            segmentCount = 3,
            reason = "上下文版本已变"
        )

        assertEquals(full, DeliveryLedgerPolicy.Entry.fromJson(full.toJson()))
        assertNull(DeliveryLedgerPolicy.Entry.fromJson("不是 json"))
        assertNull(DeliveryLedgerPolicy.Entry.fromJson(null))
        assertNull(DeliveryLedgerPolicy.Entry.fromJson(""))
    }

    @Test
    fun `主键与 scope 名是落库约定，必须稳定`() {
        assertEquals("DELIVERY_LEDGER", DeliveryLedgerPolicy.SCOPE)
        assertEquals("delivery:abc", DeliveryLedgerPolicy.rowId("abc"))
    }
}
