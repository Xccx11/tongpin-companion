package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 投递台账：**"她说了话"这件事必须有据可查，而且终态不许反悔**。
 *
 * ## 它解决什么
 *
 * 出站消息在落到聊天记录之前会经过几道闸（模型有没有返回、投递守卫放不放行、上下文版本还对不对）。
 * 以前这些闸的结论**没有任何记录**：
 *
 * - 被守卫挡下 → 只是 `continue` 跳过，谁也不知道她本来要说什么、为什么没说；
 * - 模型调用失败 → 直接往聊天里插一条消息（见下），失败被记成了"她说了话"；
 * - 成功 → 落库，但和上面两种情况在数据上长得一样。
 *
 * 现在每次投递都先记一条 [Entry]（`PENDING`），再按结果推到 `DELIVERED` / `FAILED` / `CANCELLED`。
 *
 * ## 终态不可逆（这一条是整块的核心）
 *
 * - `DELIVERED`：已经出现在聊天里了，**永远不许改回别的状态**——改回去就可能重发一条一模一样的；
 * - `CANCELLED`：守卫已经判过"这一条不该发"，同样不许反悔；
 * - `FAILED`：唯一可以重试的状态，回到 `PENDING` 时**必须清掉 `completedAt`**，
 *   否则"上次什么时候结束的"会污染这一轮的判断；
 * - 非法迁移一律返回 `null`（调用方按"不改"处理），而不是静默改掉。
 *
 * ## 与上游的差别
 *
 * 上游的 `DELIVERED` 指"平台回执确认送达"；同频没有外部平台，落库即送达，
 * 所以 `DELIVERED` 的判据是"这条可见消息真的写进聊天记录了"。状态集合与不可逆规则完全一致。
 */
object DeliveryLedgerPolicy {

    /** 台账存放的 world state scope（免 DB 迁移，与 `LIFE_PLAN` / `AGENCY_WINDOW` 同一路子）。 */
    const val SCOPE = "DELIVERY_LEDGER"

    /** 每个会话保留多少条**终态**记录；未终结的（`PENDING` / `FAILED`）永远保留。 */
    const val RETAIN_PER_THREAD = 50

    enum class State {
        /** 已经决定要说，但还没落库。 */
        PENDING,

        /** 已经出现在聊天里。**终态**。 */
        DELIVERED,

        /** 这次没成，可以重试。 */
        FAILED,

        /** 守卫挡下 / 主动放弃。**终态**。 */
        CANCELLED
    }

    /** 终态：不许再改。 */
    fun isTerminal(state: State): Boolean = state == State.DELIVERED || state == State.CANCELLED

    /**
     * 合法迁移表。返回 `null` = 非法（调用方必须当作"不改"，不许静默改掉）。
     *
     * | 从 | 可以到 |
     * |---|---|
     * | `PENDING` | `DELIVERED` / `FAILED` / `CANCELLED` |
     * | `FAILED` | `PENDING`（重试）/ `CANCELLED` |
     * | `DELIVERED` | —（终态） |
     * | `CANCELLED` | —（终态） |
     */
    fun transition(from: State, to: State): State? = when (from) {
        State.PENDING -> to.takeIf { it != State.PENDING }
        State.FAILED -> to.takeIf { it == State.PENDING || it == State.CANCELLED }
        State.DELIVERED, State.CANCELLED -> null
    }

    /** 一条投递记录。一个 `messageId` 一条；分段投递用 [segmentIndex] / [segmentCount] 标记。 */
    @Serializable
    data class Entry(
        val messageId: String,
        val threadId: String,
        val state: State,
        /** 该消息是本次投递的第几段（从 0 起）。 */
        val segmentIndex: Int = 0,
        val segmentCount: Int = 1,
        /** 状态原因（人类可读，用于排查"她为什么没说"）。 */
        val reason: String = "",
        val createdAt: Long = 0L,
        /** 终态落定的时刻；回到 `PENDING` 时清 0。 */
        val completedAt: Long = 0L
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): Entry? {
                if (raw.isNullOrBlank()) return null
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
            }
        }
    }

    /** 台账行主键：一个 messageId 一行。 */
    fun rowId(messageId: String): String = "delivery:$messageId"

    /**
     * 迁移状态；非法迁移返回**原样**（不抛异常：投递路径上不该因为账本问题炸掉一次回复）。
     *
     * 回到 `PENDING` 时清掉 [Entry.completedAt]，见类注释。
     */
    fun apply(entry: Entry, to: State, now: Long, reason: String = ""): Entry {
        if (transition(entry.state, to) == null) return entry
        return entry.copy(
            state = to,
            reason = reason,
            completedAt = if (to == State.PENDING) 0L else now
        )
    }

    /**
     * 裁剪：**未终结的一条都不许丢**，终态只留最近 [limit] 条。
     *
     * 返回"该留下的 messageId 集合"，调用方删掉不在集合里的行。
     */
    fun retainedIds(entries: List<Entry>, limit: Int = RETAIN_PER_THREAD): Set<String> {
        val open = entries.filterNot { isTerminal(it.state) }
        val settled = entries.filter { isTerminal(it.state) }
            .sortedByDescending { it.completedAt }
            .take(limit.coerceAtLeast(0))
        return (open + settled).map { it.messageId }.toSet()
    }
}
