package com.aster.companion.agent.world

import androidx.room.Room
import com.aster.companion.data.CompanionDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorldInteractionServiceTest {
    private lateinit var database: CompanionDatabase
    private lateinit var service: WorldInteractionService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CompanionDatabase::class.java
        ).allowMainThreadQueries().build()
        val dao = database.companionDao()
        service = WorldInteractionService(
            worldStateRepository = WorldStateRepository(dao),
            timelineManager = TimelineManager(dao)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `becoming not visible preserves the current user location`() = runBlocking {
        service.record(
            WorldInteraction(
                type = WorldInteractionType.MOVE,
                locationId = "home.kitchen",
                previousLocationId = "home.living_room",
                occurredAt = 1_000L,
                detail = "移动到厨房"
            )
        )

        service.recordCurrentUserNotVisible(lastAction = "离开应用", now = 2_000L)

        val user = requireNotNull(database.companionDao().worldUserState())
        assertEquals("home.kitchen", user.locationId)
        assertEquals("NOT_VISIBLE", user.visibility)
        assertEquals("离开应用", user.lastAction)
        assertEquals(2_000L, user.updatedAt)
    }
}
