package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupImagePromptPolicyTest {
    @Test
    fun `participant list accepts only current members and removes duplicates`() {
        assertEquals(
            listOf("lan", "xue"),
            GroupImagePromptPolicy.parseParticipantIds("lan,xue,lan", setOf("lan", "xue", "yuan"))
        )
        assertNull(GroupImagePromptPolicy.parseParticipantIds("lan,unknown", setOf("lan", "xue")))
        assertNull(GroupImagePromptPolicy.parseParticipantIds("", setOf("lan", "xue")))
    }

    @Test
    fun `renderer prompt contains selected visual identities and scene but no personality text`() {
        val prompt = GroupImagePromptPolicy.rendererPrompt(
            listOf(
                GroupImageCharacterAnchor("lan", "岚", "black bob hair; white shirt", "file://lan.png"),
                GroupImageCharacterAnchor("xue", "雪", "long brown hair; blue cardigan", null)
            ),
            "two friends sharing hot tea by a rainy window"
        )

        assertTrue(prompt.contains("岚"))
        assertTrue(prompt.contains("black bob hair; white shirt"))
        assertTrue(prompt.contains("雪"))
        assertTrue(prompt.contains("two friends sharing hot tea by a rainy window"))
        assertTrue(!prompt.contains("personality"))
    }
}
