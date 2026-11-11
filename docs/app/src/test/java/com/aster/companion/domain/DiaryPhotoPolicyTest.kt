package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryPhotoPolicyTest {
    @Test
    fun `keeps a character photo on the diary page for its own calendar day`() {
        assertTrue(
            DiaryPhotoPolicy.belongsToDiary(
                photoCharacterId = "lan",
                diaryCharacterId = "lan",
                photoTimestamp = 1_720_000_000_000L,
                diaryTimestamp = 1_720_020_000_000L
            )
        )
    }

    @Test
    fun `does not mix another character or another day into the diary page`() {
        assertFalse(DiaryPhotoPolicy.belongsToDiary("lan", "yuan", 1_720_000_000_000L, 1_720_020_000_000L))
        assertFalse(DiaryPhotoPolicy.belongsToDiary("lan", "lan", 1_720_000_000_000L, 1_720_086_400_000_000L))
    }
}
