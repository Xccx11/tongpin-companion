package com.aster.companion.agent

import com.aster.companion.domain.GroupContextPolicy

/** Keeps a character's own private history available in a group without exposing another character's chat. */
object GroupPrivateConversationContextPolicy {
    data class Line(val characterId: String?, val fromUser: Boolean, val body: String)

    fun render(ownerCharacterId: String, lines: List<Line>): List<String> = lines
        .asSequence()
        .filter { line -> line.body.isNotBlank() }
        .filter { line -> line.fromUser || GroupContextPolicy.isPrivateMessageVisibleToCharacter(ownerCharacterId, line.characterId) }
        .map { line -> if (line.fromUser) "User: ${line.body.trim()}" else "Character: ${line.body.trim()}" }
        .toList()
}
