package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterDistancePolicyTest {
    @Test
    fun `nearby locations`() {
        assertEquals("就在附近", CharacterDistancePolicy.distanceLabel("客厅"))
        assertEquals("就在附近", CharacterDistancePolicy.distanceLabel("阳台"))
    }

    @Test
    fun `few steps locations`() {
        assertEquals("几步远", CharacterDistancePolicy.distanceLabel("厨房"))
        assertEquals("几步远", CharacterDistancePolicy.distanceLabel("卧室"))
    }

    @Test
    fun `far locations`() {
        assertEquals("有点远", CharacterDistancePolicy.distanceLabel("楼上"))
        assertEquals("有点远", CharacterDistancePolicy.distanceLabel("外面"))
    }

    @Test
    fun `blank returns null`() {
        assertNull(CharacterDistancePolicy.distanceLabel(null))
        assertNull(CharacterDistancePolicy.distanceLabel(""))
    }
}
