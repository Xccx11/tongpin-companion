package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProactiveCharacterSelectorTest {
    @Test
    fun `uses every enabled private character in stable card order`() {
        assertEquals(
            listOf("lan", "xue"),
            ProactiveCharacterSelector.resolveAll(setOf("xue", "lan"), listOf("lan", "xue", "yuan"))
        )
    }

    @Test
    fun `uses all available characters until the user chooses a subset`() {
        assertEquals(
            listOf("lan", "xue"),
            ProactiveCharacterSelector.resolveAll(emptySet(), listOf("lan", "xue"))
        )
    }

    @Test
    fun `returns null when there are no characters`() {
        assertNull(ProactiveCharacterSelector.resolveAll(setOf("lan"), emptyList()).firstOrNull())
    }

    @Test
    fun `supports explicitly disabling proactive messages for every character`() {
        assertEquals(emptyList<String>(), ProactiveCharacterSelector.resolveAll(emptySet(), listOf("lan", "xue"), true))
    }
}
