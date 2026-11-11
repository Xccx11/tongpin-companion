package com.aster.companion.system

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedShizukuCommandTest {
    @Test
    fun `opens an allowed app with a fixed argument vector`() {
        assertEquals(
            listOf("monkey", "-p", "com.twitter.android", "1"),
            FixedShizukuCommand.forAction("app.open_known", "com.twitter.android")
        )
    }
}
