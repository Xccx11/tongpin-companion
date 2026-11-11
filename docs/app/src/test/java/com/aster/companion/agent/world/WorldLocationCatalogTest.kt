package com.aster.companion.agent.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldLocationCatalogTest {

    @Test
    fun `default locations contain the first phase home scenes`() {
        val locations = WorldLocationCatalog.defaults(now = 1L)

        assertEquals(
            setOf(
                "home",
                "home.living_room",
                "home.kitchen",
                "home.bedroom",
                "home.bathroom",
                "outside",
                "outside.classroom",
                "outside.library",
                "outside.canteen",
                "outside.cafe",
                "outside.milk_tea",
                "outside.supermarket",
                "outside.convenience",
                "outside.mall",
                "outside.street",
                "outside.park",
                "outside.gym",
                "outside.playground",
                "outside.office",
                "outside.restaurant",
                "outside.metro",
                "outside.bookstore",
                "outside.friend_home",
                "outside.night_market",
                "outside.internet_cafe",
                "outside.fitting_room",
                "outside.hospital",
                "outside.hair_salon"
            ),
            locations.map { it.id }.toSet()
        )
        assertTrue(locations.all { it.createdAt == 1L && it.updatedAt == 1L })
    }
}
