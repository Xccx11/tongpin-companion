package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnansweredEventTargetTest {
    @Test fun `round trips the private thread and character target`() {
        val source = UnansweredEventTarget.source("thread-1", "character-1")

        assertEquals(UnansweredEventTarget("thread-1", "character-1"), UnansweredEventTarget.parse(source))
    }

    @Test fun `does not parse unrelated system events`() {
        assertNull(UnansweredEventTarget.parse("device:foreground_app"))
    }

    @Test fun `round trips a later presence pulse independently from the follow up pulse`() {
        val source = UnansweredEventTarget.presenceSource("thread-1", "character-1")

        assertEquals(
            UnansweredEventTarget("thread-1", "character-1", RelationshipPulseKind.PRESENCE),
            UnansweredEventTarget.parse(source)
        )
    }
}
