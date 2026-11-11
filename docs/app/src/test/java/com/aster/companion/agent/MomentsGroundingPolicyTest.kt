package com.aster.companion.agent

import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.ConfirmationRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsGroundingPolicyTest {
    @Test
    fun `moments include every character independently of proactive contact selection`() {
        assertEquals(
            listOf("lan", "xia", "mei"),
            MomentsProactiveCharacterPolicy.eligibleCharacterIds(
                allCharacterIds = listOf("lan", "xia", "lan", "mei")
            )
        )
    }

    @Test
    fun `group messages remain source labelled grounding facts`() {
        val rendered = MomentsGrounding.render(
            listOf(
                MomentsGrounding.Fact("message:g1", "group:family", "姐姐", "今晚一起吃饭"),
                MomentsGrounding.Fact("message:g2", "group:family", "user", "我还在路上")
            )
        )

        assertTrue(rendered.contains("message:g1"))
        assertTrue(rendered.contains("group:family"))
        assertTrue(rendered.contains("姐姐"))
        assertTrue(rendered.contains("我还在路上"))
    }

    @Test
    fun `moment image action always requires local user approval`() {
        val request = MomentsImageActionPolicy.request(
            characterId = "lan",
            postId = "post-1",
            scene = "A quiet evening by the window"
        )

        assertEquals("companion.generate_moment_image", request.capabilityId)
        assertEquals(ActionTransport.LOCAL, request.transport)
        assertEquals(ConfirmationRequirement.REQUIRED, request.confirmation)
        assertEquals("post-1", request.arguments["post_id"])
        assertEquals("A quiet evening by the window", request.arguments["scene"])
    }
}
