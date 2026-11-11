package com.aster.companion.domain

import com.aster.companion.data.MemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiaryEntryPolicyTest {
    @Test
    fun `refreshing a private conversation updates its existing diary for the same day`() {
        // 只认 DIARY 类型（以前测试拿 EVENT 当日记，新实现会把它过滤掉）。
        val diary = MemoryEntity("today", "lan", "private-lan", MemoryKind.DIARY, "old", false, 1_720_000_000_000, 1_720_000_000_000)

        assertEquals("today", DiaryEntryPolicy.entryIdToRefresh(listOf(diary), "private-lan", 1_720_020_000_000))
    }

    @Test
    fun `a different conversation or day starts a new diary entry`() {
        val oldDiary = MemoryEntity("old", "lan", "private-lan", MemoryKind.DIARY, "old", false, 1_720_000_000_000, 1_720_000_000_000)

        assertNull(DiaryEntryPolicy.entryIdToRefresh(listOf(oldDiary), "private-yuan", 1_720_020_000_000))
        assertNull(DiaryEntryPolicy.entryIdToRefresh(listOf(oldDiary), "private-lan", 1_720_086_400_000))
    }
}
