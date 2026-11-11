package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.LongArcPolicy
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 长线叙事的读写与复审。
 *
 * 规则全在 [LongArcPolicy] 里（纯逻辑、有单测），这里只做三件事：
 * 读状态、把有效回合累加上去、攒够了请一次"行动许可"并投影成 system 里的一块。
 *
 * ## 什么时候复审
 *
 * 由回合收尾的后台协程调用 [reviewIfDue]，它自己判"该不该跑"：[LongArcPolicy.dueForReview]
 * （权重到阈值、且距上次至少又累计了一个增量）加一条失败退避。
 *
 * ## 累加是"一回合一分"
 *
 * 私聊一个有效回合 1 分、群聊 0.5 分（写死在 [LongArcPolicy] 里）。
 * **不是按消息条数**：连发十条只是同一轮对话，不该让她那条线涨得更快。
 */
@Singleton
class LongArcCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val cipher: CompanionSecretCipher
) {

    /** 每个角色一把锁：累加与复审都会写同一份状态，不加锁会丢更新。 */
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val lastAttemptAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun stateFor(characterId: String): LongArcPolicy.State = runCatching {
        dao.worldStates(characterId, LongArcPolicy.SCOPE)
            .maxByOrNull { it.updatedAt }
            ?.content
            ?.let(LongArcPolicy.State::fromJson)
            ?: LongArcPolicy.State()
    }.getOrDefault(LongArcPolicy.State())

    /**
     * 记一个有效回合。
     *
     * **必须等待锁**（不能用"忙就跳过"）：跳过等于白丢一分，而且丢的过程完全无声。
     */
    suspend fun observeTurn(characterId: String, source: LongArcPolicy.Source) {
        val lock = locks.getOrPut(characterId) { Mutex() }
        lock.withLock {
            val current = stateFor(characterId)
            val next = LongArcPolicy.observe(current, source)
            if (next != current) persist(characterId, next, System.currentTimeMillis())
        }
    }

    /** 投影成 system 里的一小块；没到那条线时返回空串（调用方据此不 append 任何东西）。 */
    suspend fun blockFor(characterId: String): String = LongArcPolicy.render(stateFor(characterId))

    /**
     * 该复审就审一次。返回是否真的跑了模型。
     *
     * 抢不到锁就跳过（下一回合还会再来），不阻塞回合收尾。
     */
    suspend fun reviewIfDue(characterId: String, threadId: String?, zone: ZoneId): Boolean {
        val lock = locks.getOrPut(characterId) { Mutex() }
        if (lock.isLocked) return false
        return lock.withLock {
            val state = stateFor(characterId)
            if (!LongArcPolicy.dueForReview(state)) return@withLock false
            val now = System.currentTimeMillis()
            if (now - (lastAttemptAt[characterId] ?: 0L) < RETRY_BACKOFF_MS) return@withLock false
            val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL) ?: return@withLock false
            if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return@withLock false
            val character = dao.character(characterId) ?: return@withLock false
            val recent = threadId?.let { id ->
                runCatching { dao.recentMessages(id, EXCERPT_MESSAGES) }.getOrDefault(emptyList())
            }.orEmpty()
            lastAttemptAt[characterId] = now
            val raw = runCatching {
                client.chat(
                    profile = profile,
                    apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey)),
                    systemPrompt = reviewSystem(character.name),
                    userPrompt = reviewUser(state, recent)
                ).getOrNull()
            }.getOrNull() ?: return@withLock false

            val review = LongArcPolicy.parseReview(raw)
            val next = LongArcPolicy.onReviewed(state, review.catalyst, review.firstExpression, review.branches, now)
            persist(characterId, next, now)
            true
        }
    }

    private suspend fun persist(characterId: String, state: LongArcPolicy.State, now: Long) {
        runCatching {
            dao.upsertWorldState(
                WorldStateEntity(
                    id = LongArcPolicy.rowId(characterId),
                    characterId = characterId,
                    threadId = null,
                    scope = LongArcPolicy.SCOPE,
                    content = state.toJson(),
                    updatedAt = now
                )
            )
        }
    }

    private fun reviewSystem(characterName: String): String = buildString {
        appendLine("你在判断一段关系里有没有一条**正在生长的长线**（一件攒了很久、还没走完的事）。")
        appendLine("第一行只输出一个词：DORMANT（还没有）／PRIME（在酝酿）／ACTIVATE（已经点着，可以往前走了）")
        appendLine("如果判 PRIME 或 ACTIVATE，**必须**再给出至少一行 FIRST: 和至少一行 BRANCH:，")
        appendLine("否则系统会整份拒绝、保持原状态。")
        appendLine("FIRST: 她**具体**做了什么或说了什么（不超过 ${LongArcPolicy.MAX_EXPRESSION_CHARS} 字；")
        appendLine("写具体行为，不要写\"关系更近了\"这种抽象感想）")
        appendLine("BRANCH: 这条线接下来可能的一个走向（不超过 ${LongArcPolicy.MAX_BRANCH_CHARS} 字，最多 ${LongArcPolicy.MAX_BRANCHES} 条）")
        appendLine("没有这样一条线就只输出 DORMANT 一行。")
        appendLine("只说得出抽象的\"有感觉\"而说不出具体行为时，也输出 DORMANT。")
        appendLine("禁止输出解释、markdown、代码块、多余空行。")
        appendLine("只依据给出的对话，**不要编造**没发生过的事。")
        appendLine("角色名：${characterName.trim()}")
    }.trim()

    private fun reviewUser(state: LongArcPolicy.State, recent: List<MessageEntity>): String = buildString {
        appendLine("当前累计分量：${state.weight}（私聊一个有效回合 1 分、群聊 0.5 分）")
        appendLine("当前状态：${state.catalyst.name}")
        if (state.firstExpression.isNotBlank()) {
            appendLine("已经记下的那条线：${state.firstExpression}")
        }
        val excerpt = recent.filter { it.body.isNotBlank() }.takeLast(EXCERPT_MESSAGES)
        if (excerpt.isNotEmpty()) {
            appendLine("最近对话：")
            excerpt.forEach { message ->
                val speaker = if (message.fromUser) "User" else "她"
                appendLine("- $speaker: ${message.body.trim().replace(WHITESPACE, " ").take(EXCERPT_CHARS)}")
            }
        }
    }.trim()

    private companion object {
        const val RETRY_BACKOFF_MS = 30 * 60_000L
        const val EXCERPT_MESSAGES = 24
        const val EXCERPT_CHARS = 120
        val WHITESPACE = Regex("\\s+")
    }
}
