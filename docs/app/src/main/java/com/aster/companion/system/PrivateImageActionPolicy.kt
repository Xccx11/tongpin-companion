package com.aster.companion.system

import com.aster.companion.domain.AgentActionRequest

/** Private image proposals created in a group may only target a current group member. */
object PrivateImageActionPolicy {
    fun targetCharacterId(request: AgentActionRequest, sourceGroupMemberIds: Set<String>): String? =
        request.arguments["character_id"]?.trim()?.takeIf { it in sourceGroupMemberIds }
}
