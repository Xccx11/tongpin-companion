package com.aster.companion.system

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossConversationMessageReceiptTest {
    @Test fun `private send failure has a factual source receipt`() {
        assertEquals("刚才没能把这句话私聊发过去。", CrossConversationMessageReceipt.failure("companion.send_private_message"))
    }

    @Test fun `group send failure has a factual source receipt`() {
        assertEquals("刚才没能把这句话发到群里。", CrossConversationMessageReceipt.failure("companion.send_group_message"))
    }
}
