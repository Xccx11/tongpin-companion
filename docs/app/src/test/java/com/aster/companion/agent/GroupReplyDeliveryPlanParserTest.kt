package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupReplyDeliveryPlanParserTest {
    @Test
    fun `parses a private continuation into a private message`() {
        val plan = GroupReplyDeliveryPlanParser.parse(
            """{"delivery":"PRIVATE","message":"这句我想单独和你说。"}"""
        )

        requireNotNull(plan)
        assertEquals(GroupReplyDelivery.PRIVATE, plan.delivery)
        assertEquals("这句我想单独和你说。", plan.message)
    }

    @Test
    fun `invalid protocol does not create a routed message`() {
        assertNull(GroupReplyDeliveryPlanParser.parse("我还是直接在群里说吧。"))
    }
}
