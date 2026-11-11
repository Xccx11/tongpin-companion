package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossConversationVisibleReplyPolicyTest {
    @Test fun `private message tool call without content gets a natural group acknowledgement`() {
        assertEquals("好，我私聊你。", CrossConversationVisibleReplyPolicy.fallback("companion.send_private_message"))
    }

    @Test fun `group message tool call without content gets a natural private acknowledgement`() {
        assertEquals("好，我去群里说。", CrossConversationVisibleReplyPolicy.fallback("companion.send_group_message"))
    }
}
