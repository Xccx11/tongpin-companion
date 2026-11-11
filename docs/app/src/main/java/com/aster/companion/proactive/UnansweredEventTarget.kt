package com.aster.companion.proactive

/** A relationship pulse belongs to exactly one private conversation. */
enum class RelationshipPulseKind { FOLLOW_UP, PRESENCE }

data class UnansweredEventTarget(
    val threadId: String,
    val characterId: String,
    val kind: RelationshipPulseKind = RelationshipPulseKind.FOLLOW_UP
) {
    companion object {
        private const val PREFIX = "system:presence:"

        fun source(threadId: String, characterId: String): String = "$PREFIX$threadId:$characterId"

        /** A later check gives a clingy companion another genuine chance to initiate contact. */
        fun presenceSource(threadId: String, characterId: String): String = "$PREFIX$threadId:$characterId:presence"

        fun parse(source: String): UnansweredEventTarget? {
            if (!source.startsWith(PREFIX)) return null
            val parts = source.removePrefix(PREFIX).split(':')
            if (parts.size !in 2..3 || parts.take(2).any(String::isBlank)) return null
            val kind = when (parts.getOrNull(2)) {
                null -> RelationshipPulseKind.FOLLOW_UP
                "presence" -> RelationshipPulseKind.PRESENCE
                else -> return null
            }
            return UnansweredEventTarget(parts[0], parts[1], kind)
        }
    }
}
