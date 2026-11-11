package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveEventFreshnessTest {
    @Test fun `separate fresh life sources can each enter proactive planning`() {
        assertTrue(ProactiveEventFreshness.mayEnterProactivePlanning("weather", occurredAt = 998_000L, now = 1_000_000L))
        assertTrue(ProactiveEventFreshness.mayEnterProactivePlanning("device:media", occurredAt = 998_000L, now = 1_000_000L))
    }

    @Test fun `a newly posted notification can enter proactive planning`() {
        assertTrue(ProactiveEventFreshness.mayEnterProactivePlanning("notification:parcel", occurredAt = 998_000L, now = 1_000_000L))
    }

    @Test fun `an expired notification is never replayed after a service restart`() {
        assertFalse(ProactiveEventFreshness.mayEnterProactivePlanning("notification:parcel", occurredAt = 820_000L, now = 1_000_000L))
    }

    @Test fun `non notification event freshness is controlled by its own source policy`() {
        assertTrue(ProactiveEventFreshness.mayEnterProactivePlanning("device:battery", occurredAt = 820_000L, now = 1_000_000L))
    }
}
