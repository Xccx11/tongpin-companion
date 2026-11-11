package com.aster.companion.agent

import com.aster.companion.domain.ImageRequestPolicy
import com.aster.companion.domain.GroupImageMode
import com.aster.companion.domain.GroupImagePromptPolicy

/**
 * Preserves the identity, scene, and arrangement chosen for each group image.
 * It deliberately never joins separate requests just because they happened in one turn.
 */
object GroupImageActionPolicy {
    data class MemberRef(
        val id: String,
        val name: String
    )

    data class Proposal(
        val requestingCharacterId: String,
        val participantCharacterIds: List<String>,
        val mode: GroupImageMode,
        val scene: String
    )

    fun normalize(proposals: List<Proposal>, currentMemberIds: Set<String>): List<Proposal> = proposals
        .mapNotNull { proposal ->
            val participants = GroupImagePromptPolicy.parseParticipantIds(
                proposal.participantCharacterIds.joinToString(","),
                currentMemberIds
            ) ?: return@mapNotNull null
            if (!GroupImagePromptPolicy.isValidMode(proposal.mode, participants)) return@mapNotNull null
            proposal.copy(
                requestingCharacterId = proposal.requestingCharacterId.takeIf { it in participants } ?: participants.first(),
                participantCharacterIds = participants,
                scene = proposal.scene.trim()
            ).takeIf { it.scene.isNotBlank() }
        }
        .flatMap { proposal ->
            if (proposal.mode != GroupImageMode.SEPARATE) listOf(proposal) else proposal.participantCharacterIds.map { characterId ->
                proposal.copy(
                    requestingCharacterId = characterId,
                    participantCharacterIds = listOf(characterId),
                    mode = GroupImageMode.SINGLE,
                    scene = "Generate only ${characterId}'s individual image. Do not render any additional person. ${proposal.scene}"
                )
            }
        }
        .distinctBy { proposal ->
            if (proposal.mode == GroupImageMode.TOGETHER) {
                "${proposal.participantCharacterIds.joinToString(",")}|${proposal.mode}|${proposal.scene}"
            } else {
                "${proposal.requestingCharacterId}|${proposal.participantCharacterIds.joinToString(",")}|${proposal.mode}|${proposal.scene}"
            }
        }

    /**
     * Last-resort routing for a clear user photo command when the planner omitted an action.
     * It intentionally prefers separate images for multiple people so an ambiguous request
     * can never silently turn into a composite image.
     */
    fun explicitUserRequest(
        userText: String,
        members: List<MemberRef>,
        fallbackCharacterId: String?
    ): Proposal? {
        if (!ImageRequestPolicy.isExplicitPhotoRequest(userText)) return null
        val normalized = compact(userText)
        val named = members
            .filter { member -> member.name.isNotBlank() && normalized.contains(compact(member.name)) }
            .map { it.id }
            .distinct()
        val selected = when {
            named.isNotEmpty() -> named
            ALL_MEMBER_TERMS.any(normalized::contains) -> members.map { it.id }.distinct()
            fallbackCharacterId != null && fallbackCharacterId in members.map { it.id } -> listOf(fallbackCharacterId)
            else -> return null
        }
        if (selected.isEmpty()) return null
        val mode = GroupImagePromptPolicy.inferMode(userText, selected)
            ?: selected.singleOrNull()?.let { GroupImageMode.SINGLE }
            ?: GroupImageMode.SEPARATE
        if (!GroupImagePromptPolicy.isValidMode(mode, selected)) return null
        val requester = fallbackCharacterId?.takeIf { it in selected } ?: selected.first()
        return Proposal(
            requestingCharacterId = requester,
            participantCharacterIds = selected,
            mode = mode,
            scene = "The user explicitly requested a current group-chat photo. Preserve the exact request without inventing a new event: ${userText.trim().take(MAX_EXPLICIT_REQUEST_CHARS)}"
        )
    }

    private fun compact(value: String): String = value.replace(Regex("\\s+"), "")

    private val ALL_MEMBER_TERMS = listOf("大家", "所有人", "全员", "你们", "她们", "他们", "我们")
    private const val MAX_EXPLICIT_REQUEST_CHARS = 700
}

/** Converts a character's explicit natural-language photo offer into the same consent path as a tool call. */
object ImageProposalIntentPolicy {
    private val invitationTerms = listOf(
        "要不要看", "想不想看", "给你发张", "给你发一张", "发张照片", "发一张照片",
        "拍张照片给你", "拍一张照片给你", "拍张", "拍一张", "合照给你", "照片给你",
        "给你看看我", "发给你看看", "我想给你发", "想给你发", "我发张", "我发一张",
        "我想拍给你", "拍给你"
    )
    private val imageTerms = listOf("照片", "图片", "自拍", "照")

    fun fromGroupText(
        characterId: String,
        characterName: String,
        text: String,
        members: List<GroupImageActionPolicy.MemberRef>,
        contextualScene: String
    ): GroupImageActionPolicy.Proposal? {
        val base = fromCharacterText(characterId, characterName, text, contextualScene) ?: return null
        val normalized = text.replace(Regex("\\s+"), "")
        val named = members
            .filter { member -> member.name.isNotBlank() && normalized.contains(member.name.replace(Regex("\\s+"), "")) }
            .map { it.id }
            .distinct()
        val selected = when {
            named.size >= 2 -> named
            GROUP_TERMS.any(normalized::contains) -> members.map { it.id }.distinct()
            else -> listOf(characterId)
        }
        val mode = GroupImagePromptPolicy.inferMode(text, selected)
            ?: selected.singleOrNull()?.let { GroupImageMode.SINGLE }
            ?: GroupImageMode.SEPARATE
        if (!GroupImagePromptPolicy.isValidMode(mode, selected)) return base
        return base.copy(
            participantCharacterIds = selected,
            mode = mode,
            scene = contextualScene.trim().ifBlank { "$characterName wants to send the photo just offered in the group." }
        )
    }

    fun fromCharacterText(
        characterId: String,
        characterName: String,
        text: String,
        contextualScene: String
    ): GroupImageActionPolicy.Proposal? {
        val normalized = text.replace(Regex("\\s+"), "")
        if (invitationTerms.none(normalized::contains) || imageTerms.none(normalized::contains)) return null
        return GroupImageActionPolicy.Proposal(
            requestingCharacterId = characterId,
            participantCharacterIds = listOf(characterId),
            mode = GroupImageMode.SINGLE,
            scene = contextualScene.trim().ifBlank { "$characterName wants to send the user the photo just offered." }
        )
    }

    private val GROUP_TERMS = listOf("合照", "一起拍", "一起发", "同框", "分别", "各自", "每个人", "一人一张")
}
