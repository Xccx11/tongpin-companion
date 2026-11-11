package com.aster.companion.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFactOwnershipInstructionTest {
    @Test
    fun `states that every sensed device fact belongs only to the user`() {
        val instruction = DeviceFactOwnershipInstruction.text()

        assertTrue(instruction.contains("belongs only to the user"))
        assertTrue(instruction.contains("must not call it your own phone"))
        assertTrue(instruction.contains("weather, location, battery, app activity, and media"))
    }
}
