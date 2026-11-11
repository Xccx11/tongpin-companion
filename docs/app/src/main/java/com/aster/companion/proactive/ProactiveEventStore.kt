package com.aster.companion.proactive

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.EventEntity
import com.aster.companion.data.CompanionTimelineRecorder
import com.aster.companion.domain.EventState
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveEventStore @Inject constructor(
    private val dao: CompanionDao,
    private val timeline: CompanionTimelineRecorder
) {
    suspend fun recordNotification(packageName: String, title: String, text: String, postedAt: Long): String? {
        val summary = listOf(title, text).filter(String::isNotBlank).joinToString(" | ")
        if (summary.isBlank()) return null
        val id = "notification:" + digest("$packageName|$summary|$postedAt")
        val event = EventEntity(id, "notification:$packageName", summary, EventState.NEW, postedAt, null, null)
        dao.upsertEvent(event)
        timeline.recordLifeEvent(event)
        return id
    }

    suspend fun recordSystemEvent(source: String, summary: String, dedupeKey: String, occurredAt: Long): String {
        val id = "system:" + digest("$source|$dedupeKey")
        if (dao.event(id) == null) {
            val event = EventEntity(id, source, summary, EventState.NEW, occurredAt, null, null)
            dao.upsertEvent(event)
            timeline.recordLifeEvent(event)
        }
        return id
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
