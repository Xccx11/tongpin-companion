package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.aster.companion.domain.AgentDecision
import com.aster.companion.domain.AgentDecisionKind
import com.aster.companion.domain.ConversationType

class ProactiveImageIntentPolicyTest {
    @Test
    fun `character image desire becomes a required proposal action`() {
        val action = ProactiveImageIntentPolicy.proposalAction(
            characterId = "xia",
            characterName = "夏",
            text = "我想给你发一张我刚拍的照片，你要不要看？",
            currentContext = "The user has been studying late tonight."
        )

        assertNotNull(action)
        assertEquals("companion.propose_image", action!!.capabilityId)
        assertEquals(com.aster.companion.domain.ConfirmationRequirement.REQUIRED, action.confirmation)
        assertEquals("xia", action.arguments["character_id"])
        assertEquals(true, action.arguments["scene"]?.contains("studying late tonight"))
    }

    @Test
    fun `photo mention without character intent creates no proposal`() {
        val action = ProactiveImageIntentPolicy.proposalAction(
            characterId = "xia",
            characterName = "夏",
            text = "我看见你发来的照片了，挺好看的。",
            currentContext = "The user sent an image."
        )

        assertNull(action)
    }

    @Test
    fun `character image desire upgrades an ignore decision to a required tool call`() {
        val action = requireNotNull(
            ProactiveImageIntentPolicy.proposalAction(
                characterId = "xia",
                characterName = "夏",
                text = "我想给你发一张我刚拍的照片，你要不要看？",
                currentContext = "晚自习结束了"
            )
        )
        val merged = ProactiveImageIntentPolicy.attachProposal(
            AgentDecision(
                kind = AgentDecisionKind.IGNORE,
                threadType = ConversationType.PRIVATE,
                message = "我想给你发一张我刚拍的照片，你要不要看？"
            ),
            action
        )

        assertEquals(AgentDecisionKind.TOOL_CALLS, merged.kind)
        assertEquals(1, merged.actions.size)
        assertEquals("companion.propose_image", merged.actions.single().capabilityId)
    }
}
