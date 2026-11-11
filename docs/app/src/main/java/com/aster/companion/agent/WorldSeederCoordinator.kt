package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.WorldSeedingPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 世界事件播种的读写。
 *
 * 规则全在 [WorldSeedingPolicy] 里（纯逻辑、有单测），这里只做三件事：
 * 读日志、到点请模型播一件、把还没用过的事件**认领**成 system 里的一块。
 *
 * ## 「认领」是有副作用的，所以名字挑明了
 *
 * [claimForInjection] 在返回文本的同时把这几件**标记为已注入**。上游的做法是
 * claim-then-append（先占住再写进正文），同频的正文就是 system 那一块，所以等价。
 * 副作用的必要性与 [WorldSeedingPolicy.visibleForInjection] 的注释一致：注入与标记必须
 * 看到同一份名单，否则会出现"注入了但没标记 → 下一轮又注一遍"。
 *
 * ## 切断自馈环
 *
 * 请求里**只有切面与用户这边的真实上下文**，绝不包含它自己上一批产出
 * （见 [WorldSeedingPolicy.seedingInput]）——上游作者实测过"同一件事换四种说法连排四次"。
 */
@Singleton
class WorldSeederCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val cipher: CompanionSecretCipher
) {

    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val lastAttemptAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun logFor(characterId: String): WorldSeedingPolicy.Log = runCatching {
        dao.worldStates(characterId, WorldSeedingPolicy.SCOPE)
            .maxByOrNull { it.updatedAt }
            ?.content
            ?.let(WorldSeedingPolicy.Log::fromJson)
            ?: WorldSeedingPolicy.Log()
    }.getOrDefault(WorldSeedingPolicy.Log())

    /**
     * 认领这次要注入的事件，并在返回文本之前把它们标记为已注入。
     *
     * 没有可用事件时返回空串，且**不做任何写入**。
     */
    suspend fun claimForInjection(characterId: String, now: Long): String {
        val lock = locks.getOrPut(characterId) { Mutex() }
        return lock.withLock {
            val log = logFor(characterId)
            val claimed = WorldSeedingPolicy.visibleForInjection(log, now)
            if (claimed.isEmpty()) return@withLock ""
            val text = WorldSeedingPolicy.render(log, now)
            if (text.isBlank()) return@withLock ""
            persist(characterId, WorldSeedingPolicy.markInjected(log, claimed.map { it.id }.toSet(), now), now)
            text
        }
    }

    /**
     * 到点就播一件。返回是否真的落了库。
     *
     * 调用方负责套后台 LLM 闸（[com.aster.companion.lite.LiteBackgroundLlmGate]）——
     * 它要跑模型，不能跟聊天抢并发槽。
     */
    suspend fun seedIfDue(
        characterId: String,
        threadId: String?,
        now: Long,
        dayKey: String,
        sampler: WorldSeedingPolicy.Sampler = WorldSeedingPolicy.Sampler.random
    ): Boolean {
        val lock = locks.getOrPut(characterId) { Mutex() }
        if (lock.isLocked) return false
        return lock.withLock {
            val log = logFor(characterId)
            if (!WorldSeedingPolicy.shouldSeed(now, log.lastSeededAt, sampler)) return@withLock false
            if (!WorldSeedingPolicy.withinCaps(log, now, dayKey)) return@withLock false
            if (now - (lastAttemptAt[characterId] ?: 0L) < RETRY_BACKOFF_MS) return@withLock false
            val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL) ?: return@withLock false
            if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return@withLock false
            val facet = WorldSeedingPolicy.facetFor(characterId, now / WorldSeedingPolicy.SLOT_MS)
            val recent = threadId?.let { id ->
                runCatching { dao.recentMessages(id, CONTEXT_MESSAGES) }.getOrDefault(emptyList())
            }.orEmpty()
            lastAttemptAt[characterId] = now
            val raw = runCatching {
                client.chat(
                    profile = profile,
                    apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey)),
                    systemPrompt = seedingSystem(),
                    userPrompt = WorldSeedingPolicy.seedingInput(facet, userContext(recent))
                ).getOrNull()
            }.getOrNull()

            val candidate = WorldSeedingPolicy.parseEvent(
                raw = raw,
                facet = facet,
                id = "world:$characterId:${now}",
                now = now
            )
            // 解析不出来、或与最近几件太像：**也盖上"刚播过"的时刻**，
            // 否则下一拍会立刻再问一次模型（那一拍多半还是同样的结果）。
            if (candidate == null || WorldSeedingPolicy.isDuplicate(candidate.title, log.events.takeLast(DEDUP_WINDOW))) {
                persist(characterId, log.copy(lastSeededAt = now), now)
                return@withLock false
            }
            val today = if (log.dayKey == dayKey) log.todayCount else 0
            val next = WorldSeedingPolicy.prune(
                log.copy(
                    events = log.events + candidate,
                    lastSeededAt = now,
                    todayCount = today + 1,
                    dayKey = dayKey
                ),
                now
            )
            persist(characterId, next, now)
            true
        }
    }

    private suspend fun persist(characterId: String, log: WorldSeedingPolicy.Log, now: Long) {
        runCatching {
            dao.upsertWorldState(
                WorldStateEntity(
                    id = WorldSeedingPolicy.rowId(characterId),
                    characterId = characterId,
                    threadId = null,
                    scope = WorldSeedingPolicy.SCOPE,
                    content = log.toJson(),
                    updatedAt = now
                )
            )
        }
    }

    /** 用户这边的真实上下文：**只取用户说的话**，不取她自己的输出。 */
    private fun userContext(recent: List<MessageEntity>): String = recent
        .filter { it.fromUser && it.body.isNotBlank() }
        .takeLast(CONTEXT_MESSAGES)
        .joinToString("\n") { "- " + it.body.trim().replace(WHITESPACE, " ").take(CONTEXT_CHARS) }

    private fun seedingSystem(): String = buildString {
        appendLine("你在编一件**外面刚刚发生的小事**——它属于这个世界，不属于你们的对话。")
        appendLine("要求：")
        appendLine("1. 只输出两行，格式严格是：")
        appendLine("TITLE: 一句话说清发生了什么（不超过 ${WorldSeedingPolicy.MAX_TITLE_CHARS} 字）")
        appendLine("DETAIL: 一句补充（不超过 ${WorldSeedingPolicy.MAX_DETAIL_CHARS} 字，可以省略这一行）")
        appendLine("2. 事情要**小、具体、可信**：修路、停电、天气变化、邻居动静、店里换了什么。")
        appendLine("   不要大新闻、不要戏剧化、不要和角色或用户有关的私人事件。")
        appendLine("3. 不要提到任何人的名字，不要提到你们在聊什么。")
        appendLine("4. 禁止输出解释、markdown、代码块、多余空行。")
        appendLine("5. 不要重复常见的套话（\"今天天气不错\"这类不算一件事）。")
    }.trim()

    private companion object {
        const val RETRY_BACKOFF_MS = 15 * 60_000L
        const val CONTEXT_MESSAGES = 8
        const val CONTEXT_CHARS = 80
        /** 去重只跟最近几件比：更早的重复不重要，而且窗口太大反而容易误杀。 */
        const val DEDUP_WINDOW = 6
        val WHITESPACE = Regex("\\s+")
    }
}
