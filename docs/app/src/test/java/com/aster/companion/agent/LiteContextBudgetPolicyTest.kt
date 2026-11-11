package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteContextBudgetPolicyTest {

    private val budget = ConversationContextWindowPolicy.inputBudgetChars(16_384)

    @Test
    fun `keeps everything when the request fits`() {
        val plan = LiteContextBudgetPolicy.plan(
            budgetChars = budget,
            fixedChars = 2_000,
            personalityChars = 1_500,
            personalityLimit = 2_000,
            historyChars = List(20) { 200 },
            userChars = 40
        )
        assertEquals(20, plan.historyCount)
        assertEquals(2_000, plan.personalityLimit)
        assertFalse(plan.overBudget)
    }

    /** 超预算时**先丢最旧的整条消息**，最近的消息一条都不动。 */
    @Test
    fun `drops the oldest messages first and keeps the newest intact`() {
        val plan = LiteContextBudgetPolicy.plan(
            budgetChars = 4_000,
            fixedChars = 2_000,
            personalityChars = 1_000,
            personalityLimit = 2_000,
            historyChars = listOf(900, 300, 300, 300, 300),
            userChars = 50
        )
        assertEquals("最旧的哪条超大就先不要它", 3, plan.historyCount)
        assertTrue(plan.estimatedChars <= 4_000)
        assertEquals("人设不需要动", 2_000, plan.personalityLimit)
    }

    @Test
    fun `shrinks personality only after the history is gone`() {
        val plan = LiteContextBudgetPolicy.plan(
            budgetChars = 2_000,
            fixedChars = 1_500,
            personalityChars = 2_000,
            personalityLimit = 2_000,
            historyChars = listOf(400, 400),
            userChars = 50
        )
        assertEquals(0, plan.historyCount)
        assertEquals(450, plan.personalityLimit)
        assertFalse(plan.overBudget)
    }

    @Test
    fun `never shrinks personality below the floor and reports the overflow`() {
        val plan = LiteContextBudgetPolicy.plan(
            budgetChars = 1_000,
            fixedChars = 3_000,
            personalityChars = 2_000,
            personalityLimit = 2_000,
            historyChars = emptyList(),
            userChars = 10
        )
        assertEquals(LiteContextBudgetPolicy.MIN_PERSONALITY_CHARS, plan.personalityLimit)
        assertTrue(plan.overBudget)
    }

    /** 预算随用户选的上下文档位线性放大：16K→12,288 字、32K→24,576、64K→49,152。 */
    @Test
    fun `budget scales with the selected context length`() {
        assertEquals(12_288, ConversationContextWindowPolicy.inputBudgetChars(16_384))
        assertEquals(24_576, ConversationContextWindowPolicy.inputBudgetChars(32_768))
        assertEquals(49_152, ConversationContextWindowPolicy.inputBudgetChars(65_536))
        assertEquals(73_728, ConversationContextWindowPolicy.inputBudgetChars(98_304))
    }

    /**
     * 窗口条数是**独立设置**，档位只提供"推荐值"。
     *
     * 以前它跟着档位一路涨到 160 条（128K 档把整条对话每轮重发，越聊越慢）；
     * 现在默认 40 条、可在设置里手调，这里只保证推荐值与档位相配。
     */
    @Test
    fun `window presets match the context tiers`() {
        assertEquals(20, ConversationContextWindowPolicy.recommendedWindowMessages(16_384))
        assertEquals(40, ConversationContextWindowPolicy.recommendedWindowMessages(32_768))
        assertEquals(80, ConversationContextWindowPolicy.recommendedWindowMessages(65_536))
        assertEquals(120, ConversationContextWindowPolicy.recommendedWindowMessages(98_304))
        assertEquals(160, ConversationContextWindowPolicy.recommendedWindowMessages(131_072))
        assertTrue(
            "默认窗口必须是档位表里的 40 条",
            ConversationContextWindowPolicy.WINDOW_PRESETS.any { it.second == ConversationContextWindowPolicy.DEFAULT_WINDOW_MESSAGES }
        )
    }

    /** 自定义值必须原样保留：不再吸附到档位，只做上下限收敛。 */
    @Test
    fun `custom context and window values survive normalization`() {
        assertEquals(30_000, ConversationContextWindowPolicy.normalizeContext(30_000))
        assertEquals(98_304, ConversationContextWindowPolicy.normalizeContext(98_304))
        assertEquals(77, ConversationContextWindowPolicy.normalizeWindow(77))

        assertEquals(ConversationContextWindowPolicy.MIN_CONTEXT_TOKENS, ConversationContextWindowPolicy.normalizeContext(0))
        assertEquals(ConversationContextWindowPolicy.MAX_CONTEXT_TOKENS, ConversationContextWindowPolicy.normalizeContext(9_999_999))
        assertEquals(ConversationContextWindowPolicy.MIN_WINDOW_MESSAGES, ConversationContextWindowPolicy.normalizeWindow(0))
        assertEquals(ConversationContextWindowPolicy.MAX_WINDOW_MESSAGES, ConversationContextWindowPolicy.normalizeWindow(9_999))
    }

    @Test
    fun `labels tell preset and custom values apart`() {
        assertEquals("32K", ConversationContextWindowPolicy.formatTokens(32_768))
        assertEquals("96K", ConversationContextWindowPolicy.formatTokens(98_304))
        assertTrue(ConversationContextWindowPolicy.labelFor(32_768).startsWith("32K"))
        assertTrue(ConversationContextWindowPolicy.labelFor(30_000).contains("自定义"))
        assertTrue(ConversationContextWindowPolicy.windowLabelFor(40).contains("40 条"))
        assertTrue(ConversationContextWindowPolicy.windowLabelFor(77).contains("自定义"))
    }
}
