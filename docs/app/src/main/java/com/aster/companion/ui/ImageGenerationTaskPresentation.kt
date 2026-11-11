package com.aster.companion.ui

import com.aster.companion.data.PendingActionEntity

/**
 * The database action is authoritative. Compatibility rows only fill gaps for
 * older moment posts and must never make one generation look like two tasks.
 */
object ImageGenerationTaskPresentation {
    fun merge(
        persisted: List<PendingActionEntity>,
        compatibilityFallbacks: List<PendingActionEntity>
    ): List<PendingActionEntity> {
        val persistedIds = persisted.asSequence().map { it.id }.toHashSet()
        return persisted + compatibilityFallbacks.filterNot { it.id in persistedIds }
    }
}
