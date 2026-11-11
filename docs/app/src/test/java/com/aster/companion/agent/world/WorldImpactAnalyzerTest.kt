package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldImpactAnalyzerTest {

    @Test
    fun `interrupting meal preparation reevaluates dependent dinner`() {
        val meal = opportunity(
            id = "meal",
            locationId = "home.kitchen",
            required = listOf("linxi"),
            dependencies = emptyList()
        )
        val dinner = opportunity(
            id = "dinner",
            locationId = "home.living_room",
            required = listOf("linxi", "gongyuqin"),
            dependencies = listOf("meal")
        )

        val report = WorldImpactAnalyzer().analyze(
            WorldInteraction(
                type = WorldInteractionType.INVITE,
                locationId = "home.kitchen",
                targetCharacterIds = setOf("linxi"),
                occurredAt = 1_000L
            ),
            listOf(meal, dinner)
        )

        assertEquals(WorldEventImpact.REEVALUATE, report.impacts.getValue("meal"))
        assertEquals(WorldEventImpact.REEVALUATE, report.impacts.getValue("dinner"))
    }

    @Test
    fun `confirmed activity is not reopened by a later candidate interaction`() {
        val confirmed = opportunity(
            id = "confirmed",
            locationId = "home.kitchen",
            required = listOf("linxi"),
            dependencies = emptyList()
        ).copy(status = WorldEventStatus.CONFIRMED)

        val report = WorldImpactAnalyzer().analyze(
            WorldInteraction(
                type = WorldInteractionType.CHAT,
                locationId = "home.kitchen",
                targetCharacterIds = setOf("linxi"),
                occurredAt = 1_000L
            ),
            listOf(confirmed)
        )

        assertEquals(emptyMap<String, WorldEventImpact>(), report.impacts)
    }

    @Test
    fun `direct interruption follows its strategy while dependencies are reevaluated`() {
        val interrupted = opportunity(
            id = "interrupted",
            locationId = "home.kitchen",
            required = listOf("linxi"),
            dependencies = emptyList()
        ).copy(interruptStrategy = WorldEventInterruptStrategy.CANCEL)
        val dependent = opportunity(
            id = "dependent",
            locationId = "home.living_room",
            required = listOf("gongyuqin"),
            dependencies = listOf("interrupted")
        )

        val report = WorldImpactAnalyzer().analyze(
            WorldInteraction(
                type = WorldInteractionType.CHAT,
                locationId = "home.kitchen",
                targetCharacterIds = setOf("linxi"),
                occurredAt = 1_000L
            ),
            listOf(interrupted, dependent)
        )

        assertEquals(WorldEventImpact.CANCEL, report.impacts.getValue("interrupted"))
        assertEquals(WorldEventImpact.REEVALUATE, report.impacts.getValue("dependent"))
    }

    @Test
    fun `free action reevaluates only opportunities at the current location`() {
        val currentRoom = opportunity(
            id = "current-room",
            locationId = "home.living_room",
            required = listOf("linxi"),
            dependencies = emptyList()
        )
        val elsewhere = opportunity(
            id = "elsewhere",
            locationId = "home.kitchen",
            required = listOf("gongyuqin"),
            dependencies = emptyList()
        )

        val report = WorldImpactAnalyzer().analyze(
            WorldInteraction(
                type = WorldInteractionType.FREE_ACTION,
                locationId = "home.living_room",
                occurredAt = 1_000L,
                detail = "打开客厅音响"
            ),
            listOf(currentRoom, elsewhere)
        )

        assertEquals(mapOf("current-room" to WorldEventImpact.REEVALUATE), report.impacts)
    }

    @Test
    fun `moving reevaluates opportunities at both origin and destination`() {
        val origin = opportunity(
            id = "origin",
            locationId = "home.living_room",
            required = listOf("linxi"),
            dependencies = emptyList()
        )
        val destination = opportunity(
            id = "destination",
            locationId = "home.kitchen",
            required = listOf("gongyuqin"),
            dependencies = emptyList()
        )

        val report = WorldImpactAnalyzer().analyze(
            WorldInteraction(
                type = WorldInteractionType.MOVE,
                locationId = "home.kitchen",
                previousLocationId = "home.living_room",
                occurredAt = 1_000L
            ),
            listOf(origin, destination)
        )

        assertEquals(
            mapOf(
                "origin" to WorldEventImpact.REEVALUATE,
                "destination" to WorldEventImpact.REEVALUATE
            ),
            report.impacts
        )
    }

    private fun opportunity(
        id: String,
        locationId: String,
        required: List<String>,
        dependencies: List<String>
    ) = WorldOpportunity(
        id = id,
        timelineId = "t",
        scheduledAt = 2_000L,
        latestStartAt = 3_000L,
        durationMinutes = 30,
        type = "activity",
        initiatorCharacterId = required.first(),
        requiredCharacterIds = required,
        locationId = locationId,
        description = id,
        prerequisiteEventIds = dependencies
    )
}
