package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 设定提案（State Patch）：她身上那些**慢慢长出来**的东西，得攒够证据才算数。
 *
 * ## 它解决什么
 *
 * 别的系统都是"这一轮发生了什么"，而这个回答的是"**她是不是真的变了**"。
 * 没有它的话只有两条路：要么完全不变（她永远是最初那份人设），要么模型说什么就是什么
 * （一次对话就能给她安上新习惯、改掉关系定位）。两条都不对。
 *
 * 做法是：模型提的每条都先当**提案**，反复出现、跨了足够多的天、可信度够高，才落成 overlay。
 *
 * ## 四重门（全部要过）
 *
 * | 门 | 阈值 | 为什么 |
 * |---|---|---|
 * | 可信度 | ≥ [GATE_CONFIDENCE]（**重大变更** ≥ [GATE_CONFIDENCE_MAJOR]） | 一条只在对话里对得上一半的"新习惯"不该改人设 |
 * | 出现次数 | ≥ [GATE_MIN_TURNS] | 一次提起是随口一说 |
 * | 跨度天数 | ≥ [GATE_MIN_DAYS] | **同一天说十遍不算数**——要跨天 |
 * | 冷却 | 距上次生效 ≥ [GATE_COOLDOWN_MS] | 免得一次对话就把她改成另一个人 |
 *
 * 重大变更（[Kind.RELATIONSHIP]：关系定位）用更高的可信度门槛，且同样受冷却约束。
 *
 * ## 落成的是 overlay，不是改用户的人设
 *
 * 通过的提案变成一条 **overlay**，注入 system 时明确标成"慢慢长出来的"，
 * 与用户亲手写的原始人设分开。**原始人设永远不动**。
 */
object StatePatchPolicy {

    /** 存放的 world state scope（免 DB 迁移）。按**角色**存。 */
    const val SCOPE = "STATE_PATCH"

    fun rowId(characterId: String): String = "state-patch:$characterId"

    /** 四重门的阈值。 */
    const val GATE_CONFIDENCE = 0.82
    const val GATE_CONFIDENCE_MAJOR = 0.95
    const val GATE_MIN_TURNS = 3
    const val GATE_MIN_DAYS = 2
    const val GATE_COOLDOWN_MS = 72 * 3600_000L

    /** 待审队列最多留几条（超了丢最旧的）。 */
    const val MAX_PENDING = 12

    /** 生效的 overlay 最多几条（超了丢最旧的）。 */
    const val MAX_OVERLAYS = 8

    /** 提案太久没凑够证据就作废。 */
    const val EXPIRE_MS = 30 * 24 * 3600_000L

    const val MAX_TEXT_CHARS = 60

    /** 判定"同一条提案"的相似度阈值（字符二元组 Jaccard）。 */
    const val DUPLICATE_RATIO = 0.5

    /** 提案的性质。 */
    enum class Kind {
        /** 习惯：作息、口头禅、日常动作。 */
        HABIT,

        /** 偏好：喜欢/讨厌什么。 */
        PREFERENCE,

        /** 处境：工作、住处、家里的事。 */
        CIRCUMSTANCE,

        /** 关系定位——**重大变更**，门槛更高。 */
        RELATIONSHIP
    }

    /** 关系定位属于重大变更。 */
    fun isMajor(kind: Kind): Boolean = kind == Kind.RELATIONSHIP

    enum class Status { PENDING, APPLIED, REJECTED, EXPIRED }

    /** 一条提案。 */
    @Serializable
    data class Proposal(
        val id: String,
        val kind: Kind,
        val text: String,
        /** 历史最高可信度（[MemoryScorePolicy] 口径）。 */
        val confidence: Double,
        val evidenceCount: Int = 0,
        /** 被提起过多少个回合。 */
        val turnCount: Int = 0,
        /** 出现在哪几天。**跨天是硬要求**，所以这里存集合而不是计数。 */
        val dayKeys: Set<String> = emptySet(),
        val firstSeenAt: Long,
        val lastSeenAt: Long,
        val status: Status = Status.PENDING,
        val appliedAt: Long = 0L,
        val reason: String = ""
    )

