package com.aster.companion.agent

/** Native tool calls may legitimately omit content; never surface that as a chat failure. */
object CrossConversationVisibleReplyPolicy {
    fun fallback(capabilityId: String): String = when (capabilityId) {
        "companion.send_private_message" -> "好，我私聊你。"
        "companion.send_group_message" -> "好，我去群里说。"
        else -> "好。"
    }
}
