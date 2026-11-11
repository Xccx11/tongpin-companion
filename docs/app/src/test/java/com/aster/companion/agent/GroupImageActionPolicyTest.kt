package com.aster.companion.agent

import com.aster.companion.domain.GroupImageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GroupImageActionPolicyTest {
    @Test
    fun `keeps separate character photo proposals as separate actions`() {
        val actions = GroupImageActionPolicy.normalize(
            listOf(
                GroupImageActionPolicy.Proposal("xia", listOf("xia"), GroupImageMode.SINGLE, "Xia sends a window-side photo"),
                GroupImageActionPolicy.Proposal("lan", listOf("lan"), GroupImageMode.SINGLE, "Lan sends a rainy-day photo")
            ),
            setOf("xia", "lan")
        )

        assertEquals(2, actions.size)
        assertEquals(listOf("xia"), actions[0].participantCharacterIds)
        assertEquals(listOf("lan"), actions[1].participantCharacterIds)
    }

    @Test
    fun `textual image wish creates one character confirmation proposal`() {
        val proposal = ImageProposalIntentPolicy.fromCharacterText(
            characterId = "xia",
            characterName = "Lin Jianxia",
            text = "(别开视线) 要不要看我刚刚拍的照片？",
            contextualScene = "Lin Jianxia wants to show the user the photo she just mentioned."
        )

        assertNotNull(proposal)
        val resolved = requireNotNull(proposal)
        assertEquals(listOf("xia"), resolved.participantCharacterIds)
        assertEquals(GroupImageMode.SINGLE, resolved.mode)
    }

    @Test
    fun `duplicate group photo proposals create one consent action`() {
        val actions = GroupImageActionPolicy.normalize(
            listOf(
                GroupImageActionPolicy.Proposal("xia", listOf("xia", "lan"), GroupImageMode.TOGETHER, "Both are together in one photo."),
                GroupImageActionPolicy.Proposal("lan", listOf("xia", "lan"), GroupImageMode.TOGETHER, "Both are together in one photo.")
            ),
            setOf("xia", "lan")
        )

        assertEquals(1, actions.size)
        assertEquals(GroupImageMode.TOGETHER, actions.single().mode)
    }
}
