package com.aster.companion.agent

import com.aster.companion.data.MessageEntity

/** Keeps the gateway's input complete while enforcing the App-owned memory boundary. */
object GatewayConversationHistoryPolicy {
    fun visibleMessages(messages: List<MessageEntity>, memorySince: Long): List<MessageEntity> =
        messages
            .asSequence()
            .filter { message -> message.createdAt >= memorySince }
            .filter { message -> message.fromUser || message.characterId != null }
            .sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
            .toList()
}
