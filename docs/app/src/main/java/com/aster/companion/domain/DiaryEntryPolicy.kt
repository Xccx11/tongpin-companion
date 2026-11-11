package com.aster.companion.domain

import com.aster.companion.data.MemoryEntity
import java.time.Instant
import java.time.ZoneId

/** A character revises one diary entry per private conversation each local day. */
object DiaryEntryPolicy {
    fun entryIdToRefresh(memories: List<MemoryEntity>, threadId: String, generatedAt: Long): String? =
        memories
            .asSequence()
            .filter { it.threadId == threadId && it.kind == MemoryKind.DIARY && !it.blockedFromMention && isSameDay(it.updatedAt, generatedAt) }
            .maxByOrNull { it.updatedAt }
            ?.id

    private fun isSameDay(first: Long, second: Long): Boolean {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(first).atZone(zone).toLocalDate() == Instant.ofEpochMilli(second).atZone(zone).toLocalDate()
    }
}