    /** 一个角色的全部提案与生效记录。 */
    @Serializable
    data class Book(
        val proposals: List<Proposal> = emptyList(),
        /** 上次有提案生效的时刻（冷却门用）。 */
        val lastAppliedAt: Long = 0L
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): Book {
                if (raw.isNullOrBlank()) return Book()
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(Book())
            }
        }
    }

    /** 模型提出的一条候选。 */
    data class Candidate(val kind: Kind, val text: String, val confidence: Double)

    /** 模型输出里的一条：`PATCH[KIND]: 文本`（`[KIND]` 可省，默认 [Kind.CIRCUMSTANCE]）。 */
    fun parseCandidates(raw: String?): List<Candidate> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return text.lines().mapNotNull { line ->
            val trimmed = line.trim().removePrefix("-").trim()
            if (!trimmed.startsWith(PATCH, ignoreCase = true)) return@mapNotNull null
            val kind = KIND_IN_BRACKETS.find(trimmed)
                ?.groupValues?.get(1)
                ?.let { name -> Kind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
                ?: Kind.CIRCUMSTANCE
            val body = clean(trimmed.substringAfter(':', "").ifBlank { trimmed.substringAfter('：', "") })
            body.takeIf { it.isNotEmpty() }?.let { Candidate(kind, it, confidence = 0.0) }
        }
    }

    /**
     * 把这一轮提出的候选并进书里。
     *
     * 同一条（[similarity] 超过 [DUPLICATE_RATIO]）就**累积证据**：次数 +1、回合 +1、当天入集合、
     * 可信度取历史最高。新的一条则建为待审。
     *
     * [confidence] 由调用方按候选逐条给出（同频用落地性检查）。
     */
    fun observe(book: Book, candidates: List<Candidate>, now: Long, dayKey: String): Book {
        if (candidates.isEmpty()) return book
        val merged = book.proposals.toMutableList()
        candidates.forEach { candidate ->
            val index = merged.indexOfFirst {
                it.status == Status.PENDING && similarity(it.text, candidate.text) >= DUPLICATE_RATIO
            }
            if (index >= 0) {
                val old = merged[index]
                merged[index] = old.copy(
                    evidenceCount = old.evidenceCount + 1,
                    turnCount = old.turnCount + 1,
                    dayKeys = old.dayKeys + dayKey,
                    confidence = maxOf(old.confidence, candidate.confidence),
                    lastSeenAt = now
                )
            } else {
                merged += Proposal(
                    id = "patch:${now}:${merged.size}",
                    kind = candidate.kind,
                    text = clean(candidate.text),
                    confidence = candidate.confidence,
                    evidenceCount = 1,
                    turnCount = 1,
                    dayKeys = setOf(dayKey),
                    firstSeenAt = now,
                    lastSeenAt = now
                )
            }
        }
        // 待审队列封顶：丢最旧的待审（生效与作废的记录保留）。
        val pending = merged.filter { it.status == Status.PENDING }.sortedByDescending { it.lastSeenAt }
        val keptPending = pending.take(MAX_PENDING).toSet()
        return book.copy(
            proposals = merged.filter { it.status != Status.PENDING || it in keptPending }
                .sortedBy { it.firstSeenAt }
        )
    }

    /**
     * 这条提案现在能不能生效。`null` = 通过；否则是**拒绝原因**（人话，用于排查与界面）。
     */
    fun gate(proposal: Proposal, now: Long, lastAppliedAt: Long): String? {
        if (proposal.dayKeys.size < GATE_MIN_DAYS) {
            return "证据只跨了 ${proposal.dayKeys.size} 天（要 $GATE_MIN_DAYS 天）"
        }
        if (proposal.turnCount < GATE_MIN_TURNS) {
            return "只提起过 ${proposal.turnCount} 次（要 $GATE_MIN_TURNS 次）"
        }
        val required = if (isMajor(proposal.kind)) GATE_CONFIDENCE_MAJOR else GATE_CONFIDENCE
        if (proposal.confidence < required) {
            return "可信度 ${proposal.confidence} 不到 $required"
        }
        if (lastAppliedAt > 0L && now - lastAppliedAt < GATE_COOLDOWN_MS) {
            return "距上次生效还不到 ${GATE_COOLDOWN_MS / 3600_000} 小时"
        }
        return null
    }

    /**
     * 结算：通过的转 [Status.APPLIED]，太久没凑够的转 [Status.EXPIRED]，其余保持待审。
     *
     * 已生效与已作废的记录**不再改动**。
     */
    fun settle(book: Book, now: Long): Book {
        var lastApplied = book.lastAppliedAt
        val settled = book.proposals.map { proposal ->
            if (proposal.status != Status.PENDING) return@map proposal
            val reason = gate(proposal, now, lastApplied)
            when {
                reason == null -> {
                    lastApplied = now
                    proposal.copy(status = Status.APPLIED, appliedAt = now, reason = "")
                }
                now - proposal.firstSeenAt > EXPIRE_MS ->
                    proposal.copy(status = Status.EXPIRED, reason = "攒了一个月也没凑够：$reason")
                else -> proposal
            }
        }
        return book.copy(proposals = settled, lastAppliedAt = lastApplied)
    }

    /** 当前生效的 overlay（**新的在前**），最多 [MAX_OVERLAYS] 条。 */
    fun overlays(book: Book): List<String> = book.proposals
        .filter { it.status == Status.APPLIED }
        .sortedByDescending { it.appliedAt }
        .take(MAX_OVERLAYS)
        .map { it.text }

    /**
     * 注入 system 的那一块；没有 overlay 时返回空串。
     *
     * 抬头必须写清"这是慢慢长出来的、不是原始设定"——否则模型会把它当成人设的全部，
     * 甚至当作需要解释的东西去提。
     */
    fun render(book: Book): String {
        val items = overlays(book)
        if (items.isEmpty()) return ""
        return buildString {
            append("她身上这些是**慢慢长出来的**（不是一开始的设定，也不必特意解释或提起）：")
            items.forEach { append("\n- "); append(it) }
        }
    }

    /** 字符二元组 Jaccard，与提案去重口径一致。 */
    fun similarity(a: String, b: String): Double {
        val setA = bigrams(a)
        val setB = bigrams(b)
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val common = setA.count { it in setB }
        return common.toDouble() / (setA.size + setB.size - common).toDouble()
    }

    private fun clean(raw: String): String = raw.trim().replace(WHITESPACE, " ").take(MAX_TEXT_CHARS)

    private fun bigrams(text: String): Set<String> {
        val normalized = text.lowercase().filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) return emptySet()
        if (normalized.length == 1) return setOf(normalized)
        return (0..normalized.length - 2).map { normalized.substring(it, it + 2) }.toSet()
    }

    private const val PATCH = "PATCH"
    private val KIND_IN_BRACKETS = Regex("^PATCH\\s*\\[\\s*([A-Za-z]+)\\s*]", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")
}
