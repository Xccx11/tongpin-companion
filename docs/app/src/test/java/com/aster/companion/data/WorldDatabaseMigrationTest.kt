package com.aster.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldDatabaseMigrationTest {

    @Test
    fun `world engine migration advances v26 database to v27`() {
        assertEquals(26, CompanionDatabase.MIGRATION_26_27.startVersion)
        assertEquals(27, CompanionDatabase.MIGRATION_26_27.endVersion)
    }

    @Test
    fun `conversation boundary migration advances v27 database to v28`() {
        assertEquals(27, CompanionDatabase.MIGRATION_27_28.startVersion)
        assertEquals(28, CompanionDatabase.MIGRATION_27_28.endVersion)
    }

    @Test
    fun `image protocol migration advances v28 database to v29`() {
        assertEquals(28, CompanionDatabase.MIGRATION_28_29.startVersion)
        assertEquals(29, CompanionDatabase.MIGRATION_28_29.endVersion)
    }

    @Test
    fun `life evidence migration advances v29 database to v30`() {
        assertEquals(29, CompanionDatabase.MIGRATION_29_30.startVersion)
        assertEquals(30, CompanionDatabase.MIGRATION_29_30.endVersion)
    }
}
