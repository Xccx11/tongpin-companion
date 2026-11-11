package com.aster.companion.agent

import com.aster.companion.data.MomentCommentEntity
import com.aster.companion.data.MomentPostEntity
import com.aster.companion.domain.MomentActors

/** Only public feed activity involving this character may enter her private conversation context. */
object MomentConversationContext {
    fun forCharacter(
        characterId: String,
        posts: List<MomentPostEntity>,
        comments: List<MomentCommentEntity>
    ): String {
        val ownPosts = posts.asSequence()
            .filter { it.authorType == MomentActors.CHARACTER && it.authorCharacterId == characterId }
            .sortedByDescending { it.createdAt }
            .take(MAX_POSTS)
            .toList()
        if (ownPosts.isEmpty()) return ""
        val commentsByPost = comments.groupBy { it.postId }
        return buildString {
            appendLine("该角色在同频动态中真实发生的近期互动（只在相关时自然提及，不要机械复述）：")
            ownPosts.asReversed().forEach { post ->
                appendLine("动态[${post.id}]：${post.body.take(MAX_TEXT)}")
                commentsByPost[post.id].orEmpty().takeLast(MAX_COMMENTS_PER_POST).forEach { comment ->
                    val author = when {
                        comment.authorType == MomentActors.USER -> "用户"
                        comment.authorCharacterId == characterId -> "你"
                        else -> "另一个角色"
                    }
                    appendLine("动态评论[$author]：${comment.body.take(MAX_TEXT)}")
                }
            }
        }.trim()
    }

    /** Public feed activity can be shared in a group only with the current group members. */
    fun forGroup(
        memberIds: Set<String>,
        posts: List<MomentPostEntity>,
        comments: List<MomentCommentEntity>
    ): String {
        val visiblePosts = posts.asSequence()
            .filter { it.status == "PUBLISHED" }
            .filter { post ->
                post.authorType == "USER" || post.authorCharacterId in memberIds
            }
            .sortedByDescending(MomentPostEntity::createdAt)
            .take(MAX_GROUP_POSTS)
            .toList()
            .asReversed()
        if (visiblePosts.isEmpty()) return ""
        val commentsByPost = comments.groupBy(MomentCommentEntity::postId)
        return buildString {
            appendLine("Recent public feed activity shared with this group:")
            visiblePosts.forEach { post ->
                val author = if (post.authorType == "USER") "User" else post.authorCharacterId.orEmpty().ifBlank { "Group member" }
                appendLine("Feed post [$author]: ${post.body.take(MAX_TEXT)}")
                commentsByPost[post.id].orEmpty()
                    .asSequence()
                    .filter { comment -> comment.authorType == "USER" || comment.authorCharacterId in memberIds }
                    .toList()
                    .takeLast(MAX_COMMENTS_PER_POST)
                    .forEach { comment ->
                        val commentAuthor = if (comment.authorType == "USER") "User" else comment.authorCharacterId.orEmpty().ifBlank { "Group member" }
                        appendLine("Feed reply [$commentAuthor]: ${comment.body.take(MAX_TEXT)}")
                    }
            }
            append("These are public-feed events, not private-chat statements. Mention them only when they naturally matter.")
        }.trim()
    }

    private const val MAX_POSTS = 4
    private const val MAX_GROUP_POSTS = 4
    private const val MAX_COMMENTS_PER_POST = 6
    private const val MAX_TEXT = 400
}
