package com.aster.companion.agent

import com.aster.companion.data.MemoryEntity
import com.aster.companion.data.SemanticMemoryEntity
import com.aster.companion.data.TimelineEventEntity
import com.aster.companion.data.ThreadEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.MemoryKind

/** Defines which durable records may enter a thread after its memory boundary. */
object ConversationMemoryVisibilityPolicy {
    fun boundaryFor(thread: ThreadEntity): Long = thread.memorySince.takeIf { it > 0 } ?: thread.createdAt

    fun visibleMemory(memory: MemoryEntity, memorySince: Long): Boolean {
        val stableCharacterMemory = memory.threadId == null && memory.kind != MemoryKind.CONVERSATION
        return stableCharacterMemory || memory.updatedAt >= memorySince
    }

    fun visibleSemanticMemory(memory: SemanticMemoryEntity, memorySince: Long): Boolean =
        memory.updatedAt >= memorySince

    fun visibleWorldState(state: WorldStateEntity, memorySince: Long): Boolean =
        state.updatedAt >= memorySince

    fun visibleTimeline(event: TimelineEventEntity, memorySince: Long): Boolean =
        maxOf(event.occurredAt, event.createdAt) >= memorySince
}
