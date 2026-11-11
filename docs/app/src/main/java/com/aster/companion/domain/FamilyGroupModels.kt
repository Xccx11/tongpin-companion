package com.aster.companion.domain

enum class GroupAtmosphere(val maxFollowUps: Int) {
    QUIET(0),
    DAILY(2),
    LIVELY(3)
}

/** Local delivery guards only. The model still decides whether a group has anything natural to say. */
object GroupProactivePolicy {
    fun canStart(
        isSystemModeEnabled: Boolean,
        isPaused: Boolean,
        hasMembers: Boolean,
        isGroupProactiveEnabled: Boolean = true
    ): Boolean = isSystemModeEnabled && isGroupProactiveEnabled && !isPaused && hasMembers

    fun eligibleMembers(memberIds: List<String>, pausedMemberIds: Set<String>): List<String> =
        memberIds.distinct().filterNot { it in pausedMemberIds }
}

data class GroupMemberContext(
    val id: String,
    val name: String,
    val relationshipPrompt: String
)

/** Keeps a character's relationship attached to that character in group speaker planning. */
object GroupMemberContextFormatter {
    fun render(members: List<GroupMemberContext>): String = members.joinToString("; ") { member ->
        "${member.id}=${member.name}; relationship=${member.relationshipPrompt.ifBlank { "unspecified" }}"
    }
}

data class GroupTurnPlan(
    val mainSpeakerId: String?,
    val followUpSpeakerIds: List<String>,
    val intent: GroupProactiveIntent = if (mainSpeakerId == null) GroupProactiveIntent.SILENT else GroupProactiveIntent.CONTINUE,
    val topic: String? = null,
    val invitedSpeakerId: String? = null
)

/** The model chooses the reason for a background group turn; local code only validates it. */
enum class GroupProactiveIntent {
    SILENT,
    CONTINUE,
    START_TOPIC
}

data class GroupImageCharacterAnchor(
    val id: String,
    val name: String,
    val appearancePrompt: String,
    val referenceImageUri: String?
)

/** The model chooses the visual arrangement from the conversation; the app only validates it. */
enum class GroupImageMode {
    SINGLE,
    SEPARATE,
    TOGETHER
}

/** Validates group image participants and keeps visual identity separate from personality. */
object GroupImagePromptPolicy {
    fun parseParticipantIds(raw: String?, currentMemberIds: Set<String>): List<String>? {
        val ids = raw.orEmpty()
            .split(',', '|', '\n')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (ids.isEmpty() || ids.size > MAX_PARTICIPANTS || ids.any { it !in currentMemberIds }) return null
        return ids
    }

    fun resolveMode(raw: String?, participantIds: List<String>): GroupImageMode? {
        val normalized = raw?.trim()?.takeIf(String::isNotBlank)?.uppercase()
        val selected = if (normalized == null) {
            participantIds.distinct().singleOrNull()?.let { GroupImageMode.SINGLE }
        } else {
            runCatching { GroupImageMode.valueOf(normalized) }.getOrNull()
        }
        return selected
            ?.takeIf { isValidMode(it, participantIds) }
    }

    /** Infers an arrangement only when the scene explicitly distinguishes it. */
    fun inferMode(scene: String?, participantIds: List<String>): GroupImageMode? {
        val selected = participantIds.distinct()
        if (selected.size == 1) return GroupImageMode.SINGLE
        if (selected.isEmpty()) return null
        val text = scene.orEmpty().lowercase().replace(Regex("\\s+"), "")
        return when {
            TOGETHER_TERMS.any(text::contains) -> GroupImageMode.TOGETHER
            SEPARATE_TERMS.any(text::contains) -> GroupImageMode.SEPARATE
            else -> null
        }
    }

    fun isValidMode(mode: GroupImageMode, participantIds: List<String>): Boolean = when (mode) {
        GroupImageMode.SINGLE -> participantIds.distinct().size == 1
        GroupImageMode.SEPARATE, GroupImageMode.TOGETHER -> participantIds.distinct().size >= 2
    }

    fun rendererPrompt(anchors: List<GroupImageCharacterAnchor>, scene: String): String {
        require(anchors.isNotEmpty()) { "At least one group image participant is required" }
        return buildString {
            appendLine("SELECTED CHARACTER VISUAL IDENTITIES - include only these people:")
            anchors.forEachIndexed { index, anchor ->
                appendLine("Character ${index + 1} (${anchor.name}): ${anchor.appearancePrompt.trim()}")
            }
            appendLine()
            appendLine("CURRENT GROUP IMAGE MOMENT - follow this scene exactly:")
            append(scene.trim())
        }
    }

    private const val MAX_PARTICIPANTS = 8
    private val TOGETHER_TERMS = listOf(
        "\u5408\u7167", "\u4e00\u8d77\u62cd", "\u4e00\u8d77\u53d1", "\u540c\u6846", "\u4e00\u5f20\u5408\u7167", "\u4e00\u8d77\u51fa\u73b0"
    )
    private val SEPARATE_TERMS = listOf(
        "\u5206\u522b", "\u5404\u81ea", "\u6bcf\u4e2a\u4eba", "\u4e00\u4eba\u4e00\u5f20", "\u5404\u62cd", "\u5355\u72ec\u53d1", "\u81ea\u5df1\u7684\u7167\u7247"
    )
}
