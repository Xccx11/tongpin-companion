package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkStatePolicyTest {
    @Test fun `classifies only the network category without retaining identifiers`() {
        assertEquals("wifi", NetworkStatePolicy.classify(hasNetwork = true, validated = true, wifi = true, cellular = false))
        assertEquals("cellular", NetworkStatePolicy.classify(hasNetwork = true, validated = true, wifi = false, cellular = true))
        assertEquals("offline", NetworkStatePolicy.classify(hasNetwork = false, validated = false, wifi = false, cellular = false))
    }

    @Test fun `refreshes unchanged state only after its bounded window`() {
        assertEquals(false, NetworkStatePolicy.shouldEmit("wifi", "wifi", 1_000L, 1_000L + 14 * 60_000L))
        assertEquals(true, NetworkStatePolicy.shouldEmit("wifi", "wifi", 1_000L, 1_000L + 15 * 60_000L))
        assertEquals(true, NetworkStatePolicy.shouldEmit("wifi", "offline", 1_000L, 1_001L))
    }
}
