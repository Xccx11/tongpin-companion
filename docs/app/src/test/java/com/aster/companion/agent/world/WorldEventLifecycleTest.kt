package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorldEventLifecycleTest {

    @Test
    fun `planned opportunity enters evaluation`() {
        assertEquals(
            WorldEventStatus.EVALUATING,
            WorldEventLifecycle.transition(WorldEventStatus.PLANNED, WorldEventStatus.EVALUATING)
        )
    }

    @Test
    fun `planned opportunity cannot finish before confirmation`() {
        assertNull(
            WorldEventLifecycle.transition(WorldEventStatus.PLANNED, WorldEventStatus.FINISHED)
        )
    }

    @Test
    fun `terminal opportunity cannot transition again`() {
        assertNull(
            WorldEventLifecycle.transition(WorldEventStatus.CANCELLED, WorldEventStatus.EVALUATING)
        )
    }
}
