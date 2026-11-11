package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTurnOrderPolicyTest {
    @Test
    fun `group user message is persisted before speaker planning`() {
        assertTrue(
            ConversationTurnOrderPolicy.shouldPersistUserMessageBeforeSpeakerSelection(
                threadType = ConversationType.GROUP,
                persistUserMessage = true
            )
        )
    }

    @Test
    fun `visual context without a persisted user message does not create a message`() {
        assertFalse(
            ConversationTurnOrderPolicy.shouldPersistUserMessageBeforeSpeakerSelection(
                threadType = ConversationType.GROUP,
                persistUserMessage = false
            )
        )
    }
}
