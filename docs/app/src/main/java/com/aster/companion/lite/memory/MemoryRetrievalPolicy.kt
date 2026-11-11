package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind
import com.aster.companion.domain.MemoryScorePolicy

/** L2 里的一条事实（与 MemoryEntity 解耦，方便纯逻辑单测）。 */
data class LiteMemoryFact(
    val id: String,
    val kind: MemoryKind,
    val content: String,
    val updatedAt: Long,
    val importance: Int = MemoryBudgetPolicy.importanceOf(kind),
    /** 用户点过「不再提及」。 */
    val blockedFromMention: Boolean = false,
    /** 这条事实有多可信 0..1。`0`（=[MemoryScorePolicy.UNKNOWN_CONFIDENCE]）= 库里没标过。 */
    val confidence: Double = MemoryScorePolicy.UNKNOWN_CONFIDENCE,
    /**
     * 证据角色（[KnowledgeEvidencePolicy.Mode] 的名字）。
     *
     * 空串 = **没有证据行**（老数据、或用户手动「让她记住」）→ 注入时**不加限定语**。
     */
    val evidenceMode: String = ""
) {
    companion object {
        /**
         * 从**库里的列**造一条事实。
         *
         * `importance = 0` / `confidence = 0` 一律解释为"未标注"，退回加列之前的行为
         * （按 kind 推重要度、可信度取中性值），所以老数据读进来语义完全不变。
         *
         * 两个映射写在这里而不是各调用点，是因为它必须只有一处——分散写迟早会漂移。
         */
        fun stored(
            id: String,
            kind: MemoryKind,
            content: String,
            updatedAt: Long,
            blockedFromMention: Boolean,
            importance: Int,
            confidence: Double,
            evidenceMode: String = ""
        ) = LiteMemoryFact(
            id = id,
            kind = kind,
            content = content,
            updatedAt = updatedAt,
            importance = if (importance > 0) {
                importance.coerceAtMost(MemoryScorePolicy.MAX_IMPORTANCE)
            } else {
                MemoryBudgetPolicy.importanceOf(kind)
            },
            blockedFromMention = blockedFromMention,
            confidence = if (confidence > 0.0) {
                confidence.coerceIn(0.0, 1.0)
            } else {
                MemoryScorePolicy.UNKNOWN_CONFIDENCE
            },
            evidenceMode = evidenceMode.trim()
        )
    }
}

/**
 * 无 Embedding 的 Top-K 检索（记忆分册 §4.3）。
 *
 * 排序走 [MemoryScorePolicy] 的三项加权：关键词命中（相似度）是主信号，同档之内再由
 * 重要度与新近度决定先后——不再是"重要度一票否决时间"的三级字典序。
 * 只取 K 条、单条截断、整段不超过字符预算。
 */
object MemoryRetrievalPolicy {

    fun select(
        facts: List<LiteMemoryFact>,
        userText: String = "",
        budget: MemoryBudget = MemoryBudgetPolicy.default,
        /** 打分要用新近度；时钟由调用方传，policy 自己不读时钟（便于单测）。 */
        now: Long = System.currentTimeMillis()
    ): List<String> {
        val keywords = keywordsOf(userText)
        val ranked = facts
            .filter { !it.blockedFromMention && MemoryBudgetPolicy.isInjectable(it.kind) }
            .mapNotNull { fact ->
                val content = fact.content.trim().replace(WHITESPACE, " ").take(budget.perFactChars)
                content.takeIf { it.isNotEmpty() }?.let { fact to it }
            }
            .sortedWith(
                compareByDescending<Pair<LiteMemoryFact, String>> { (fact, content) ->
                    MemoryScorePolicy.score(
                        similarity = MemoryScorePolicy.keywordSimilarity(matches(content, keywords)),
                        importance = fact.importance,
                        updatedAt = fact.updatedAt,
                        now = now,
                        confidence = fact.confidence
                    )
                }
            )

        val selected = mutableListOf<String>()
        // 同一段**内容**只进一次：限定语不同的两条"同一件事"（比如一条偏好、一条计划）一起注入
        // 等于白花一条预算，还会让模型看到重复的一行。按内容去重比按渲染后的整行更严——
        // 整行会因为时间戳或限定语不同而看起来不一样。
        val seenContent = mutableSetOf<String>()
        var used = 0
        for ((fact, content) in ranked) {
            if (selected.size >= budget.topK) break
            if (content in seenContent) continue
            // 证据角色：只有"她亲历 / 已被确认"才以事实口气出现，其余带限定语（见 KnowledgeEvidencePolicy）。
            // 另外 `PLAN` 类记忆是**打算**，注入时必须标「计划，还没发生」——不许被演成已发生的剧情。
            val stamped = "${LiteTimeFormat.stamp(fact.updatedAt)}｜" +
                KnowledgeEvidencePolicy.decorate(
                    rawMode = fact.evidenceMode,
                    content = content,
                    futurePlan = fact.kind == MemoryKind.PLAN
                )
            if (used + stamped.length > budget.totalCharBudget) continue
            seenContent += content
            selected += stamped
            used += stamped.length
        }
        return selected
    }

    /** 关键词：≥3 字母的英文词 + 中文连续片段的 2-gram（比整段更易命中）。 */
    fun keywordsOf(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val latin = LATIN.findAll(text).map { it.value.lowercase() }
        val cjk = CJK.findAll(text).flatMap { match -> bigrams(match.value) }
        return (latin + cjk).toSet()
    }

    private fun bigrams(run: String): List<String> =
        if (run.length <= 2) listOf(run) else (0..run.length - 2).map { run.substring(it, it + 2) }

    private fun matches(content: String, keywords: Set<String>): Boolean =
        keywords.any { keyword -> keyword.length >= 2 && content.contains(keyword, ignoreCase = true) }

    private val WHITESPACE = Regex("\\s+")
    private val LATIN = Regex("[A-Za-z]{3,}")
    private val CJK = Regex("[\\u4e00-\\u9fff]{2,}")
}
