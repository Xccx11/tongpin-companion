package com.aster.companion.data

import com.aster.companion.domain.EventState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTransferPolicyTest {
    @Test
    fun `migrated sensing events are retained as consumed and cannot replay`() {
        assertEquals(EventState.CONSUMED, DeviceTransferPolicy.importEventState(EventState.NEW))
        assertEquals(EventState.CONSUMED, DeviceTransferPolicy.importEventState(EventState.DEFERRED))
    }

    @Test
    fun `migrated pending actions require a fresh user decision`() {
        assertEquals("MIGRATED_REVIEW_REQUIRED", DeviceTransferPolicy.importActionState("WAITING_CONFIRMATION"))
        assertEquals("MIGRATED_REVIEW_REQUIRED", DeviceTransferPolicy.importActionState("RUNNING"))
    }

    @Test
    fun `completed actions preserve their final state`() {
        assertEquals("COMPLETED", DeviceTransferPolicy.importActionState("COMPLETED"))
        assertEquals("FAILED", DeviceTransferPolicy.importActionState("FAILED"))
    }
}
