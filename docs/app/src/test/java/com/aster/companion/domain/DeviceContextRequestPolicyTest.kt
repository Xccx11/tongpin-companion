package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceContextRequestPolicyTest {
    @Test
    fun `battery request becomes a no-confirmation Shizuku read`() {
        val action = requireNotNull(DeviceContextRequestPolicy.fallbackAction("你看下哥哥手机还有多少电"))

        assertEquals("device.read_battery", action.capabilityId)
        assertEquals(ActionTransport.SHIZUKU, action.transport)
        assertEquals(ConfirmationRequirement.NONE, action.confirmation)
    }

    @Test
    fun `time request receives a verified current time only when asked`() {
        assertNotNull(DeviceContextRequestPolicy.verifiedTimeContext("现在几点啦", 0L))
        assertTrue(DeviceContextRequestPolicy.verifiedTimeContext("陪我说说话", 0L) == null)
    }
}
