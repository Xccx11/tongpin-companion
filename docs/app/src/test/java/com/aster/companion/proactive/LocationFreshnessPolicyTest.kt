package com.aster.companion.proactive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessPolicyTest {
    @Test fun `accepts a recent plausible location`() {
        assertTrue(LocationFreshnessPolicy.isUsable(31.2304, 121.4737, 35f, observedAt = 990_000L, now = 1_000_000L))
    }

    @Test fun `rejects stale placeholder and low quality locations`() {
        assertFalse(LocationFreshnessPolicy.isUsable(0.0, 0.0, 20f, observedAt = 999_000L, now = 1_000_000L))
        assertFalse(LocationFreshnessPolicy.isUsable(31.2304, 121.4737, 20f, observedAt = 1_000L, now = 1_000_000L))
        assertFalse(LocationFreshnessPolicy.isUsable(31.2304, 121.4737, 6_000f, observedAt = 999_000L, now = 1_000_000L))
    }
}
