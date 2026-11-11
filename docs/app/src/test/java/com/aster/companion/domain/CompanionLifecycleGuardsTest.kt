package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLifecycleGuardsTest {
    @Test
    fun `does not seed default characters again after a user has removed every character`() {
        assertFalse(DefaultCharacterSeedPolicy.shouldSeed(hasSeededBefore = true, hasCharacters = false))
    }

    @Test
    fun `never auto creates a character in an empty companion space`() {
        assertFalse(DefaultCharacterSeedPolicy.shouldSeed(hasSeededBefore = false, hasCharacters = false))
        assertFalse(DefaultCharacterSeedPolicy.shouldSeed(hasSeededBefore = false, hasCharacters = true))
    }

    @Test
    fun `does not deliver a completed proactive response after its character or private thread was removed`() {
        assertFalse(ProactiveDeliveryGuard.canDeliver(characterExists = false, threadExists = true))
        assertFalse(ProactiveDeliveryGuard.canDeliver(characterExists = true, threadExists = false))
        assertTrue(ProactiveDeliveryGuard.canDeliver(characterExists = true, threadExists = true))
    }
}
