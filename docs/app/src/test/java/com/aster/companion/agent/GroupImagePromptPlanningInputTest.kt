package com.aster.companion.agent

import com.aster.companion.domain.GroupImageCharacterAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupImagePromptPlanningInputTest {
    @Test
    fun `preserves named group context and each selected identity for a shared image`() {
        val input = GroupImagePromptPlanningInput.create(
            anchors = listOf(
                GroupImageCharacterAnchor("lan", "岚", "black high ponytail, almond eyes", "lan.png"),
                GroupImageCharacterAnchor("xue", "雪", "short silver hair, round eyes", "xue.png")
            ),
            currentScene = "姐姐刚说想在下雨的窗边喝热可可，拍我们两个人的合照",
            recentTranscript = listOf(
                GroupImagePromptPlanningInput.Line("用户", true, "姐姐妹妹一起拍一张"),
                GroupImagePromptPlanningInput.Line("岚", false, "那就坐窗边吧，我去倒热可可。"),
                GroupImagePromptPlanningInput.Line("雪", false, "好呀，我要靠姐姐近一点。")
            )
        )

        assertEquals(2, input.requiredCharacterCount)
        assertTrue(input.identityFacts.contains("Character 1 (岚)"))
        assertTrue(input.identityFacts.contains("Character 2 (雪)"))
        assertTrue(input.recentConversation.any { it.startsWith("CHARACTER [岚]:") })
        assertTrue(input.recentConversation.any { it.startsWith("CHARACTER [雪]:") })
        assertTrue(input.scene.contains("下雨的窗边"))
        assertFalse(input.recentConversation.any { it.startsWith("Character:") })
    }
}
