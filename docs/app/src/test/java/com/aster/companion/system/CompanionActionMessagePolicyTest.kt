package com.aster.companion.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionActionMessagePolicyTest {
    @Test
    fun `alarm failures use a natural message without technical detail`() {
        val message = CompanionActionMessagePolicy.failure("device.set_alarm")

        assertEquals("刚才没能把闹钟设好，我们晚点再试一次。", message)
        assertFalse(message.contains("Shizuku", ignoreCase = true))
        assertFalse(message.contains("HTTP", ignoreCase = true))
    }
}
