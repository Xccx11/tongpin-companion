package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldUserInvitationResponseParserTest {

    @Test
    fun `only explicit acceptance phrases join an invitation`() {
        assertEquals(WorldUserInvitationResponse.ACCEPT, WorldUserInvitationResponseParser.parse("我参加"))
        assertEquals(WorldUserInvitationResponse.ACCEPT, WorldUserInvitationResponseParser.parse("接受"))
        assertEquals(WorldUserInvitationResponse.NONE, WorldUserInvitationResponseParser.parse("好呀，晚点聊"))
    }

    @Test
    fun `explicit rejection is distinguishable from unrelated chat`() {
        assertEquals(WorldUserInvitationResponse.REJECT, WorldUserInvitationResponseParser.parse("不参加"))
        assertEquals(WorldUserInvitationResponse.REJECT, WorldUserInvitationResponseParser.parse("拒绝"))
        assertEquals(WorldUserInvitationResponse.NONE, WorldUserInvitationResponseParser.parse("我现在在开会"))
    }
}
