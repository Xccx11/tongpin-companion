package com.aster.companion.agent

import com.aster.companion.domain.GroupImageCharacterAnchor

/** Immutable group evidence passed to the image planner, never directly to the renderer. */
data class GroupImagePromptPlanningInput(
    val identityFacts: String,
    val scene: String,
    val recentConversation: List<String>,
    val requiredCharacterCount: Int
) {
    data class Line(
        val speakerName: String,
        val fromUser: Boolean,
        val body: String
    )

    companion object {
        fun create(
            anchors: List<GroupImageCharacterAnchor>,
            currentScene: String,
            recentTranscript: List<Line>
        ): GroupImagePromptPlanningInput {
            require(anchors.isNotEmpty()) { "At least one group image participant is required" }
            return GroupImagePromptPlanningInput(
                identityFacts = buildString {
                    appendLine("SELECTED CHARACTER VISUAL IDENTITIES - include exactly these people and do not merge their features:")
                    anchors.forEachIndexed { index, anchor ->
                        appendLine("Character ${index + 1} (${anchor.name}): ${anchor.appearancePrompt.trim()}")
                    }
                }.trim(),
                scene = currentScene.trim(),
                recentConversation = recentTranscript
                    .takeLast(MAX_TRANSCRIPT_LINES)
                    .mapNotNull { line ->
                        val body = line.body.trim().take(MAX_LINE_CHARS)
                        if (body.isBlank()) null else {
                            val role = if (line.fromUser) "USER" else "CHARACTER [${line.speakerName.trim().ifBlank { "Unknown" }}]"
                            "$role: $body"
                        }
                    },
                requiredCharacterCount = anchors.size
            )
        }

        private const val MAX_TRANSCRIPT_LINES = 16
        private const val MAX_LINE_CHARS = 480
    }
}
