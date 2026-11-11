package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRollupPolicyTest {

    @Test
    fun `parses typed fact lines`() {
        val drafts = MemoryRollupPolicy.parse(
            """
            PREFERENCE: 喜欢喝冰美式
            PLAN: 周末打算去看电影
            RELATIONSHIP: 把用户当成最亲近的人
            """.trimIndent()
        )

        assertEquals(
            listOf(
                MemoryDraft(MemoryKind.PREFERENCE, "喜欢喝冰美式"),
                MemoryDraft(MemoryKind.PLAN, "周末打算去看电影"),
                MemoryDraft(MemoryKind.RELATIONSHIP, "把用户当成最亲近的人")
            ),
            drafts
        )
    }

    @Test
    fun `none means nothing worth remembering`() {
        assertEquals(emptyList<MemoryDraft>(), MemoryRollupPolicy.parse("NONE"))
        assertEquals(emptyList<MemoryDraft>(), MemoryRollupPolicy.parse("  none "))
    }

    @Test
    fun `untyped lines fall back to event and lists are trimmed to three`() {
        val drafts = MemoryRollupPolicy.parse(
            """
            用户提过想养猫
            PLAN: 下个月去看海
            EVENT: 昨天加班到很晚
            EVENT: 早上没吃早饭
            """.trimIndent()
        )

        assertEquals(MemoryRollupPolicy.MAX_FACTS_PER_ROLLUP, drafts?.size)
        assertEquals(MemoryKind.EVENT, drafts?.first()?.kind)
        assertEquals("用户提过想养猫", drafts?.first()?.content)
    }

    @Test
    fun `markdown and empty output are rejected so the caller can retry`() {
        assertNull(MemoryRollupPolicy.parse(""))
        assertNull(MemoryRollupPolicy.parse("```\nPREFERENCE: 喜欢猫\n```"))
        assertNull(MemoryRollupPolicy.parse("# 记忆\nPREFERENCE: 喜欢猫"))
    }

    @Test
    fun `facts are truncated to the per fact limit`() {
        val long = "很".repeat(80)

        val drafts = MemoryRollupPolicy.parse("EVENT: $long")

        assertEquals(MemoryRollupPolicy.MAX_FACT_CHARS, drafts?.single()?.content?.length)
    }

    @Test
    fun `duplicates are detected by containment or heavy overlap`() {
        val existing = listOf("喜欢喝冰美式", "周末打算去看电影")

        assertEquals("喜欢喝冰美式", MemoryRollupPolicy.duplicateOf("喜欢喝冰美式", existing))
        assertEquals("喜欢喝冰美式", MemoryRollupPolicy.duplicateOf("她喜欢喝冰美式", existing))
        assertNull(MemoryRollupPolicy.duplicateOf("养了一只叫团子的猫", existing))
    }

    @Test
    fun `rollup triggers only after enough new messages and enough time`() {
        val trigger = MemoryRollupPolicy.Trigger()

        assertTrue(MemoryRollupPolicy.shouldTrigger(newMessageCount = 10, sinceLastRollupMs = 11 * 60_000L, trigger = trigger))
        assertFalse(MemoryRollupPolicy.shouldTrigger(newMessageCount = 9, sinceLastRollupMs = 11 * 60_000L, trigger = trigger))
        assertFalse(MemoryRollupPolicy.shouldTrigger(newMessageCount = 12, sinceLastRollupMs = 60_000L, trigger = trigger))
    }
}
