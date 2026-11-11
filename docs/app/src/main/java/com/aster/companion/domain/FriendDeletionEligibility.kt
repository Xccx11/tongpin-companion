package com.aster.companion.domain

sealed interface FriendDeletionEligibility {
    data object Allowed : FriendDeletionEligibility
    data class BlockedByGroups(val groupTitles: List<String>) : FriendDeletionEligibility

    companion object {
        fun fromGroupTitles(groupTitles: List<String>): FriendDeletionEligibility =
            if (groupTitles.isEmpty()) Allowed else BlockedByGroups(groupTitles)
    }
}
