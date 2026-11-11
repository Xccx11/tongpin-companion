package com.aster.companion.system

/** Keeps source-chat receipts factual without exposing internal database or provider errors. */
object CrossConversationMessageReceipt {
    fun failure(capabilityId: String): String = when (capabilityId) {
        "companion.send_private_message" -> "刚才没能把这句话私聊发过去。"
        "companion.send_group_message" -> "刚才没能把这句话发到群里。"
        else -> "刚才没能把这句话发出去。"
    }
}
