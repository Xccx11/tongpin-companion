package com.aster.companion.lite.memory

import com.aster.companion.agent.AgentTurnCoordinator
import com.aster.companion.agent.ImageGenerationCoordinator
import com.aster.companion.agent.OpenAiCompatibleClient
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionRepository
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.MemoryEntity
import com.aster.companion.data.MemoryEvidenceEntity
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.ConversationType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * L1 压缩记忆（记忆分册 §4.2）。
 *
 * 触发：线程自上次压缩后新增 ≥10 条；或线程 Idle（由调度器调用）。
 * 一次短 chat → 0–3 条事实 → 与既有 L2 去重后写库；输出 NONE 什么都不写。
 * 出图中跳过；同一线程同一时刻只跑一次；失败重试 1 次后放弃。
 *
 * ## 游标（这一版的重点）
 *
 * 两个数各管一件事，**都落库**（存进 world state 的 [CURSOR_SCOPE]，免 DB 迁移，
 * 与 `LIFE_PLAN` 同一路子）：
 *
 * - **水位 `watermark`**：已经压缩到的消息时间。只用来筛"哪些是新的"；
 * - **运行时间 `runAt`**：上次压缩**跑过**的时间。只用来算间隔闸。
 *
 * 两者以前是同一样东西、而且**只活在内存 map 里**——进程一重启就全部归零，
 * 于是同一段对话会被反复压缩，跨重启的"连续前缀"纪律也无从谈起。
 *
 * ## 连续前缀
 *
 * 单次最多压 [MAX_BATCH] 条，水位**只推到最后一条真的处理过的消息**。
 * 以前是"整段 fresh 一次吃完、水位直接推到 `now`"：候选窗口（[MESSAGE_WINDOW] 条）装不下的
 * 消息会被永久跳过，`now` 还会连带跳过这一批之后所有没压的部分。
 */
