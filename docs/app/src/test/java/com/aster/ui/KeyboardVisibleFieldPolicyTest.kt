package com.aster.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardVisibleFieldPolicyTest {
    @Test
    fun `moves a focused field into view only after the keyboard is visible`() {
        assertTrue(KeyboardVisibleFieldPolicy.shouldBringIntoView(isFocused = true, isImeVisible = true))
        assertFalse(KeyboardVisibleFieldPolicy.shouldBringIntoView(isFocused = false, isImeVisible = true))
        assertFalse(KeyboardVisibleFieldPolicy.shouldBringIntoView(isFocused = true, isImeVisible = false))
    }
}
