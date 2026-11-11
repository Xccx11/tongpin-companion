package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryScorePolicy
import kotlin.math.sqrt

/**
 * 向量检索的纯计算部分（记忆分册 §4.3 的 Embedding 分支）。
 *
 * 只做余弦相似度与排序；维度不一致或零向量一律返回 null，
 * 由调用方回退到 [MemoryRetrievalPolicy] 的关键词规则。
 */
object MemoryEmbeddingPolicy {

    /** 余弦相似度；向量不可比时返回 null。 */
    fun cosine(a: FloatArray, b: FloatArray): Double? {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return null
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in a.indices) {
            dot += a[index] * b[index]
            normA += a[index] * a[index]
            normB += b[index] * b[index]
        }
        if (normA <= 0.0 || normB <= 0.0) return null
        return dot / (sqrt(normA) * sqrt(normB))
    }

    /**
     * 按**加权打分**降序排列（相似度 / 重要度 / 新近度）；向量不可比的项直接丢弃。
     *
     * 以前这里是"纯余弦降序"——于是"碰巧语义相近的琐事"会压过"她昨天刚说的要紧事"。
     * 现在三样一起算（见 [MemoryScorePolicy]），相似度仍是主信号，不会被另两项翻盘。
     */
    fun rank(
        facts: List<Pair<LiteMemoryFact, FloatArray>>,
        query: FloatArray,
        /** 打分要用新近度；时钟由调用方传，policy 自己不读时钟（便于单测）。 */
        now: Long = System.currentTimeMillis()
    ): List<LiteMemoryFact> = facts
        .mapNotNull { (fact, vector) ->
            cosine(query, vector)?.let { similarity ->
                fact to MemoryScorePolicy.score(
                    similarity = MemoryScorePolicy.similarityOf(similarity),
                    importance = fact.importance,
                    updatedAt = fact.updatedAt,
                    now = now,
                    confidence = fact.confidence
                )
            }
        }
        .sortedByDescending { it.second }
        .map { it.first }

    /** 向量排序后再套 Top-K 与字符预算，产出与关键词规则同样形态的「记得的事」。 */
    fun selectBySimilarity(
        facts: List<Pair<LiteMemoryFact, FloatArray>>,
        query: FloatArray,
        budget: MemoryBudget = MemoryBudgetPolicy.default,
        now: Long = System.currentTimeMillis()
    ): List<String> {
        val eligible = facts.filter { (fact, _) ->
            !fact.blockedFromMention && MemoryBudgetPolicy.isInjectable(fact.kind)
        }
        val selected = mutableListOf<String>()
        var used = 0
        for (fact in rank(eligible, query, now)) {
            if (selected.size >= budget.topK) break
            val content = fact.content.trim().replace(WHITESPACE, " ").take(budget.perFactChars)
            if (content.isEmpty()) continue
            val stamped = "${LiteTimeFormat.stamp(fact.updatedAt)}｜$content"
            if (stamped in selected) continue
            if (used + stamped.length > budget.totalCharBudget) continue
            selected += stamped
            used += stamped.length
        }
        return selected
    }

    private val WHITESPACE = Regex("\\s+")
}
