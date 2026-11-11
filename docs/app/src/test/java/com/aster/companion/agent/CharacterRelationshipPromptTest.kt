package com.aster.companion.agent

import com.aster.companion.domain.CharacterCard
import com.aster.companion.domain.CompanionCapabilitySnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterRelationshipPromptTest {
    @Test
    fun relationshipSettingIsIncludedAsBehaviorContext() {
        val prompt = PersonaPromptBuilder.build(
            character = CharacterCard(
                id = "lan",
                name = "岚",
                personalityPrompt = "克制但会主动陪伴",
                appearancePrompt = "黑色短发",
                relationshipPrompt = "我是用户的恋人，叫他阿澈，亲近但尊重边界"
            ),
            snapshot = CompanionCapabilitySnapshot.empty(),
            recentMessages = emptyList(),
            eventSummaries = emptyList()
        )

        assertTrue(prompt.contains("我是用户的恋人，叫他阿澈，亲近但尊重边界"))
    }
}
