package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeSnapshotPolicyTest {
    @Test fun `keeps only the newest fresh foreground application`() {
        val now = 1_000_000L
        val facts = LifeSnapshotPolicy.currentFacts(
            listOf(
                LifeSnapshotInput("device:foreground_app", "app.old", now - 9 * 60_000L),
                LifeSnapshotInput("device:foreground_app", "app.current", now - 60_000L)
            ),
            now
        )

        assertEquals(listOf("app.current"), facts.map { it.summary })
    }

    @Test fun `drops stale life events and internal relationship pulses`() {
        val now = 1_000_000L
        val facts = LifeSnapshotPolicy.currentFacts(
            listOf(
                LifeSnapshotInput("weather", "rain", now - 91 * 60_000L),
                LifeSnapshotInput("system:presence:thread:character", "relationship pulse", now - 1_000L),
                LifeSnapshotInput("device:battery", "battery 12 percent", now - 1_000L)
            ),
            now
        )

        assertEquals(listOf("battery 12 percent"), facts.map { it.summary })
        assertFalse(facts.any { it.source.startsWith("system:presence") })
    }

    @Test fun `labels trusted facts with their source and observation time`() {
        val facts = LifeSnapshotPolicy.currentFacts(
            listOf(LifeSnapshotInput("notification:parcel", "parcel delivered", 900L)),
            now = 1_000L
        )

        assertTrue(LifeSnapshotPolicy.promptLines(facts).single().contains("source=notification:parcel"))
        assertTrue(LifeSnapshotPolicy.promptLines(facts).single().contains("observed_at=900"))
    }

    @Test fun `drops an old notification before a later relationship pulse can see it`() {
        val now = 1_000_000L
        val facts = LifeSnapshotPolicy.currentFacts(
            listOf(LifeSnapshotInput("notification:parcel", "parcel delivered", now - 3 * 60_000L - 1)),
            now
        )

        assertTrue(facts.isEmpty())
    }

    @Test fun `keeps a fresh approximate place without retaining coordinates`() {
        val now = 1_000_000L
        val facts = LifeSnapshotPolicy.currentFacts(
            listOf(LifeSnapshotInput("device:place", "Current user area: Pudong.", now - 30 * 60_000L)),
            now
        )

        assertEquals(listOf("Current user area: Pudong."), facts.map { it.summary })
    }

    @Test fun `keeps a recent unlock state as immediate life context`() {
        val now = 1_000_000L
        val facts = LifeSnapshotPolicy.currentFacts(
            listOf(LifeSnapshotInput("device:screen", "The user just unlocked their phone.", now - 60_000L)),
            now
        )

        assertEquals(listOf("The user just unlocked their phone."), facts.map { it.summary })
    }
}
