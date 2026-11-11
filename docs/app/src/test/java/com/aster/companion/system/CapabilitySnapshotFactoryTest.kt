package com.aster.companion.system

import com.aster.companion.domain.ConfirmationRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitySnapshotFactoryTest {
    @Test
    fun `Shizuku actions are hidden until binder and authorization are ready`() {
        val offline = CapabilitySnapshotFactory.create(ShizukuStatus(false, false, false))
        val online = CapabilitySnapshotFactory.create(ShizukuStatus(true, true, false))

        assertFalse(offline.capabilities.any { it.id == "device.read_battery" })
        assertTrue(online.capabilities.any { it.id == "device.read_battery" })
    }

    @Test
    fun `character initiated image generation runs without a separate approval step`() {
        val imageCapability = CapabilitySnapshotFactory.create(ShizukuStatus(false, false, false))
            .capabilities.first { it.id == "companion.generate_image" }

        assertEquals(ConfirmationRequirement.NONE, imageCapability.confirmation)
    }

    @Test
    fun `setting an alarm is shown only with Shizuku and always requires confirmation`() {
        val offline = CapabilitySnapshotFactory.create(ShizukuStatus(false, false, false))
        val online = CapabilitySnapshotFactory.create(ShizukuStatus(true, true, false))

        assertFalse(offline.capabilities.any { it.id == "device.set_alarm" })
        assertEquals(ConfirmationRequirement.REQUIRED, online.capabilities.first { it.id == "device.set_alarm" }.confirmation)
    }
}
