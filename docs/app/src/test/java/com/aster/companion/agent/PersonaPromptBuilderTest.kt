package com.aster.companion.agent

import com.aster.companion.domain.CharacterCard
import com.aster.companion.domain.CompanionCapabilitySnapshot
import com.aster.companion.domain.Capability
import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.ConfirmationRequirement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaPromptBuilderTest {
    @Test
    fun `chat prompt contains personality but excludes appearance`() {
        val prompt = PersonaPromptBuilder.build(
            CharacterCard("lan", "岚", "克制但会主动陪伴", "黑色短发，窗边摄影", null),
            CompanionCapabilitySnapshot.empty(),
            recentMessages = listOf("用户：我有点饿"),
            eventSummaries = listOf("外卖状态：已送达")
        )

        assertTrue(prompt.contains("克制但会主动陪伴"))
        assertFalse(prompt.contains("黑色短发"))
    }

    @Test
    fun `agent prompt explains Shizuku priority and action arguments`() {
        val prompt = PersonaPromptBuilder.build(
            CharacterCard("lan", "岚", "克制但会主动陪伴", "黑色短发", null),
            CompanionCapabilitySnapshot(1, setOf(Capability("phone.dial", ActionTransport.SHIZUKU, ConfirmationRequirement.REQUIRED))),
            recentMessages = emptyList(),
            eventSummaries = emptyList()
        )

        assertTrue(prompt.contains("优先使用 Shizuku"))
        assertTrue(prompt.contains("phone_number"))
        assertTrue(prompt.contains("没有自己独立的手机"))
        assertTrue(prompt.contains("不超过 28 字"))
    }

    @Test
    fun `proactive private prompt exposes group targets for autonomous group messages`() {
        val prompt = PersonaPromptBuilder.build(
            character = CharacterCard("lan", "lan", "steady", "short hair"),
            snapshot = CompanionCapabilitySnapshot(
                1,
                setOf(Capability("companion.send_group_message", ActionTransport.LOCAL, ConfirmationRequirement.NONE))
            ),
            recentMessages = emptyList(),
            eventSummaries = listOf("a real group message"),
            groupContext = listOf("In family group, User said: I am tired"),
            availableGroupTargets = listOf("group_thread_id=family; title=family")
        )

        assertTrue(prompt.contains("without waiting for a user command"))
        assertTrue(prompt.contains("group_thread_id=family"))
    }
}
