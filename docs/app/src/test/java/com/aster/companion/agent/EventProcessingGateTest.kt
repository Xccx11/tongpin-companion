package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventProcessingGateTest {
    @Test fun `the same event cannot be processed concurrently`() {
        val gate = EventProcessingGate()

        assertTrue(gate.tryAcquire("courier-event"))
        assertFalse(gate.tryAcquire("courier-event"))

        gate.release("courier-event")
        assertTrue(gate.tryAcquire("courier-event"))
    }
}
