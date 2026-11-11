package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ProactiveContextPolicyTest {
    @Test
    fun `proactive context contains only primary conversation and user saved memories`() {
        val context = ProactiveContextPolicy.planningContext(
            recentConversation = listOf("用户：今天在玩游戏"),
            userSavedMemories = listOf("记忆：用户喜欢竞技游戏")
        )

        assertEquals(
            listOf("用户：今天在玩游戏", "记忆：用户喜欢竞技游戏"),
            context
        )
    }

    @Test
    fun `grounding facts never accept generated world or semantic memory`() {
        val facts = ProactiveContextPolicy.groundingFacts(
            recentConversation = listOf("角色：我等你打完"),
            userSavedMemories = listOf("记忆：用户晚上通常有空"),
            realEvents = listOf("device:foreground_app: com.tencent.tmgp.game")
        )

        assertEquals(
            listOf(
                "角色：我等你打完",
                "记忆：用户晚上通常有空",
                "device:foreground_app: com.tencent.tmgp.game"
            ),
            facts
        )
    }

    @Test
    fun `planning context includes character continuity but preserves its explicit labels`() {
        val context = ProactiveContextPolicy.planningContext(
            recentConversation = listOf("chat"),
            userSavedMemories = listOf("memory"),
            semanticMemories = listOf("diary"),
            privateWorld = listOf("private world"),
            sharedWorld = listOf("shared world")
        )

        assertEquals(listOf("chat", "memory", "diary", "private world", "shared world"), context)
    }
}
