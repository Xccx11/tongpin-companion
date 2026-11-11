package com.aster.companion.proactive

import com.aster.companion.data.CompanionDao
import javax.inject.Inject
import javax.inject.Singleton

/** One shared current-life projection for interactive and proactive model turns. */
@Singleton
class LifeSnapshotFactory @Inject constructor(
    private val dao: CompanionDao
) {
    suspend fun capture(now: Long, excludedEventId: String? = null): List<LifeSnapshotFact> =
        LifeSnapshotPolicy.currentFacts(
            dao.recentEvents(now - LOOKBACK_MS, LOOKUP_LIMIT)
                .filterNot { it.id == excludedEventId }
                .map { event -> LifeSnapshotInput(event.source, event.summary, event.occurredAt) },
            now
        )

    private companion object {
        const val LOOKBACK_MS = 2 * 60 * 60 * 1000L
        const val LOOKUP_LIMIT = 64
    }
}
