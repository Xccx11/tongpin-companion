package com.aster.companion.agent

/** Shared posts can invite the other roles in, without making anyone react to their own post. */
object MomentParticipantPolicy {
    fun reactorsForPost(characterIds: List<String>, postAuthorCharacterId: String?): List<String> =
        characterIds.filterNot { it == postAuthorCharacterId }

    fun repliersForUserComment(characterIds: List<String>, postAuthorCharacterId: String?): List<String> =
        listOfNotNull(postAuthorCharacterId).filter { it in characterIds } +
            characterIds.filterNot { it == postAuthorCharacterId }
}
