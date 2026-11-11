package com.aster.companion.agent

import com.aster.companion.data.SemanticMemoryEntity

/** Local deterministic fallback when the configured gateway has no /v1/embeddings endpoint. */
object SemanticMemoryFallbackPolicy {
    fun rank(candidates: List<SemanticMemoryEntity>, limit: Int): List<SemanticMemoryEntity> =
        candidates
            .sortedWith(compareByDescending<SemanticMemoryEntity> { it.importance }.thenByDescending { it.updatedAt })
            .take(limit)
}
