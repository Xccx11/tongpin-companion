package com.aster.companion.agent

import com.aster.companion.data.MemoryEntity
import com.aster.companion.data.SemanticMemoryEntity
import com.aster.companion.data.TimelineEventEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.MemoryKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBoundaryTest {
    private val boundary = 1_000L

    @Test
    fun `explicit global event memory survives while old thread event memory is hidden`() {
        val stable = MemoryEntity("stable", "lan", null, MemoryKind.RELATIONSHIP, "老朋友", false, 100, 100)
        val globalEvent = MemoryEntity("event", "lan", null, MemoryKind.EVENT, "昨天去了花园", false, 100, 100)
        val oldThreadEvent = globalEvent.copy(id = "thread-event", threadId = "old-thread")
        val recentThreadEvent = oldThreadEvent.copy(id = "recent-thread-event", updatedAt = 1_001)

        assertTrue(ConversationMemoryVisibilityPolicy.visibleMemory(stable, boundary))
        assertTrue(ConversationMemoryVisibilityPolicy.visibleMemory(globalEvent, boundary))
        assertFalse(ConversationMemoryVisibilityPolicy.visibleMemory(oldThreadEvent, boundary))
        assertTrue(ConversationMemoryVisibilityPolicy.visibleMemory(recentThreadEvent, boundary))
    }

    @Test
    fun `old semantic world and timeline records are hidden from a fresh thread`() {
        val oldSemantic = SemanticMemoryEntity("semantic", "lan", null, null, "旧对话", null, 70, true, 100, 100)
        val recentSemantic = oldSemantic.copy(id = "recent-semantic", updatedAt = 1_001)
        val oldWorld = WorldStateEntity("world", "lan", null, "SYNCED_CONTEXT", "旧同步", 100)
        val recentWorld = oldWorld.copy(id = "recent-world", updatedAt = 1_001)
        val oldTimeline = TimelineEventEntity("timeline", "thread", "lan", "message", "NORMAL", "旧消息", null, 100, 100)
        val recentTimeline = oldTimeline.copy(id = "recent-timeline", occurredAt = 1_001)

        assertFalse(ConversationMemoryVisibilityPolicy.visibleSemanticMemory(oldSemantic, boundary))
        assertTrue(ConversationMemoryVisibilityPolicy.visibleSemanticMemory(recentSemantic, boundary))
        assertFalse(ConversationMemoryVisibilityPolicy.visibleWorldState(oldWorld, boundary))
        assertTrue(ConversationMemoryVisibilityPolicy.visibleWorldState(recentWorld, boundary))
        assertFalse(ConversationMemoryVisibilityPolicy.visibleTimeline(oldTimeline, boundary))
        assertTrue(ConversationMemoryVisibilityPolicy.visibleTimeline(recentTimeline, boundary))
    }
}
