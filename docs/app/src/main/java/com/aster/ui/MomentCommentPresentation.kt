package com.aster.ui

import com.aster.companion.data.MomentCommentEntity

/** Maps persisted reply links to the familiar social-feed author line. */
object MomentCommentPresentation {
    fun authorLine(
        comment: MomentCommentEntity,
        allComments: List<MomentCommentEntity>,
        characterName: (String) -> String
    ): String {
        val author = nameFor(comment, characterName)
        val target = comment.replyToCommentId
            ?.let { replyId -> allComments.firstOrNull { it.id == replyId } }
            ?.let { replied -> nameFor(replied, characterName) }
        return target?.let { "$author 回复 $it" } ?: author
    }

    private fun nameFor(comment: MomentCommentEntity, characterName: (String) -> String): String =
        if (comment.authorType == "USER") "我" else comment.authorCharacterId?.let(characterName) ?: "角色"
}
