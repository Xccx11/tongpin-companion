package com.aster.companion.agent

import com.aster.companion.data.TimelineEventEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionContinuityContextPolicyTest {
    @Test
    fun `private context keeps only its own completed actions and social activity`() {
        val lines = CompanionContinuityContextPolicy.forPrivate(
            characterId = "lan",
            threadId = "private-lan",
            events = listOf(
                event("chat", "private-lan", null, "chat:user", "raw private message", 1),
                event("life", null, null, "notification:parcel", "raw parcel notification", 2),
                event("other-action", "private-other", "other", "tool:device.set_alarm", "unrelated alarm", 3),
                event("own-action", "private-lan", "lan", "tool:device.set_alarm", "alarm was set", 4),
                event("own-feed", null, "lan", "moment:comment", "user replied under her post", 5)
            )
        ).joinToString("\n")

        assertTrue(lines.contains("alarm was set"))
        assertTrue(lines.contains("user replied under her post"))
        assertFalse(lines.contains("raw private message"))
        assertFalse(lines.contains("raw parcel notification"))
        assertFalse(lines.contains("unrelated alarm"))
    }

    @Test
    fun `group context keeps only group actions and member public activity`() {
        val lines = CompanionContinuityContextPolicy.forGroup(
            threadId = "family",
            memberIds = setOf("lan", "xia"),
            events = listOf(
                event("group-action", "family", "lan", "tool:device.set_alarm", "group alarm result", 1),
                event("member-feed", null, "xia", "moment:post", "xia shared a feed post", 2),
                event("outsider-feed", null, "other", "moment:post", "other shared a feed post", 3),
                event("private-action", "private-lan", "lan", "tool:device.set_alarm", "private alarm result", 4),
                event("raw-group-chat", "family", null, "chat:user", "group chat copy", 5)
            )
        ).joinToString("\n")

        assertTrue(lines.contains("group alarm result"))
        assertTrue(lines.contains("xia shared a feed post"))
        assertFalse(lines.contains("other shared a feed post"))
        assertFalse(lines.contains("private alarm result"))
        assertFalse(lines.contains("group chat copy"))
    }

    private fun event(
        id: String,
        threadId: String?,
        characterId: String?,
        source: String,
        summary: String,
        occurredAt: Long
    ) = TimelineEventEntity(
        id = id,
        threadId = threadId,
        characterId = characterId,
        source = source,
        sensitiveLevel = "TEST",
        summary = summary,
        payloadJson = null,
        occurredAt = occurredAt,
        createdAt = occurredAt
    )
}
