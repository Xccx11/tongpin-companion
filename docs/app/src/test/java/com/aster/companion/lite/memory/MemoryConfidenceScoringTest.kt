package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind
import com.aster.companion.domain.MemoryScorePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可信度（confidence）这一维的落地测试。
 *
 * 覆盖三件事：
 * 1. [MemoryScorePolicy] 的四维打分与 `confidence = 0` 的"未标注"语义；
 * 2. [MemoryRollupPolicy.groundingConfidence] 的落地性判定；
 * 3. [LiteMemoryFact.stored] 把库里的列映射成检索输入（含"0 = 未标注"的退回）。
 */
class MemoryConfidenceScoringTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    // ---- 打分：可信度这一维 ----

    @Test
    fun `未标注的可信度取中性值`() {
        assertEquals(MemoryScorePolicy.UNKNOWN_CONFIDENCE, MemoryScorePolicy.confidenceOf(0.0), 0.0001)
        assertEquals(MemoryScorePolicy.UNKNOWN_CONFIDENCE, MemoryScorePolicy.confidenceOf(-1.0), 0.0001)
        assertEquals(0.9, MemoryScorePolicy.confidenceOf(0.9), 0.0001)
        assertEquals(1.0, MemoryScorePolicy.confidenceOf(2.0), 0.0001)
    }

    @Test
    fun `同等条件下更可信的事实分数更高`() {
        val weak = MemoryScorePolicy.score(0.5, importance = 1, updatedAt = now, now = now, confidence = 0.1)
        val strong = MemoryScorePolicy.score(0.5, importance = 1, updatedAt = now, now = now, confidence = 0.9)

        assertTrue("$strong 应当高于 $weak", strong > weak)
    }

    @Test
    fun `未标注与显式传中性值完全等价`() {
        assertEquals(
            MemoryScorePolicy.score(0.5, importance = 2, updatedAt = now, now = now),
            MemoryScorePolicy.score(0.5, importance = 2, updatedAt = now, now = now, confidence = 0.0),
            0.0001
        )
    }

    /**
     * 权重守卫：相似度必须能压过"重要 + 新近 + 可信"三项之和。
     *
     * 这是这一维存在的意义——一个"她很确信、很重要、刚刚发生"但**这一轮根本没聊到**的事，
     * 不该盖过这一轮真的聊到的那件事。
     */
    @Test
    fun `相似度仍能翻掉高可信高重要新近`() {
        val hitButTrivial = MemoryScorePolicy.score(
            similarity = 1.0, importance = 0, updatedAt = now - 365 * day, now = now, confidence = 0.0
        )
        val missButEverything = MemoryScorePolicy.score(
            similarity = 0.0, importance = 3, updatedAt = now, now = now, confidence = 1.0
        )

        assertTrue("$hitButTrivial 应当高于 $missButEverything", hitButTrivial > missButEverything)
    }

    @Test
    fun `四维权重之和仍小于相似度权重`() {
        val others = MemoryScorePolicy.WEIGHT_IMPORTANCE +
            MemoryScorePolicy.WEIGHT_RECENCY +
            MemoryScorePolicy.WEIGHT_CONFIDENCE

        assertTrue("三项之和 $others 必须小于 ${MemoryScorePolicy.WEIGHT_SIMILARITY}", others < MemoryScorePolicy.WEIGHT_SIMILARITY)
    }

    // ---- 落地性可信度 ----

    @Test
    fun `事实能在来源对话里找到依据就是高可信`() {
        val confidence = MemoryRollupPolicy.groundingConfidence(
            fact = "喜欢美式咖啡",
            source = "我特别喜欢美式咖啡，每天早上都要来一杯"
        )

        assertEquals(MemoryRollupPolicy.CONFIDENCE_GROUNDED, confidence, 0.0001)
    }

    @Test
    fun `一个关键词都对不上就算依据不足`() {
        val confidence = MemoryRollupPolicy.groundingConfidence(
            fact = "喜欢美式咖啡",
            source = "今天天气不错，出门走了走"
        )

        assertEquals(MemoryRollupPolicy.CONFIDENCE_UNSUPPORTED, confidence, 0.0001)
    }

    @Test
    fun `只对上一部分算弱依据`() {
        val confidence = MemoryRollupPolicy.groundingConfidence(
            fact = "用户喜欢喝美式咖啡",
            source = "我们今天聊了美式"
        )

        assertEquals(MemoryRollupPolicy.CONFIDENCE_WEAK, confidence, 0.0001)
    }

    @Test
    fun `来源为空或事实没有关键词时给弱依据而不是零`() {
        assertEquals(
            MemoryRollupPolicy.CONFIDENCE_WEAK,
            MemoryRollupPolicy.groundingConfidence("喜欢美式咖啡", ""),
            0.0001
        )
        assertEquals(
            MemoryRollupPolicy.CONFIDENCE_WEAK,
            MemoryRollupPolicy.groundingConfidence("好", "好"),
            0.0001
        )
    }

    /** 依据不足**不等于**未标注：低分也必须非零，否则会被读侧当成"库里没标过"。 */
    @Test
    fun `依据不足的分数不是零`() {
        assertTrue(MemoryRollupPolicy.CONFIDENCE_UNSUPPORTED > 0.0)
        assertTrue(
            MemoryRollupPolicy.CONFIDENCE_UNSUPPORTED < MemoryRollupPolicy.CONFIDENCE_WEAK
        )
        assertTrue(MemoryRollupPolicy.CONFIDENCE_WEAK < MemoryRollupPolicy.CONFIDENCE_GROUNDED)
    }

    // ---- 库列 → 检索输入 ----

    @Test
    fun `重要度为未标注时退回按类型推`() {
        val fact = LiteMemoryFact.stored(
            id = "m1",
            kind = MemoryKind.RELATIONSHIP,
            content = "她怕黑",
            updatedAt = now,
            blockedFromMention = false,
            importance = 0,
            confidence = 0.0
        )

        assertEquals(MemoryBudgetPolicy.importanceOf(MemoryKind.RELATIONSHIP), fact.importance)
        assertEquals(MemoryScorePolicy.UNKNOWN_CONFIDENCE, fact.confidence, 0.0001)
    }

    @Test
    fun `落列的重要度优先于类型推断，并夹到上限`() {
        val stored = LiteMemoryFact.stored(
            id = "m2",
            kind = MemoryKind.EVENT,
            content = "她昨天去了医院",
            updatedAt = now,
            blockedFromMention = false,
            importance = 2,
            confidence = 0.9
        )
        assertEquals(2, stored.importance)
        assertEquals(0.9, stored.confidence, 0.0001)

        val overflow = LiteMemoryFact.stored(
            id = "m3",
            kind = MemoryKind.EVENT,
            content = "x",
            updatedAt = now,
            blockedFromMention = false,
            importance = 99,
            confidence = 9.0
        )
        assertEquals(MemoryScorePolicy.MAX_IMPORTANCE, overflow.importance)
        assertEquals(1.0, overflow.confidence, 0.0001)
    }

    /**
     * 落列的差距必须真的走到打分里：同样命中、同样重要、同样新，更可信的分数更高。
     *
     * 这条同时验证 [LiteMemoryFact.stored] **没有把库里的可信度抹掉**（如果它被清零或退回中性值，
     * 两个分数会相等而失败）。
     */
    @Test
    fun `落列可信度会改变打分`() {
        fun factOf(confidence: Double) = LiteMemoryFact.stored(
            id = "m-$confidence",
            kind = MemoryKind.EVENT,
            content = "她喜欢喝美式咖啡",
            updatedAt = now,
            blockedFromMention = false,
            importance = 1,
            confidence = confidence
        )

        fun scoreOf(fact: LiteMemoryFact) = MemoryScorePolicy.score(
            similarity = MemoryScorePolicy.keywordSimilarity(true),
            importance = fact.importance,
            updatedAt = fact.updatedAt,
            now = now,
            confidence = fact.confidence
        )

        val low = scoreOf(factOf(MemoryRollupPolicy.CONFIDENCE_UNSUPPORTED))
        val high = scoreOf(factOf(MemoryRollupPolicy.CONFIDENCE_GROUNDED))

        assertTrue("高可信 $high 应当高于低可信 $low", high > low)
    }
}
