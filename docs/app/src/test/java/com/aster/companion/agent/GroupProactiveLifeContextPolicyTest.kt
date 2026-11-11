package com.aster.companion.agent

import com.aster.companion.proactive.LifeSnapshotFact
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupProactiveLifeContextPolicyTest {
    @Test
    fun `keeps current life signals but excludes raw notifications sms and payload identifiers`() {
        val lines = GroupProactiveLifeContextPolicy.lines(
            listOf(
                LifeSnapshotFact("weather", "Rain, 22C", 100L),
                LifeSnapshotFact("device:media", "Currently playing: song title", 99L),
                LifeSnapshotFact("notification:parcel", "Raw parcel notification text", 98L),
                LifeSnapshotFact("sms", "Private SMS body", 97L),
                LifeSnapshotFact("device:place", "Current user area: Pudong", 96L),
                LifeSnapshotFact("device:foreground_app", "Current foreground app: Video (com.example.video)", 95L)
            )
        )

        val joined = lines.joinToString("\n")
        assertTrue(joined.contains("weather"))
        assertTrue(joined.contains("song title"))
        assertTrue(joined.contains("Current user area: Pudong"))
        assertTrue(joined.contains("Video"))
        assertFalse(joined.contains("Raw parcel notification text"))
        assertFalse(joined.contains("Private SMS body"))
        assertFalse(joined.contains("com.example.video"))
    }
}
