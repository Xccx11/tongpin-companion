package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChatDuplicatePolicyTest {
    @Test
    fun `recognizes the same reply despite chat punctuation`() {
        assertTrue(CrossChatDuplicatePolicy.isRecentDuplicate(
            "我刚才没说清楚，我们私下聊。",
            listOf("我刚才没说清楚 我们私下聊")
        ))
    }

    @Test
    fun `keeps a different group reply`() {
        assertFalse(CrossChatDuplicatePolicy.isRecentDuplicate(
            "我先在群里补一句。",
            listOf("我们私下聊。")
        ))
    }
}
