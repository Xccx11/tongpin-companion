package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEmbeddingPolicyTest {

    private fun fact(id: String, kind: MemoryKind = MemoryKind.PREFERENCE, content: String = "事实 $id", updatedAt: Long = 1L, blocked: Boolean = false) =
        LiteMemoryFact(
            id = id,
            kind = kind,
            content = content,
            updatedAt = updatedAt,
            blockedFromMention = blocked
        )

    @Test
    fun `cosine measures vector similarity`() {
        assertEquals(1.0, MemoryEmbeddingPolicy.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f))!!, 1e-6)
        assertEquals(0.0, MemoryEmbeddingPolicy.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))!!, 1e-6)
        assertEquals(-1.0, MemoryEmbeddingPolicy.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f))!!, 1e-6)
    }

    @Test
    fun `unusable vectors fall back instead of throwing`() {
        assertNull(MemoryEmbeddingPolicy.cosine(floatArrayOf(), floatArrayOf(1f)))
        assertNull(MemoryEmbeddingPolicy.cosine(floatArrayOf(1f, 2f), floatArrayOf(1f)))
        assertNull(MemoryEmbeddingPolicy.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)))
    }

    @Test
    fun `rank orders facts by similarity to the query`() {
        val query = floatArrayOf(1f, 0f)
        val ranked = MemoryEmbeddingPolicy.rank(
            listOf(
                fact("far") to floatArrayOf(0f, 1f),
                fact("near") to floatArrayOf(0.9f, 0.1f),
                fact("broken") to floatArrayOf(1f)
            ),
            query
        )

        assertEquals(listOf("near", "far"), ranked.map { it.id })
    }

    @Test
    fun `selection keeps top k and drops blocked or conversation memories`() {
        val query = floatArrayOf(1f, 0f)
        val facts = listOf(
            fact("blocked", content = "不喜欢香菜", blocked = true) to floatArrayOf(1f, 0f),
            fact("conversation", kind = MemoryKind.CONVERSATION, content = "聊过一次晚饭") to floatArrayOf(1f, 0f),
            fact("a", content = "喜欢冰美式") to floatArrayOf(1f, 0f),
            fact("b", content = "周末看电影") to floatArrayOf(0.8f, 0.2f)
        )

        val selected = MemoryEmbeddingPolicy.selectBySimilarity(facts, query, MemoryBudget(topK = 1))

        assertEquals(1, selected.size)
        assertTrue(selected.single().endsWith("喜欢冰美式"))
    }

    @Test
    fun `selection respects the character budget`() {
        val query = floatArrayOf(1f, 0f)
        val facts = (1..4).map { index ->
            fact("f$index", content = "事实编号 $index" + "补".repeat(20)) to floatArrayOf(1f, 0f)
        }

        val selected = MemoryEmbeddingPolicy.selectBySimilarity(facts, query, MemoryBudget(topK = 4, totalCharBudget = 200))

        assertTrue(selected.sumOf { it.length } <= 200)
        assertFalse(selected.isEmpty())
    }
}
