package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticMemoryRankerTest {
    @Test
    fun `returns the closest retrievable memory first`() {
        val ranked = SemanticMemoryRanker.rank(
            query = floatArrayOf(1f, 0f),
            candidates = listOf(
                SemanticVectorCandidate("related", floatArrayOf(0.9f, 0.1f), true),
                SemanticVectorCandidate("unrelated", floatArrayOf(0f, 1f), true),
                SemanticVectorCandidate("blocked", floatArrayOf(1f, 0f), false)
            ),
            limit = 2
        )

        assertEquals(listOf("related", "unrelated"), ranked)
    }
}
