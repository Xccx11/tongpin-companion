package com.aster.companion.proactive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensingDiagnosticPolicyTest {
    @Test fun `writes only a changed state or a periodic heartbeat`() {
        assertTrue(SensingDiagnosticPolicy.shouldWrite(null, "event_recorded", 0L, 1_000L))
        assertFalse(SensingDiagnosticPolicy.shouldWrite("event_recorded", "event_recorded", 1_000L, 2_000L))
        assertTrue(SensingDiagnosticPolicy.shouldWrite("event_recorded", "event_recorded", 1_000L, 11 * 60_000L))
    }
}
