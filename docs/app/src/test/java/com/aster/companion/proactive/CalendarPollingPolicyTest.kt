package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarPollingPolicyTest {
    @Test fun `reports calendar availability without exposing event content`() {
        assertEquals("permission_missing", CalendarPollingPolicy.status(hasPermission = false, eventCount = 0, querySucceeded = false))
        assertEquals("no_upcoming", CalendarPollingPolicy.status(hasPermission = true, eventCount = 0, querySucceeded = true))
        assertEquals("events_available", CalendarPollingPolicy.status(hasPermission = true, eventCount = 2, querySucceeded = true))
        assertEquals("query_failed", CalendarPollingPolicy.status(hasPermission = true, eventCount = 0, querySucceeded = false))
    }
}
