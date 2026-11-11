package com.aster.companion.domain

object DefaultCharacterSeedPolicy {
    fun shouldSeed(hasSeededBefore: Boolean, hasCharacters: Boolean): Boolean = false
}

object ProactiveDeliveryGuard {
    fun canDeliver(characterExists: Boolean, threadExists: Boolean): Boolean =
        characterExists && threadExists
}
