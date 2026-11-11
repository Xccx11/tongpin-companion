package com.aster.companion.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class UserRealityBoundaryInstructionTest {
    @Test
    fun `allows only verified context to establish user reality`() {
        val instruction = UserRealityBoundaryInstruction.text()

        assertTrue(instruction.contains("recent conversation, confirmed memory, and fresh sensed facts"))
        assertTrue(instruction.contains("must not invent or assume"))
        assertTrue(instruction.lowercase().contains("the character's own feelings"))
    }
}
