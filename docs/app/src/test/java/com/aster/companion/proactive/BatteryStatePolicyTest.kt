package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryStatePolicyTest {
    @Test fun `emits a state change and periodically refreshes a stable state`() {
        assertEquals(true, BatteryStatePolicy.shouldEmit(null, "charging:28", 0L, 1_000L))
        assertEquals(false, BatteryStatePolicy.shouldEmit("charging:28", "charging:28", 1_000L, 10 * 60_000L))
        assertEquals(true, BatteryStatePolicy.shouldEmit("charging:28", "charging:28", 1_000L, 16 * 60_000L))
        assertEquals(true, BatteryStatePolicy.shouldEmit("charging:28", "discharging:28", 1_000L, 2_000L))
    }
}
