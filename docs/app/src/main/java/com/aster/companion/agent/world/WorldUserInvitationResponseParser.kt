package com.aster.companion.agent.world

enum class WorldUserInvitationResponse {
    ACCEPT,
    REJECT,
    NONE
}

/** Requires an unambiguous response before changing user participation. */
object WorldUserInvitationResponseParser {
    fun parse(raw: String): WorldUserInvitationResponse {
        val text = raw.trim().replace(Regex("[，。！？、,.!?\\s]+$"), "")
        return when {
            text in ACCEPT_PHRASES -> WorldUserInvitationResponse.ACCEPT
            text in REJECT_PHRASES -> WorldUserInvitationResponse.REJECT
            else -> WorldUserInvitationResponse.NONE
        }
    }

    private val ACCEPT_PHRASES = setOf(
        "接受",
        "接受邀请",
        "我参加",
        "参加",
        "我去",
        "去",
        "我加入",
        "加入"
    )

    private val REJECT_PHRASES = setOf(
        "拒绝",
        "拒绝邀请",
        "不参加",
        "不去",
        "我不去",
        "不加入",
        "我不加入"
    )
}
