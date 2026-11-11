package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.StatePatchPolicy
import com.aster.companion.lite.memory.MemoryRollupPolicy
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 设定提案的读写与结算。
 *
 * 规则全在 [StatePatchPolicy] 里（纯逻辑、有单测），这里只做三件事：
 * 从压缩的原始输出里读提案、累积证据并**结算**、把生效的 overlay 投影成 system 里的一块。
 *
 * ## 为什么要单独一个 Coordinator
 *
 * 提案的"证据"就是压缩那一轮看到的对话，所以**顺路**从它那里取（不额外跑模型）。
 * 但累积与结算是跨天的状态，必须有个地方持久化——就是这里。
 *
 * ## 它不改用户的人设
 *
 * 生效的是 **overlay**（[StatePatchPolicy.render]），注入时明确标成"慢慢长出来的"。
 * 用户亲手写的人设永远不动。
 */
@Singleton
class StatePatchCoordinator @Inject constructor(
    private val dao: CompanionDao
) {

    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    suspend fun bookFor(characterId: String): StatePatchPolicy.Book = runCatching {
        dao.worldStates(characterId, StatePatchPolicy.SCOPE)
            .maxByOrNull { it.updatedAt }
            ?.content
            ?.let(StatePatchPolicy.Book::fromJson)
            ?: StatePatchPolicy.Book()
    }.getOrDefault(StatePatchPolicy.Book())

    /**
     * 从一次压缩的原始输出里读提案：累积证据 → 结算 → 落库。
     *
     * [source] 是产出这些提案的那段对话：每条候选的可信度用**落地性检查**
     * （[MemoryRollupPolicy.groundingConfidence]，与记忆同一口径）算出来——
     * 一句在对话里对不上任何词面依据的"新习惯"，可信度自然低，也就过不了第一道门。
     *
     * 返回书有没有变化。
     */
    suspend fun ingest(characterId: String, raw: String?, source: String, now: Long): Boolean {
        val candidates = StatePatchPolicy.parseCandidates(raw)
        if (candidates.isEmpty()) return false
        val lock = locks.getOrPut(characterId) { Mutex() }
        return lock.withLock {
            val book = bookFor(characterId)
            val scored = candidates.map {
                it.copy(confidence = MemoryRollupPolicy.groundingConfidence(it.text, source))
            }
            val settled = StatePatchPolicy.settle(
                StatePatchPolicy.observe(book, scored, now, dayKeyOf(now)),
                now
            )
            if (settled == book) return@withLock false
            persist(characterId, settled, now)
            true
        }
    }

    /** 生效的 overlay 块；没有时返回空串（调用方据此不 append 任何东西）。 */
    suspend fun overlayBlock(characterId: String): String = StatePatchPolicy.render(bookFor(characterId))

    /** 待审提案的摘要，给状态卡与排查用：`(待审条数, 生效条数)`。 */
    suspend fun counts(characterId: String): Pair<Int, Int> {
        val book = bookFor(characterId)
        return book.proposals.count { it.status == StatePatchPolicy.Status.PENDING } to
            book.proposals.count { it.status == StatePatchPolicy.Status.APPLIED }
    }

    private suspend fun persist(characterId: String, book: StatePatchPolicy.Book, now: Long) {
        runCatching {
            dao.upsertWorldState(
                WorldStateEntity(
                    id = StatePatchPolicy.rowId(characterId),
                    characterId = characterId,
                    threadId = null,
                    scope = StatePatchPolicy.SCOPE,
                    content = book.toJson(),
                    updatedAt = now
                )
            )
        }
    }

    private fun dayKeyOf(now: Long): String =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
