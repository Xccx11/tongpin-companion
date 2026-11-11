package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityEffectsTest {

    @Test
    fun `watch tv reduces stress and loneliness`() {
        val e = ActivityEffects.calculate("watch_tv")
        assertTrue(e.stressDelta < 0)
        assertTrue(e.lonelinessDelta < 0)
        assertTrue(e.affectionDelta > 0)
    }

    @Test
    fun `positive mood boosts affection`() {
        val base = ActivityEffects.calculate("chat", moodPositive = false)
        val happy = ActivityEffects.calculate("chat", moodPositive = true)
        assertTrue(happy.affectionDelta >= base.affectionDelta)
    }

    @Test
    fun `family actions restore energy`() {
        val e = ActivityEffects.calculate("meal_remind")
        assertTrue(e.energyDelta > 0)
    }

    @Test
    fun `emotional actions release more stress`() {
        val e = ActivityEffects.calculate("vent")
        assertTrue(e.stressDelta <= -10)
    }

    @Test
    fun `unknown action has no effect`() {
        val e = ActivityEffects.calculate("unknown")
        assertEquals(0, e.affectionDelta)
    }
}
