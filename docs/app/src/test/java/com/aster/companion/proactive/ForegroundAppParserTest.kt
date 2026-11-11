package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppParserTest {
    @Test fun `extracts the package from the top resumed activity`() {
        val output = """
            topResumedActivity=ActivityRecord{7fa u0 com.twitter.android/com.twitter.app.main.MainActivity t42}
        """.trimIndent()

        assertEquals("com.twitter.android", ForegroundAppParser.parse(output))
    }

    @Test fun `does not treat arbitrary dumpsys text as a foreground application`() {
        assertNull(ForegroundAppParser.parse("mResumedActivity=null"))
        assertNull(ForegroundAppParser.parse("FAILED: permission denied"))
    }
}
