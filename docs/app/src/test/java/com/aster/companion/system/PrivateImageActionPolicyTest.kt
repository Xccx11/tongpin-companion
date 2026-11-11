package com.aster.companion.system

import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.ConfirmationRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateImageActionPolicyTest {
    @Test
    fun `routes a group private photo request to the requested member private conversation`() {
        val request = AgentActionRequest(
            capabilityId = "companion.propose_private_image",
            transport = ActionTransport.LOCAL,
            confirmation = ConfirmationRequirement.REQUIRED,
            arguments = mapOf(
                "character_id" to "xia",
                "scene" to "one young woman, long black hair, relaxed smile, natural indoor portrait, soft window light"
            )
        )

        assertEquals("xia", PrivateImageActionPolicy.targetCharacterId(request, setOf("xia", "lan")))
        assertNull(ActionRequestValidator.errorFor(request.capabilityId, request.arguments))
        assertNull(ActionTransportPolicy.errorFor(request.capabilityId, request.transport, shizukuReady = false))
    }
}
