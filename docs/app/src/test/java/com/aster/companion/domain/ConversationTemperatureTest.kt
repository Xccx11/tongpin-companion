package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTemperatureTest {
    @Test
    fun `normalizes the selectable conversation temperature range`() {
        assertEquals(0.0, ConversationTemperature.normalize(-1.0), 0.0)
        assertEquals(0.75, ConversationTemperature.normalize(0.75), 0.0)
        assertEquals(2.0, ConversationTemperature.normalize(3.0), 0.0)
    }

    @Test
    fun `normalizes supported reasoning effort values`() {
        assertEquals("high", ConversationReasoning.normalizeEffort("HIGH"))
        assertEquals("medium", ConversationReasoning.normalizeEffort("unsupported"))
    }
}
