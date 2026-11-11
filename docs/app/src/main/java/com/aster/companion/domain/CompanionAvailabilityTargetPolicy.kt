package com.aster.companion.domain

/** Availability statements pause every role visible in the current conversation. */
object CompanionAvailabilityTargetPolicy {
    fun resolve(conversationType: ConversationType?, participantIds: List<String>): List<String> =
        when (conversationType) {
            ConversationType.PRIVATE -> participantIds.take(1)
            ConversationType.GROUP -> participantIds.distinct()
            null -> emptyList()
        }
}
