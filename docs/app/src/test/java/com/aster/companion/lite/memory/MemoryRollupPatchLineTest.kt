package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 压缩输出里的 `PATCH:` 行**是给设定提案系统的，不是记忆**。
 *
 * 不跳过它们的话，`splitKind` 会把 `PATCH: 习惯早起` 当成一条 `EVENT` 事实写进记忆库——
 * 于是"她是不是真的变了"这件事会同时污染"记得的事"。
 */
class MemoryRollupPatchLineTest {

    @Test
    fun `提案行不会变成记忆`() {
        val drafts = MemoryRollupPolicy.parse("PREFERENCE: 喜欢冰美式\nPATCH[HABIT]: 习惯早起")

        assertNotNull(drafts)
        assertEquals(1, drafts!!.size)
        assertEquals(MemoryKind.PREFERENCE, drafts.single().kind)
    }

    @Test
    fun `只有提案行时等于没有新事实`() {
        assertEquals(emptyList<MemoryDraft>(), MemoryRollupPolicy.parse("PATCH: 最近在学做菜"))
    }

    @Test
    fun `大小写与前后横线都容忍`() {
        val drafts = MemoryRollupPolicy.parse("- patch: 习惯早起\nEVENT: 昨天去了医院")

        assertEquals(1, drafts!!.size)
        assertEquals(MemoryKind.EVENT, drafts.single().kind)
    }

    /** 别的坏行仍然要整体拒绝——跳过提案行不等于放宽校验。 */
    @Test
    fun `提案行之外的坏行仍然整体拒绝`() {
        assertNull(MemoryRollupPolicy.parse("PATCH: 习惯早起\n```json"))
    }
}
