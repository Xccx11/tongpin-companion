package com.aster.companion.ui

import com.aster.companion.data.MessageEntity

data class ThreadPreview(
    val lastPreview: String,
    val lastMessageAt: Long,
    val unreadCount: Int
)

object ThreadPreviewBuilder {
    fun build(messages: List<MessageEntity>): Map<String, ThreadPreview> =
        messages.groupBy(MessageEntity::threadId).mapValues { (_, threadMessages) ->
            val newest = threadMessages.maxBy(MessageEntity::createdAt)
            ThreadPreview(
                lastPreview = newest.body.ifBlank { if (newest.imageUri == null) "新消息" else "[图片]" },
                lastMessageAt = newest.createdAt,
                unreadCount = threadMessages.count { !it.fromUser && it.readAt == null }
            )
        }
}
