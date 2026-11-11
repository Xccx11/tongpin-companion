package com.aster.companion.agent

data class GroupConversationWindowResult(
    val lines: List<GroupTranscriptLine>,
    val truncated: Boolean,
    val renderedTranscript: String
)

/** Bounds request size while making the omitted-history boundary explicit to the model. */
object GroupConversationWindow {
    fun forModel(lines: List<GroupTranscriptLine>, maxMessages: Int = DEFAULT_MAX_MESSAGES): GroupConversationWindowResult {
        require(maxMessages > 0) { "Group context window must be positive" }
        val selected = lines.takeLast(maxMessages)
        val truncated = lines.size > selected.size
        val rendered = buildString {
            if (truncated) appendLine("OLDER_GROUP_MESSAGES_OMITTED: use supplied group continuity facts for older context; do not invent omitted content.")
            append(GroupTranscriptFormatter.render(selected))
        }
        return GroupConversationWindowResult(selected, truncated, rendered)
    }

    const val DEFAULT_MAX_MESSAGES = 64
}
