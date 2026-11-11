package com.aster.companion.data

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Writes the facts available to companion reasoning without turning private raw payloads into memory. */
@Singleton
class CompanionTimelineRecorder @Inject constructor(private val dao: CompanionDao) {
    suspend fun recordMessage(message: MessageEntity) {
        val summary = when {
            message.imageUri != null && message.body.isBlank() -> "Shared an image in the conversation"
            message.imageUri != null -> "${message.body.take(MAX_SUMMARY)} [image attached]"
            else -> message.body.take(MAX_SUMMARY)
        }
        dao.upsertTimelineEvent(
            TimelineEventEntity(
                id = "message:${message.id}",
                threadId = message.threadId,
                characterId = message.characterId,
                source = if (message.fromUser) "chat:user" else "chat:character",
                sensitiveLevel = "PRIVATE",
                summary = summary,
                payloadJson = null,
                occurredAt = message.createdAt,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordLifeEvent(event: EventEntity) {
        dao.upsertTimelineEvent(
            TimelineEventEntity(
                id = "life:${event.id}",
                threadId = null,
                characterId = null,
                source = event.source,
                sensitiveLevel = "LIFE_SIGNAL",
                summary = event.summary.take(MAX_SUMMARY),
                payloadJson = null,
                occurredAt = event.occurredAt,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordToolResult(threadId: String?, characterId: String?, capabilityId: String, summary: String, occurredAt: Long = System.currentTimeMillis()) {
        dao.upsertTimelineEvent(
            TimelineEventEntity(
                id = "tool:${UUID.randomUUID()}",
                threadId = threadId,
                characterId = characterId,
                source = "tool:$capabilityId",
                sensitiveLevel = "ACTION_RESULT",
                summary = summary.take(MAX_SUMMARY),
                payloadJson = null,
                occurredAt = occurredAt,
                createdAt = occurredAt
            )
        )
    }

    suspend fun recordMomentPost(post: MomentPostEntity) {
        dao.upsertTimelineEvent(
            TimelineEventEntity(
                id = "moment:${post.id}:post",
                threadId = null,
                characterId = post.authorCharacterId,
                source = "moment:post",
                sensitiveLevel = "SOCIAL",
                summary = post.body.take(MAX_SUMMARY),
                payloadJson = null,
                occurredAt = post.updatedAt,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordMomentComment(post: MomentPostEntity, comment: MomentCommentEntity) {
        val involvedCharacterId = comment.authorCharacterId ?: post.authorCharacterId
        dao.upsertTimelineEvent(
            TimelineEventEntity(
                id = "moment:${post.id}:comment:${comment.id}",
                threadId = null,
                characterId = involvedCharacterId,
                source = "moment:comment",
                sensitiveLevel = "SOCIAL",
                summary = comment.body.take(MAX_SUMMARY),
                payloadJson = null,
                occurredAt = comment.createdAt,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordMomentMedia(post: MomentPostEntity, media: MomentMediaEntity) {
        dao.upsertTimelineEvent(
            TimelineEventEntity(
                id = "moment:${post.id}:media:${media.ordinal}",
                threadId = null,
                characterId = post.authorCharacterId,
                source = "image:moment",
                sensitiveLevel = "SOCIAL",
                summary = "An image was attached to a public feed post",
                payloadJson = null,
                occurredAt = post.updatedAt,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordMomentReaction(post: MomentPostEntity, actorType: String, actorId: String, liked: Boolean) {
        val involvedCharacterId = if (actorType == "CHARACTER") actorId else post.authorCharacterId
        val now = System.currentTimeMillis()
        dao.upsertTimelineEvent(
            TimelineEventEntity(
                id = "moment:${post.id}:reaction:$actorType:$actorId",
                threadId = null,
                characterId = involvedCharacterId,
                source = "moment:reaction",
                sensitiveLevel = "SOCIAL",
                summary = if (liked) "A public feed post received a like" else "A public feed like was removed",
                payloadJson = null,
                occurredAt = now,
                createdAt = now
            )
        )
    }

    private companion object {
        const val MAX_SUMMARY = 600
    }
}
