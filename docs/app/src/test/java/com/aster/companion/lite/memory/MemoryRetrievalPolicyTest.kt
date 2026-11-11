package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrievalPolicyTest {

    private fun fact(
        id: String,
        kind: MemoryKind,
        content: String,
        updatedAt: Long = 1_000L,
        blocked: Boolean = false
    ) = LiteMemoryFact(
        id = id,
        kind = kind,
        content = content,
        updatedAt = updatedAt,
        blockedFromMention = blocked
    )

    @Test
    fun `higher importance facts come first`() {
        val facts = listOf(
            fact("event", MemoryKind.EVENT, "上周一起去过海边", updatedAt = 9_000L),
            fact("plan", MemoryKind.PLAN, "周末打算去看电影", updatedAt = 1_000L)
        )

        val selected = MemoryRetrievalPolicy.select(facts)

        assertEquals(2, selected.size)
        assertTrue(selected[0].endsWith("周末打算去看电影"))
        assertTrue(selected[1].endsWith("上周一起去过海边"))
    }

    @Test
    fun `keyword hits are promoted above newer low importance facts`() {
        val facts = listOf(
            fact("a", MemoryKind.EVENT, "她最近在学吉他", updatedAt = 9_000L),
            fact("b", MemoryKind.PLAN, "她答应周末一起看电影", updatedAt = 1_000L)
        )

        val selected = MemoryRetrievalPolicy.select(facts, userText = "周末我们去看电影吧")

        assertTrue(selected.first().endsWith("她答应周末一起看电影"))
    }

    @Test
    fun `facts include a year month day hour minute timestamp prefix`() {
        val selected = MemoryRetrievalPolicy.select(
            listOf(fact("f", MemoryKind.PREFERENCE, "喜欢喝冰美式", updatedAt = 1_700_000_000_000L))
        )

        assertTrue(selected.single().contains("｜喜欢喝冰美式"))
        assertTrue(Regex("^\\d{4}年\\d{1,2}月\\d{1,2}日 \\d{2}:\\d{2}｜").containsMatchIn(selected.single()))
    }

    @Test
    fun `blocked and conversation memories never reach the prompt`() {
        val facts = listOf(
            fact("blocked", MemoryKind.PREFERENCE, "不喜欢香菜", blocked = true),
            fact("conversation", MemoryKind.CONVERSATION, "最近聊过一次晚饭"),
            fact("ok", MemoryKind.PREFERENCE, "喜欢喝冰美式")
        )

        val selected = MemoryRetrievalPolicy.select(facts)

        assertEquals(1, selected.size)
        assertTrue(selected.single().endsWith("喜欢喝冰美式"))
    }

    @Test
    fun `top k and character budget both cap the result`() {
        val many = (1..8).map { index -> fact("f$index", MemoryKind.PREFERENCE, "事实编号 $index", updatedAt = index.toLong()) }
        val budget = MemoryBudget(topK = 5, totalCharBudget = 400, perFactChars = 40)

        val selected = MemoryRetrievalPolicy.select(many, budget = budget)

        assertEquals(5, selected.size)
    }

    @Test
    fun `total character budget stops adding facts`() {
        val facts = (1..5).map { index ->
            fact("f$index", MemoryKind.PREFERENCE, "很长的一条事实" + "补".repeat(20), updatedAt = index.toLong())
        }
        val budget = MemoryBudget(topK = 5, totalCharBudget = 80, perFactChars = 40)

        val selected = MemoryRetrievalPolicy.select(facts, budget = budget)

        assertTrue(selected.sumOf { it.length } <= 80)
        assertTrue(selected.size < 5)
    }

    @Test
    fun `single fact body is truncated to the per fact limit`() {
        val long = "事实".repeat(60)
        val budget = MemoryBudget(perFactChars = 40)

        val selected = MemoryRetrievalPolicy.select(listOf(fact("f", MemoryKind.PREFERENCE, long)), budget = budget)
        val body = selected.single().substringAfter("｜")

        assertEquals(40, body.length)
    }

    @Test
    fun `duplicate contents are not repeated`() {
        val facts = listOf(
            fact("f1", MemoryKind.PREFERENCE, "喜欢冰美式", updatedAt = 2L),
            fact("f2", MemoryKind.PLAN, "喜欢冰美式", updatedAt = 1L)
        )

        val selected = MemoryRetrievalPolicy.select(facts)

        assertEquals(1, selected.size)
    }

    @Test
    fun `keywords extract latin words and chinese runs`() {
        val keywords = MemoryRetrievalPolicy.keywordsOf("周末去看 Movie 吧")

        assertTrue(keywords.contains("movie"))
        assertTrue(keywords.contains("周末"))
        assertTrue(keywords.contains("去看"))
    }

    @Test
    fun `trim keeps the most valuable facts under the character limit`() {
        val facts = (1..5).map { index -> fact("f$index", MemoryKind.PREFERENCE, "事实 $index", updatedAt = index.toLong()) }
        val budget = MemoryBudget(perCharacterLimit = 2)

        val trimmed = MemoryBudgetPolicy.trimToLimit(facts, budget)

        assertEquals(listOf("事实 5", "事实 4"), trimmed.map { it.content })
    }

    @Test
    fun `importance and injectability follow the documented kinds`() {
        assertEquals(3, MemoryBudgetPolicy.importanceOf(MemoryKind.PREFERENCE))
        assertEquals(3, MemoryBudgetPolicy.importanceOf(MemoryKind.PLAN))
        assertEquals(2, MemoryBudgetPolicy.importanceOf(MemoryKind.RELATIONSHIP))
        assertEquals(2, MemoryBudgetPolicy.importanceOf(MemoryKind.EVENT))
        assertEquals(0, MemoryBudgetPolicy.importanceOf(MemoryKind.CONVERSATION))
        assertFalse(MemoryBudgetPolicy.isInjectable(MemoryKind.CONVERSATION))
        assertTrue(MemoryBudgetPolicy.isInjectable(MemoryKind.PLAN))
    }
}