@Singleton
class MemoryRollupCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val repository: CompanionRepository,
    private val client: OpenAiCompatibleClient,
    private val cipher: CompanionSecretCipher,
    private val imageGeneration: ImageGenerationCoordinator,
    /** 设定提案：从同一份原始输出里读 `PATCH:` 行，顺路累积证据并结算（不额外跑模型）。 */
    private val statePatch: com.aster.companion.agent.StatePatchCoordinator
) {

    /** 一次压缩游标：谁在什么时候压到了哪条消息。 */
    private data class Cursor(val runAt: Long, val watermark: Long)

    private val empty = Cursor(0L, 0L)

    /** threadId → 上次压缩**运行**时间。只是缓存，权威副本在库里。 */
    private val lastRunAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private val trigger = MemoryRollupPolicy.Trigger(
        minNewMessages = 6,
        minIntervalMs = 3 * 60_000L
    )

    /** 清空会话后重置压缩游标，避免旧窗口继续当新记忆源（库里的那份由清库链路一并删掉）。 */
    fun resetForThread(threadId: String) {
        lastRunAt.remove(threadId)
    }

    /** 返回本次写入/更新的事实条数（0 表示没压、没新事实或跳过）。 */
    suspend fun rollUp(threadId: String, now: Long = System.currentTimeMillis()): Int {
        if (imageGeneration.isImageLaneBusy()) return 0
        val lock = locks.getOrPut(threadId) { Mutex() }
        if (lock.isLocked) return 0
        return lock.withLock {
            val thread = dao.thread(threadId) ?: return@withLock 0
            if (thread.type != ConversationType.PRIVATE) return@withLock 0
            val characterId = dao.participantIds(threadId).firstOrNull() ?: return@withLock 0
            val window = dao.recentMessages(threadId, MESSAGE_WINDOW).asReversed()
                .filter { it.body.isNotBlank() && !it.id.startsWith("stream:") }
            if (window.isEmpty()) return@withLock 0
            val stored = readCursor(threadId)
            val lastRun = maxOf(stored.runAt, lastRunAt[threadId] ?: 0L)
            val fresh = window.filter { it.createdAt > stored.watermark }
            if (!MemoryRollupPolicy.shouldTrigger(fresh.size, now - lastRun, trigger)) return@withLock 0
            // **只吃连续前缀**：水位只推到最后一条真的处理过的消息（见类注释）。
            val batch = fresh.take(MAX_BATCH)
            val character = dao.character(characterId) ?: return@withLock 0
            val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL) ?: return@withLock 0
            if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return@withLock 0
            val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
            val source = batch.joinToString(" ") { it.body }
            val (raw, drafts) = requestDrafts(
                profile = profile,
                apiKey = apiKey,
                system = rollupSystem(),
                user = rollupUser(character.name, batch)
            ) ?: return@withLock 0
            // 无论有没有事实，都推进游标：这一批不重复压。
            writeCursor(threadId, characterId, Cursor(runAt = now, watermark = batch.last().createdAt), now)
            // 设定提案：**即使这次没提炼出事实**，原始输出里也可能有 `PATCH:` 行。
            // 它用的证据就是这一批对话，所以顺路取、不额外跑模型。
            runCatching { statePatch.ingest(characterId, raw, source, now) }
            if (drafts.isEmpty()) return@withLock 0
            persist(characterId, threadId, drafts, batch = batch, now = now)
            drafts.size
        }
    }

    /** 读库里的权威游标；读不到（或格式不对）当作从没压过。 */
    private suspend fun readCursor(threadId: String): Cursor = runCatching {
        val raw = dao.worldStatesByThread(threadId, CURSOR_SCOPE)
            .maxByOrNull { it.updatedAt }?.content
        val parts = raw?.split('|')
        if (parts == null || parts.size < 2) empty
        else Cursor(
            runAt = parts[0].trim().toLongOrNull() ?: 0L,
            watermark = parts[1].trim().toLongOrNull() ?: 0L
        )
    }.getOrDefault(empty)

    /** 写权威游标：内存 + 库（库里那份才是重启后能恢复的那份）。 */
    private suspend fun writeCursor(threadId: String, characterId: String, cursor: Cursor, now: Long) {
        lastRunAt[threadId] = cursor.runAt
        runCatching {
            dao.upsertWorldState(
                WorldStateEntity(
                    id = "memory-rollup:$threadId",
                    characterId = characterId,
                    threadId = threadId,
                    scope = CURSOR_SCOPE,
                    content = "${cursor.runAt}|${cursor.watermark}",
                    updatedAt = now
                )
            )
        }
    }

    private suspend fun requestDrafts(
        profile: com.aster.companion.data.ModelProfileEntity,
        apiKey: String,
        system: String,
        user: String
    ): Pair<String, List<MemoryDraft>>? {
        repeat(MAX_ATTEMPTS) {
            val raw = client.chat(profile, apiKey, system, user).getOrNull() ?: return null
            MemoryRollupPolicy.parse(raw)?.let { return raw to it }
        }
        return null
    }

    private suspend fun persist(
        characterId: String,
        threadId: String,
        drafts: List<MemoryDraft>,
        /** 产出这批事实的原始对话：算落地性可信度、判证据角色都靠它。 */
        batch: List<MessageEntity>,
        now: Long
    ) {
        val source = batch.joinToString(" ") { it.body }
        val clauses = batch.map {
            KnowledgeEvidencePolicy.Clause(text = it.body, fromUser = it.fromUser, messageId = it.id)
        }
        val existing = dao.characterMemories(characterId, MEMORY_LIMIT + OVERFLOW_SLACK)
        val existingContents = existing.map { it.content }
        drafts.forEach { draft ->
            val matchedContent = MemoryRollupPolicy.duplicateOf(draft.content, existingContents)
            val duplicate = matchedContent?.let { matched -> existing.firstOrNull { it.content == matched } }
            val memoryId = repository.saveMemory(
                id = duplicate?.id,
                characterId = characterId,
                // Bind to this thread so "clear chat" / new conversation can forget it.
                // Manual "让她记住" still uses threadId=null and stays global.
                threadId = threadId,
                kind = draft.kind,
                content = draft.content,
                blocked = false,
                // 重要度**显式落列**（以前是按 kind 现推）：落列之后才谈得上单独调它。
                importance = MemoryBudgetPolicy.importanceOf(draft.kind),
                // 可信度 = 这条事实在来源对话里能不能找到词面依据（见 groundingConfidence）。
                confidence = MemoryRollupPolicy.groundingConfidence(draft.content, source)
            )
            recordEvidence(characterId, memoryId, draft.content, clauses, isExisting = duplicate != null, now = now)
        }
        val after = dao.characterMemories(characterId, MEMORY_LIMIT + OVERFLOW_SLACK)
        if (after.size > MEMORY_LIMIT) {
            val keep = MemoryBudgetPolicy.trimToLimit(after.map { it.toLiteFact() }, MemoryBudgetPolicy.default)
                .map { it.id }
                .toSet()
            after.filterNot { it.id in keep }.forEach {
                // 事实删掉，挂在上面的证据角色也要一起删——否则会留下指向不存在事实的孤儿行。
                dao.deleteMemoryEvidence(it.id)
                dao.deleteMemory(it.id)
            }
        }
    }

    /**
     * 记下这条事实的**证据角色**。
     *
     * - 新事实：按原话判（用户说的 → `REPORTED`，她说的 → `OBSERVED`，判不出 → `UNCLASSIFIED`）；
     * - 已经在库里的事实（又被提到了）：拿这一批原话试着**升格为已确认**。上游规则是
     *   "用户先提出、之后她回应"——所以要把库里那条"他说的"原话摆在这一批之前，
     *   否则跨批次的"先提出、后回应"永远看不出来。升不上去就保持原判，**绝不因为又提了一次就当确认**。
     *
     * 主键带 sourceMessageId：一条事实可以攒下多条证据（不同来源、不同时刻各一行）。
     */
    private suspend fun recordEvidence(
        characterId: String,
        memoryId: String,
        fact: String,
        clauses: List<KnowledgeEvidencePolicy.Clause>,
        isExisting: Boolean,
        now: Long
    ) {
        runCatching {
            val verdict = KnowledgeEvidencePolicy.classify(fact, clauses)
            val mode = if (!isExisting) {
                verdict.mode
            } else {
                val stored = dao.memoryEvidence(memoryId).maxByOrNull { it.createdAt }
                val previous = stored?.let { KnowledgeEvidencePolicy.parse(it.mode) }
                    ?: KnowledgeEvidencePolicy.Mode.UNCLASSIFIED
                val ordered = if (previous == KnowledgeEvidencePolicy.Mode.REPORTED && stored != null) {
                    listOf(
                        KnowledgeEvidencePolicy.Clause(
                            text = stored.clauseText,
                            fromUser = true,
                            messageId = stored.sourceMessageId
                        )
                    ) + clauses
                } else {
                    clauses
                }
                KnowledgeEvidencePolicy.promote(previous, fact, ordered)
            }
            dao.upsertMemoryEvidence(
                MemoryEvidenceEntity(
                    id = "evidence:$memoryId:${verdict.sourceMessageId ?: "unknown"}",
                    memoryId = memoryId,
                    mode = mode.name,
                    // 认知主体一直是这个角色自己；"来源"由 mode + sourceMessageId 表达。
                    holderCharacterId = characterId,
                    clauseText = verdict.clauseText.take(CLAUSE_CHARS),
                    sourceMessageId = verdict.sourceMessageId,
                    createdAt = now
                )
            )
        }
    }

    private fun rollupSystem(): String = buildString {
        appendLine("只提炼「以后还用得上的事实」。最多 ${MemoryRollupPolicy.MAX_FACTS_PER_ROLLUP} 条，每条 ≤${MemoryRollupPolicy.MAX_FACT_CHARS} 字中文。")
        appendLine("每行格式：TYPE: 事实；TYPE 只能是 PREFERENCE、PLAN、EVENT、RELATIONSHIP。")
        appendLine("禁止：复述整段聊天、日记体、编造用户没说过的事、提系统/模型/提示词。")
        appendLine("无新事实则只输出 ${MemoryRollupPolicy.NONE}")
        appendLine("另外：如果出现了她**新长出来的**长期特征（新习惯、新偏好、处境变化、关系定位变化），")
        appendLine("可以另起一行写：PATCH[HABIT|PREFERENCE|CIRCUMSTANCE|RELATIONSHIP]: 一句话；没有就不写。")
        appendLine("PATCH 行会被**单独审核**（要反复出现、跨天、可信度够高才会生效），所以只写真正长期的东西，")
        appendLine("不要写一次性的琐事，也不要把它们再写进上面的事实行。")
    }.trim()

    private fun rollupUser(characterName: String, messages: List<MessageEntity>): String = buildString {
        appendLine("角色：${characterName.trim()}")
        appendLine("最近对话摘录：")
        messages.forEach { message ->
            val speaker = if (message.fromUser) "User" else characterName.trim().ifBlank { "角色" }
            appendLine("- $speaker: ${message.body.trim().replace(WHITESPACE, " ").take(EXCERPT_CHARS)}")
        }
    }.trim()

    private fun MemoryEntity.toLiteFact() = LiteMemoryFact.stored(
        id = id,
        kind = kind,
        content = content,
        updatedAt = updatedAt,
        blockedFromMention = blockedFromMention,
        importance = importance,
        confidence = confidence
    )

    private companion object {
        /** 压缩游标存放的 world state scope（免 DB 迁移）。 */
        const val CURSOR_SCOPE = "MEMORY_ROLLUP"

        /**
         * 取多少条消息当候选。
         *
         * 刻意比单次批量（[MAX_BATCH]）大一截：留出余量才判断得出"水位之后还有没有没压过的"，
         * 窗口刚好等于批量时永远看不出积压。
         */
        const val MESSAGE_WINDOW = 60

        /** 单次最多压多少条（连续前缀的长度上限）。 */
        const val MAX_BATCH = 24

        /** 逐字引用（证据原话）的截断长度。 */
        const val CLAUSE_CHARS = 120

        const val MAX_ATTEMPTS = 2
        const val MEMORY_LIMIT = 80
        const val OVERFLOW_SLACK = 40
        const val EXCERPT_CHARS = 60
        val WHITESPACE = Regex("\\s+")
    }
}
