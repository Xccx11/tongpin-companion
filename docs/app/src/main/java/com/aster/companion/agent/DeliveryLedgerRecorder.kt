package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.DeliveryLedgerPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 投递台账的读写。状态机在 [DeliveryLedgerPolicy] 里，这里只管落库与裁剪。
 *
 * ## 存在哪里
 *
 * 存进 `companion_world_states` 的独立 scope（[DeliveryLedgerPolicy.SCOPE]），**免 DB 迁移**——
 * 与 `LIFE_PLAN` / `AGENCY_WINDOW` / `MEMORY_ROLLUP` 同一路子。一条消息一行，
 * 主键 `delivery:<messageId>`。
 *
 * 为什么先不建表：一次会话的台账量级是"每条回复一行"，KV 完全够用；而**建表要走一次
 * 单向的库版本升级**，当前 v32 迁移还没在真机上验证过，不值得为一块台账再压一次注。
 * 真到了要按状态批量查、要跨会话聚合的时候，再迁成表。
 *
 * ## 什么时候会写
 *
 * - 回复**真的落进聊天记录**之后 → `DELIVERED`；
 * - 模型调用失败 → `FAILED`（可重试）；
 * - 投递守卫 / 上下文版本把这一条挡下 → `CANCELLED`（终态，记下"她本来要说什么、为什么没说"）。
 *
 * 写台账**永远不会抛异常**：账本出问题不该毁掉一次回复。
 */
@Singleton
class DeliveryLedgerRecorder @Inject constructor(
    private val dao: CompanionDao
) {

    /** 某个会话的全部台账行。 */
    suspend fun entries(threadId: String): List<DeliveryLedgerPolicy.Entry> = runCatching {
        dao.worldStatesByThread(threadId, DeliveryLedgerPolicy.SCOPE)
            .mapNotNull { DeliveryLedgerPolicy.Entry.fromJson(it.content) }
    }.getOrDefault(emptyList())

    /**
     * 记一次投递结论。
     *
     * 没有过记录的 `messageId` 会先以 `PENDING` 建立再迁到 [to]（所以这一步同时也是"开始投递"）；
     * 已经有记录的走 [DeliveryLedgerPolicy.transition]——**非法的迁移按"不改"处理**，
     * 尤其是已经 `DELIVERED` / `CANCELLED` 的条目绝不被改写。
     */
    suspend fun mark(
        threadId: String,
        characterId: String?,
        messageId: String,
        to: DeliveryLedgerPolicy.State,
        now: Long,
        reason: String = "",
        segmentIndex: Int = 0,
        segmentCount: Int = 1
    ) {
        runCatching {
            val existing = entries(threadId).firstOrNull { it.messageId == messageId }
            val base = existing ?: DeliveryLedgerPolicy.Entry(
                messageId = messageId,
                threadId = threadId,
                state = DeliveryLedgerPolicy.State.PENDING,
                segmentIndex = segmentIndex,
                segmentCount = segmentCount,
                createdAt = now
            )
            val next = DeliveryLedgerPolicy.apply(base, to, now, reason)
            if (existing != null && next == existing) return@runCatching
            dao.upsertWorldState(
                WorldStateEntity(
                    id = DeliveryLedgerPolicy.rowId(messageId),
                    characterId = characterId,
                    threadId = threadId,
                    scope = DeliveryLedgerPolicy.SCOPE,
                    content = next.toJson(),
                    updatedAt = now
                )
            )
            prune(threadId)
        }
    }

    /**
     * 裁剪终态行。
     *
     * 只在**超出余量**时才做一遍删除，避免每条消息都跑一次全表读 + 批量删。
     * 未终结的行（`PENDING` / `FAILED`）永远保留。
     */
    private suspend fun prune(threadId: String) {
        val all = entries(threadId)
        if (all.size <= DeliveryLedgerPolicy.RETAIN_PER_THREAD + PRUNE_SLACK) return
        val keep = DeliveryLedgerPolicy.retainedIds(all)
        all.filterNot { it.messageId in keep }.forEach { stale ->
            dao.deleteWorldStateById(DeliveryLedgerPolicy.rowId(stale.messageId))
        }
    }

    private companion object {
        const val PRUNE_SLACK = 10
    }
}
