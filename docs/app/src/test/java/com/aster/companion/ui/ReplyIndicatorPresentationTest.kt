package com.aster.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyIndicatorPresentationTest {
    @Test
    fun `group indicator uses active speaker rather than group title`() {
        assertEquals("Lin Jianxia 正在思考...", ReplyIndicatorPresentation.label("Family group", "Lin Jianxia", true))
    }
}
