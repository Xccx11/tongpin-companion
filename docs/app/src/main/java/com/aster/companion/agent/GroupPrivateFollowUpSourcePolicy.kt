package com.aster.companion.agent

/**
 * Keeps a private follow-up grounded in the latest visible group window.
 * A group message can inspire one private follow-up per character, regardless
 * of whether that message was written by the user or another character.
 */
object GroupPrivateFollowUpSourcePolicy {
    fun select(
        window: GroupConversationWindowResult,
        previouslyUsedMessageIds: Set<String>,
        requestedMessageIds: List<String>
    ): Set<String> {
        val visibleIds = window.lines.mapTo(linkedSetOf<String>()) { it.messageId }
        val requested = requestedMessageIds.filter { it in visibleIds }.toSet()
        if (requestedMessageIds.isNotEmpty()) return requested - previouslyUsedMessageIds

        // Older model responses may not return source ids yet. Ground that one
        // follow-up in the newest unused visible group message, never in private-chat time.
        return window.lines.asReversed()
            .firstOrNull { it.messageId !in previouslyUsedMessageIds }
            ?.let { setOf(it.messageId) }
            .orEmpty()
    }
}
