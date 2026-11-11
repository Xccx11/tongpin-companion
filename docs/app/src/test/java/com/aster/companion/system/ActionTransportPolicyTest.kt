package com.aster.companion.system

import com.aster.companion.domain.ActionTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionTransportPolicyTest {
    @Test fun `blocks Shizuku actions before confirmation when Shizuku is unavailable`() {
        assertEquals("Shizuku is unavailable", ActionTransportPolicy.errorFor("device.set_alarm", ActionTransport.SHIZUKU, shizukuReady = false))
    }

    @Test fun `requires the declared transport to match the fixed capability`() {
        assertEquals("Invalid action transport", ActionTransportPolicy.errorFor("device.set_alarm", ActionTransport.LOCAL, shizukuReady = true))
        assertNull(ActionTransportPolicy.errorFor("companion.generate_image", ActionTransport.LOCAL, shizukuReady = false))
        assertNull(ActionTransportPolicy.errorFor("companion.send_private_message", ActionTransport.LOCAL, shizukuReady = false))
        assertNull(ActionTransportPolicy.errorFor("companion.send_group_message", ActionTransport.LOCAL, shizukuReady = false))
    }
}
