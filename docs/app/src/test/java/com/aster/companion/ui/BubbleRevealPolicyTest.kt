package com.aster.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多气泡投递的节奏。
 *
 * 要钉死的：**只播刚到的最后一条**；流式占位不播；每段的停顿按自己的字数估并夹在上下限之间。
 */
class BubbleRevealPolicyTest {

    private val now = 1_700_000_000_000L

    // ---- 播不播 ----

    @Test
    fun `刚到的多段消息要逐段冒出来`() {
        assertTrue(BubbleRevealPolicy.shouldStagger(createdAt = now - 500, now = now, streaming = false, segmentCount = 3))
        assertTrue(BubbleRevealPolicy.shouldStagger(createdAt = now, now = now, streaming = false, segmentCount = 2))
    }

    @Test
    fun `超过时间窗的整条显示，滚上去不会重播`() {
        assertFalse(
            BubbleRevealPolicy.shouldStagger(
                createdAt = now - BubbleRevealPolicy.STAGGER_WINDOW_MS - 1,
                now = now,
                streaming = false,
                segmentCount = 3
            )
        )
        assertTrue(
            BubbleRevealPolicy.shouldStagger(
                createdAt = now - BubbleRevealPolicy.STAGGER_WINDOW_MS,
                now = now,
                streaming = false,
                segmentCount = 3
            )
        )
    }

    @Test
    fun `只有一段时没什么可冒的`() {
        assertFalse(BubbleRevealPolicy.shouldStagger(createdAt = now, now = now, streaming = false, segmentCount = 1))
        assertFalse(BubbleRevealPolicy.shouldStagger(createdAt = now, now = now, streaming = false, segmentCount = 0))
    }

    /** 流式占位的正文还在长，逐段冒出会把后面的话藏起来。 */
    @Test
    fun `流式输出一律整条显示`() {
        assertFalse(BubbleRevealPolicy.shouldStagger(createdAt = now, now = now, streaming = true, segmentCount = 5))
    }

    /**
     * 流式偏好开着时**不做**逐段冒泡：正文本来就是一整条逐步长出来的，用户已经看全了，
     * 定稿后再拆开逐段冒等于把刚看全的话收回去重放（这是实测报上来的 bug）。
     */
    @Test
    fun `流式偏好开着时不做逐段冒泡`() {
        assertFalse(
            BubbleRevealPolicy.shouldStagger(
                createdAt = now,
                now = now,
                streaming = false,
                segmentCount = 4,
                progressiveOutput = true
            )
        )
        assertTrue(
            "关掉流式时仍然逐段冒",
            BubbleRevealPolicy.shouldStagger(
                createdAt = now,
                now = now,
                streaming = false,
                segmentCount = 4,
                progressiveOutput = false
            )
        )
    }

    @Test
    fun `时间戳缺失或来自未来都不播`() {
        assertFalse(BubbleRevealPolicy.shouldStagger(createdAt = 0L, now = now, streaming = false, segmentCount = 3))
        assertFalse(BubbleRevealPolicy.shouldStagger(createdAt = now + 5_000, now = now, streaming = false, segmentCount = 3))
    }

    // ---- 停顿 ----

    @Test
    fun `短句取下限，长段取上限`() {
        assertEquals(BubbleRevealPolicy.MIN_PAUSE_MS, BubbleRevealPolicy.pauseBefore("嗯"))
        assertEquals(BubbleRevealPolicy.MAX_PAUSE_MS, BubbleRevealPolicy.pauseBefore("字".repeat(200)))
    }

    @Test
    fun `中等长度按字数估`() {
        val segment = "字".repeat(10)

        assertEquals(10L * BubbleRevealPolicy.MS_PER_CHAR, BubbleRevealPolicy.pauseBefore(segment))
    }

    @Test
    fun `首尾空白不计入字数`() {
        assertEquals(BubbleRevealPolicy.pauseBefore("字".repeat(10)), BubbleRevealPolicy.pauseBefore("   " + "字".repeat(10) + "\n"))
    }

    // ---- 时间表 ----

    @Test
    fun `第一段立刻出，之后每段按自己的长度等`() {
        val segments = listOf("嗯。", "字".repeat(12), "字".repeat(200))

        val schedule = BubbleRevealPolicy.revealSchedule(segments)

        assertEquals(3, schedule.size)
        assertEquals(0L, schedule[0])
        assertEquals(BubbleRevealPolicy.pauseBefore(segments[1]), schedule[1])
        assertEquals(BubbleRevealPolicy.MAX_PAUSE_MS, schedule[2])
    }

    @Test
    fun `空列表也能用`() {
        assertEquals(emptyList<Long>(), BubbleRevealPolicy.revealSchedule(emptyList()))
    }

    @Test
    fun `每一段的等待都在上下限之内`() {
        val schedule = BubbleRevealPolicy.revealSchedule(listOf("一", "二", "三"))

        schedule.forEach { pause ->
            assertTrue(pause >= 0)
            assertTrue(pause <= BubbleRevealPolicy.MAX_PAUSE_MS)
        }
    }
}
