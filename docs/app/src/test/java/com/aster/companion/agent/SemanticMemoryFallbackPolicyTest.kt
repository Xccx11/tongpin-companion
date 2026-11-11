package com.aster.companion.agent

import com.aster.companion.data.SemanticMemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticMemoryFallbackPolicyTest {
    @Test
    fun `fallback ranks importance first and recency second`() {
        val older = memory("older", importance = 80, updatedAt = 1L)
        val newer = memory("newer", importance = 80, updatedAt = 2L)
        val important = memory("important", importance = 90, updatedAt = 0L)

        assertEquals(
            listOf("important", "newer"),
            SemanticMemoryFallbackPolicy.rank(listOf(older, newer, important), 2).map { it.id }
        )
    }

    private fun memory(id: String, importance: Int, updatedAt: Long) = SemanticMemoryEntity(
        id = id,
        characterId = "character",
        threadId = null,
        timelineEventId = null,
        content = id,
        embedding = null,
        importance = importance,
        retrievable = true,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )
}
