package com.aster.companion.domain

object GroupContextPolicy {
    /** A character carries its own continuity into a group, but never another character's continuity. */
    fun includePrivateCharacterMemory(type: ConversationType): Boolean =
        type == ConversationType.PRIVATE || type == ConversationType.GROUP

    fun isPrivateMessageVisibleToCharacter(viewerCharacterId: String, messageCharacterId: String?): Boolean =
        viewerCharacterId.isNotBlank() && (messageCharacterId == null || messageCharacterId == viewerCharacterId)

    /** Only a compact relationship fact may leave a private conversation. */
    fun canSharePrivateSummary(summary: String): Boolean {
        val value = summary.trim()
        if (value.isBlank() || value.length > MAX_SHARED_SUMMARY_LENGTH) return false
        val normalized = value.lowercase()
        return DISALLOWED_MARKERS.none(normalized::contains)
    }

    private const val MAX_SHARED_SUMMARY_LENGTH = 280
    private val DISALLOWED_MARKERS = listOf(
        "content://",
        "file://",
        "data:image",
        "notification raw",
        "package=",
        "device payload",
        "private secret"
    )
}
