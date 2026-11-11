package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检索打分（[MemoryScorePolicy]）的纯逻辑测试。
 *
 * 最要紧的一条不变量：**相似度是主信号**——命中的最旧琐事，仍然要排在未命中的最新重要事实之前。
 * 否则"这一轮真的聊到了它"这件事会被重要度/时间翻掉，检索就退化成按重要度列表输出。
 */
class MemoryScorePolicyTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `重要度归一化到零至一并夹取越界值`() {
        assertEquals(0.0, MemoryScorePolicy.importanceOf(0), 1e-9)
        assertEquals(1.0 / 3, MemoryScorePolicy.importanceOf(1), 1e-9)
        assertEquals(1.0, MemoryScorePolicy.importanceOf(3), 1e-9)
        assertEquals(1.0, MemoryScorePolicy.importanceOf(9), 1e-9)
        assertEquals(0.0, MemoryScorePolicy.importanceOf(-2), 1e-9)
    }

    @Test
    fun `新近度按半衰期衰减`() {
        assertEquals(1.0, MemoryScorePolicy.recencyOf(0), 1e-9)
        assertEquals(0.5, MemoryScorePolicy.recencyOf(MemoryScorePolicy.RECENCY_HALF_LIFE_MS), 1e-9)
        assertEquals(0.25, MemoryScorePolicy.recencyOf(2 * MemoryScorePolicy.RECENCY_HALF_LIFE_MS), 1e-9)
    }

    @Test
    fun `新近度随时间单调不增，时钟回拨不会变成奖励`() {
        assertTrue(MemoryScorePolicy.recencyOf(day) >= MemoryScorePolicy.recencyOf(30 * day))
        assertEquals(1.0, MemoryScorePolicy.recencyOf(-5 * day), 1e-9)
    }

    @Test
    fun `相似度把不可比与负数收敛到 0，把大于一夹到一`() {
        assertEquals(0.0, MemoryScorePolicy.similarityOf(null), 1e-9)
        assertEquals(0.0, MemoryScorePolicy.similarityOf(-1.0), 1e-9)
        assertEquals(0.42, MemoryScorePolicy.similarityOf(0.42), 1e-9)
        assertEquals(1.0, MemoryScorePolicy.similarityOf(1.7), 1e-9)
    }

    @Test
    fun `关键词命中就是满分相似度`() {
        assertEquals(1.0, MemoryScorePolicy.keywordSimilarity(true), 1e-9)
        assertEquals(0.0, MemoryScorePolicy.keywordSimilarity(false), 1e-9)
    }

    @Test
    fun `总分是四项的加权和`() {
        val components = MemoryScorePolicy.components(similarity = 1.0, importance = 3, updatedAt = now, now = now)
        assertEquals(1.0, components.similarity, 1e-9)
        assertEquals(1.0, components.importance, 1e-9)
        assertEquals(1.0, components.recency, 1e-9)
        assertEquals(MemoryScorePolicy.UNKNOWN_CONFIDENCE, components.confidence, 1e-9)
        assertEquals(
            MemoryScorePolicy.WEIGHT_SIMILARITY +
                MemoryScorePolicy.WEIGHT_IMPORTANCE +
                MemoryScorePolicy.WEIGHT_RECENCY +
                MemoryScorePolicy.UNKNOWN_CONFIDENCE * MemoryScorePolicy.WEIGHT_CONFIDENCE,
            components.total,
            1e-9
        )
    }

    @Test
    fun `相似度是主信号，不会被重要度新近度与可信度之和翻盘`() {
        val hit = MemoryScorePolicy.score(similarity = 1.0, importance = 0, updatedAt = now - 365 * day, now = now)
        val miss = MemoryScorePolicy.score(similarity = 0.0, importance = 3, updatedAt = now, now = now)
        assertTrue("命中的最旧琐事仍应排在未命中的最新重要事实之前", hit > miss)
        val floor = MemoryScorePolicy.UNKNOWN_CONFIDENCE * MemoryScorePolicy.WEIGHT_CONFIDENCE
        assertEquals(MemoryScorePolicy.WEIGHT_SIMILARITY + floor, hit, 1e-9)
        assertEquals(
            MemoryScorePolicy.WEIGHT_IMPORTANCE + MemoryScorePolicy.WEIGHT_RECENCY + floor,
            miss,
            1e-9
        )
    }

    @Test
    fun `同相似度时重要度高者胜`() {
        val important = MemoryScorePolicy.score(0.8, importance = 3, updatedAt = now, now = now)
        val trivial = MemoryScorePolicy.score(0.8, importance = 1, updatedAt = now, now = now)
        assertTrue(important > trivial)
    }

    @Test
    fun `同相似度同重要度时更新的胜`() {
        val fresh = MemoryScorePolicy.score(0.8, importance = 2, updatedAt = now - day, now = now)
        val stale = MemoryScorePolicy.score(0.8, importance = 2, updatedAt = now - 60 * day, now = now)
        assertTrue(fresh > stale)
    }

    /**
     * 前三维都趋零时，总分只剩"未标注可信度"那一份底分（0.5 × 0.15 = 0.075）。
     *
     * 这个底分是刻意的：`confidence = 0` 是"库里没标过"，不是"不可信"——老数据不该因此被惩罚。
     */
    @Test
    fun `三项都趋于零时只剩可信度的底分`() {
        val score = MemoryScorePolicy.score(0.0, importance = 0, updatedAt = now - 1000 * day, now = now)

        assertEquals(
            MemoryScorePolicy.UNKNOWN_CONFIDENCE * MemoryScorePolicy.WEIGHT_CONFIDENCE,
            score,
            1e-9
        )
        assertTrue("底分必须远小于任意命中项", score < MemoryScorePolicy.WEIGHT_SIMILARITY / 4)
    }
}
