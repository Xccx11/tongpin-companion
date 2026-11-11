package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ProactiveDeliveryTextPolicyTest {
    @Test
    fun `uses the rendered text as the final text to ground`() {
        assertEquals(
            "（低头看着手机）我有点想你了。",
            ProactiveDeliveryTextPolicy.finalText(
                planned = "我有点想你了。",
                rendered = "（低头看着手机）我有点想你了。"
            )
        )
    }
}
