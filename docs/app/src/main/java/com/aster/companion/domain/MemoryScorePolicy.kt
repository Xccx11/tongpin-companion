package com.aster.companion.domain

/**
 * L2 事实的检索打分：把**相似度 / 重要度 / 新近度 / 可信度**四样合成一个分数。
 *
 * ## 它解决什么
 *
 * 以前检索排序是两条互不相干的规则：
 *
 * - 无向量分支：`关键词命中 → 重要度 → 更新时间`，**三级字典序**——只要重要度差一级，
 *   时间就完全不起作用（昨天刚说的事会输给三个月前的一件"更重要"的事）；
 * - 向量分支：**纯余弦降序**，重要度和时间一概不看——于是"碰巧语义相近的琐事"会压过
 *   "她昨天刚说的要紧事"。
 *
 * 现在两处都走这一个打分：相似度是主信号（权重远大于另三项，所以不会把命中翻掉），
 * 同档之内再由重要度、新近度与可信度决定先后。
 *
 * ## 可信度（confidence）从哪来
 *
 * 上游由压缩模型自报"这条事实有多可信"；同频的压缩模型不返回这个值，所以改由**落地性检查**
 * 给出（见 `MemoryRollupPolicy.groundingConfidence`）：能在产生它的那段对话里找到词面依据
 * 就给高分。v32 起它是**落库的列**（`companion_memories.confidence`），不再是现推的。
 *
 * 库里 `0` 一律解释为"未标注"→ 取中性的 [UNKNOWN_CONFIDENCE]，所以加列之前的老数据语义不变。
 *
 * 纯计算：不读时钟（`now` 由调用方传）、不碰数据库。
 */
object MemoryScorePolicy {

    /**
     * 相似度权重。刻意**大于**另三项之和（0.35 + 0.25 + 0.15 = 0.75）：
     * "这一轮真的聊到了它"是一票否决级别的主信号，不能被重要度/新近度/可信度翻盘。
     */
    const val WEIGHT_SIMILARITY = 1.0
    const val WEIGHT_IMPORTANCE = 0.35
    const val WEIGHT_RECENCY = 0.25
    const val WEIGHT_CONFIDENCE = 0.15

    /** 重要度上限（与 `MemoryBudgetPolicy.importanceOf` 的返回值域一致：0..3）。 */
    const val MAX_IMPORTANCE = 3

    /** 新近度半衰期：7 天前的事，这一维权重减半。 */
    const val RECENCY_HALF_LIFE_MS = 7L * 24 * 60 * 60 * 1000

    /** 没有相似度可言时的取值（关键词没命中）。 */
    const val NO_SIMILARITY = 0.0

    /** 库里没标过可信度（`confidence = 0`）时取的中性值。 */
    const val UNKNOWN_CONFIDENCE = 0.5

    data class Components(
        val similarity: Double,
        val importance: Double,
        val recency: Double,
        val confidence: Double
    ) {
        val total: Double
            get() = similarity * WEIGHT_SIMILARITY +
                importance * WEIGHT_IMPORTANCE +
                recency * WEIGHT_RECENCY +
                confidence * WEIGHT_CONFIDENCE
    }

    /** 重要度归一化到 0..1；越界夹取（kind 推导出的值只可能是 0..3，这里是防御）。 */
    fun importanceOf(importance: Int): Double =
        importance.coerceIn(0, MAX_IMPORTANCE).toDouble() / MAX_IMPORTANCE

    /** 可信度：`0` = 未标注 → [UNKNOWN_CONFIDENCE]；其余夹到 0..1。 */
    fun confidenceOf(confidence: Double): Double =
        if (confidence <= 0.0) UNKNOWN_CONFIDENCE else confidence.coerceIn(0.0, 1.0)

    /**
     * 新近度：`0.5 ^ (age / 半衰期)`，刚发生是 1.0，半衰期后 0.5，两周后 0.25。
     *
     * 时钟回拨（[ageMs] 为负）按"刚发生"算，不让它变成大于 1 的奖励。
     */
    fun recencyOf(ageMs: Long): Double {
        val age = ageMs.coerceAtLeast(0L)
        return Math.pow(0.5, age.toDouble() / RECENCY_HALF_LIFE_MS)
    }

    /**
     * 余弦相似度 → 打分用的相似度。
     *
     * - 不可比（null）→ [NO_SIMILARITY]；
     * - 负数（语义相反）**夹到 0**：语义相反不该比"没聊到"更差，否则会把它排到很后面而不是
     *   正常参与排序——反过来说，也不给它负分去惩罚一条本来就重要的记忆。
     */
    fun similarityOf(cosine: Double?): Double = (cosine ?: NO_SIMILARITY).coerceIn(0.0, 1.0)

    /** 关键词分支的相似度：命中满分，没命中 0。 */
    fun keywordSimilarity(matched: Boolean): Double = if (matched) 1.0 else NO_SIMILARITY

    fun components(
        similarity: Double,
        importance: Int,
        updatedAt: Long,
        now: Long,
        confidence: Double = UNKNOWN_CONFIDENCE
    ): Components = Components(
        similarity = similarity.coerceIn(0.0, 1.0),
        importance = importanceOf(importance),
        recency = recencyOf(now - updatedAt),
        confidence = confidenceOf(confidence)
    )

    fun score(
        similarity: Double,
        importance: Int,
        updatedAt: Long,
        now: Long,
        confidence: Double = UNKNOWN_CONFIDENCE
    ): Double = components(similarity, importance, updatedAt, now, confidence).total
}
