package com.aster.companion.domain

/** Prevents an event queued before a conversation reset from recreating deleted context. */
object ConversationResetGuard {
    fun mayEnterConversation(eventOccurredAt: Long, conversationUpdatedAt: Long): Boolean =
        eventOccurredAt > conversationUpdatedAt
}
