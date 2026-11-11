package com.aster.companion.agent

import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionRepository
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.CompanionTimelineRecorder
import com.aster.companion.data.GroupPrivateFollowUpUseEntity
import com.aster.companion.data.MessageEntity
import com.aster.companion.domain.ConversationContextVersion
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.ConfirmationRequirement
import com.aster.companion.proactive.CompanionNotificationPublisher
import com.aster.companion.proactive.ProactiveAttemptResult
import com.aster.companion.proactive.ProactiveDeliveryStateStore
import com.aster.companion.proactive.SystemModeState
import com.aster.companion.system.CompanionActionCoordinator
import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Lets a character privately follow up on a real group exchange without copying private chats. */
@Singleton
class GroupToPrivateProactiveCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val repository: CompanionRepository,
    private val cipher: CompanionSecretCipher,
    private val client: OpenAiCompatibleClient,
    private val systemModeState: SystemModeState,
    private val deliveryState: ProactiveDeliveryStateStore,
    private val notificationPublisher: CompanionNotificationPublisher,
    private val timeline: CompanionTimelineRecorder,
    private val continuityContext: CompanionContinuityContextAssembler,
    private val actionCoordinator: CompanionActionCoordinator
) {
    /** A direct user request in the group is a command, not a best-effort proactive suggestion. */
    suspend fun sendExplicitUserRequest(
        threadId: String,
        targetCharacterIds: List<String>,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val group = dao.thread(threadId) ?: return false
        if (group.type != ConversationType.GROUP) return false
        val trigger = dao.recentMessages(threadId, 1).firstOrNull()?.takeIf { it.fromUser && it.body.isNotBlank() } ?: return false
        val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() } ?: return false
        val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
        val transcript = groupTranscript(threadId).renderedTranscript
        var delivered = false
        targetCharacterIds.distinct().filter { it in dao.participantIds(threadId) }.forEach { characterId ->
            val character = dao.character(characterId) ?: return@forEach
            val privateThread = repository.ensurePrivateThread(characterId)
            val privateHistory = dao.recentMessages(privateThread.id, PRIVATE_MESSAGE_LIMIT).asReversed()
                .map { message -> if (message.fromUser) "User: ${message.body}" else "${character.name}: ${message.body}" }
            val prompt = buildString {
                appendLine(UserRealityBoundaryInstruction.text())
                appendLine("You are ${character.name}. Your personality is: ${character.personalityPrompt}")
                character.relationshipPrompt.takeIf(String::isNotBlank)?.let { appendLine("Your relationship with the user: $it") }
                appendLine("The user explicitly asked you in the group to privately message them. You must now send one concise, natural, in-character opening message in the private chat. Do not say this is a system action. Do not invent an event outside the supplied transcript.")
                appendLine("Visible group context window:")
                appendLine(transcript)
                if (privateHistory.isNotEmpty()) {
                    appendLine("Your private chat history:")
                    privateHistory.forEach(::appendLine)
                }
            }
            val generatedBody = client.chat(profile, apiKey, "Write only the private message text.", prompt)
                .getOrNull()
                ?.let(RoleplayResponseSanitizer::clean)
                ?.trim()
                ?.takeIf(String::isNotBlank)
            // This is an explicit user command, so a failed follow-up model call cannot silently cancel delivery.
            val body = generatedBody?.takeUnless { it.contains("没说清楚") || (it.contains("刚才") && it.length < 18) }
                ?: "我来了，刚才在群里看到你叫我。"
            val message = MessageEntity(UUID.randomUUID().toString(), privateThread.id, characterId, body, null, false, now)
            dao.insertMessage(message)
            dao.touchThread(privateThread.id, now)
            timeline.recordMessage(message)
            notificationPublisher.publish(character.name, body, privateThread.id)
            handoffLatestPhotoOffer(threadId, privateThread.id, character)
            Log.i("TongpinCrossChat", "group_to_private delivered group=$threadId character=$characterId private=${privateThread.id} generated=${generatedBody != null}")
            delivered = true
        }
        return delivered
    }

    /** A group offer becomes a private consent card only when the user explicitly asks that character to send it privately. */
    private suspend fun handoffLatestPhotoOffer(
        groupThreadId: String,
        privateThreadId: String,
        character: CharacterEntity
    ) {
        val offer = dao.recentMessages(groupThreadId, 12)
            .firstOrNull { message -> !message.fromUser && message.characterId == character.id }
            ?.let { message ->
                ImageProposalIntentPolicy.fromCharacterText(
                    characterId = character.id,
                    characterName = character.name,
                    text = message.body,
                    contextualScene = "${character.name} sends the photo they just offered in the group directly to the user in their private chat."
                )
            } ?: return
        actionCoordinator.submit(
            eventId = null,
            threadId = privateThreadId,
            request = AgentActionRequest(
                capabilityId = "companion.propose_image",
                transport = ActionTransport.LOCAL,
                confirmation = ConfirmationRequirement.REQUIRED,
                arguments = mapOf("character_id" to offer.requestingCharacterId, "scene" to offer.scene)
            )
        )
    }

    suspend fun process(threadId: String, now: Long = System.currentTimeMillis()): Boolean {
        val group = dao.thread(threadId) ?: return false
        if (group.type != ConversationType.GROUP) return false
        if (!systemModeState.enabled) return false
        val latest = dao.recentMessages(threadId, 1).firstOrNull() ?: return false
        if (latest.body.isBlank() || now - latest.createdAt < MIN_DELAY_MS) return false
        val memberIds = dao.participantIds(threadId).distinct()
        if (memberIds.isEmpty()) return false
        val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return false
        val groupWindow = groupTranscript(threadId)
        val groupContinuity = continuityContext.forGroup(threadId, memberIds.toSet(), now)
        val sharedContext = buildList {
            addAll(groupContinuity.groupContext)
            groupContinuity.momentContext.takeIf(String::isNotBlank)?.let(::add)
            addAll(groupContinuity.activityContext)
        }
        var delivered = false
        memberIds.filterNot(systemModeState::isProactivePaused).forEach { characterId ->
            val character = dao.character(characterId) ?: return@forEach
            val privateThread = repository.ensurePrivateThread(characterId)
            val eventKey = "group-private:$threadId:${latest.id}"
            when (deliveryState.begin(characterId, eventKey, now, urgent = false)) {
                ProactiveAttemptResult.Started -> Unit
                else -> return@forEach
            }
            val currentGroup = dao.thread(threadId)
            if (!ConversationContextVersion.isCurrent(group.contextVersion, currentGroup?.contextVersion ?: -1) ||
                characterId !in dao.participantIds(threadId)
            ) {
                deliveryState.clearAttempt(characterId, now)
                return@forEach
            }
            val privateHistory = dao.recentMessages(privateThread.id, PRIVATE_MESSAGE_LIMIT).asReversed()
                .map { message ->
                    if (message.fromUser) "User: ${message.body}" else "${character.name}: ${message.body}"
                }
            val privateContinuity = continuityContext.forPrivateCharacter(characterId, privateThread.id, now)
            val previouslyUsedMessageIds = dao.usedGroupPrivateFollowUpMessageIds(threadId, characterId).toSet()
            val prompt = privateFollowUpPrompt(
                character = character,
                groupWindow = groupWindow,
                previouslyUsedMessageIds = previouslyUsedMessageIds,
                privateHistory = privateHistory,
                sharedContext = sharedContext,
                socialContext = privateContinuity.momentContext,
                activityContext = privateContinuity.activityContext
            )
            val raw = client.chat(
                profile,
                cipher.decrypt(requireNotNull(profile.encryptedApiKey)),
                prompt,
                "Decide whether this visible group exchange naturally deserves one new private message from you. Return JSON only."
            ).getOrElse {
                deliveryState.recordFailure(characterId, now)
                return@forEach
            }
            val decision = GroupPrivateFollowUpParser.parse(raw)
            val body = decision.message?.let(RoleplayResponseSanitizer::clean)?.takeIf(String::isNotBlank)
            val sourceMessageIds = GroupPrivateFollowUpSourcePolicy.select(
                window = groupWindow,
                previouslyUsedMessageIds = previouslyUsedMessageIds,
                requestedMessageIds = decision.sourceMessageIds
            )
            if (!decision.shouldSend || body == null || sourceMessageIds.isEmpty()) {
                deliveryState.recordDecision(characterId, eventKey, now)
                return@forEach
            }
            val currentPrivate = dao.thread(privateThread.id)
            if (currentPrivate == null || characterId !in dao.participantIds(privateThread.id)) {
                deliveryState.clearAttempt(characterId, now)
                return@forEach
            }
            val message = MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = privateThread.id,
                characterId = characterId,
                body = body,
                imageUri = null,
                fromUser = false,
                createdAt = System.currentTimeMillis()
            )
            dao.insertGroupPrivateFollowUp(
                message = message,
                uses = sourceMessageIds.map { sourceMessageId ->
                    GroupPrivateFollowUpUseEntity(threadId, characterId, sourceMessageId, message.createdAt)
                }
            )
            dao.touchThread(privateThread.id, message.createdAt)
            timeline.recordMessage(message)
            deliveryState.recordDelivery(characterId, eventKey, message.createdAt)
            notificationPublisher.publish(character.name, body, privateThread.id)
            delivered = true
        }
        return delivered
    }

    private suspend fun groupTranscript(threadId: String): GroupConversationWindowResult = GroupConversationWindow.forModel(
        dao.recentMessages(threadId, GroupConversationWindow.DEFAULT_MAX_MESSAGES).asReversed().map { message ->
            GroupTranscriptLine(
                messageId = message.id,
                speakerId = message.characterId,
                speakerName = if (message.fromUser) "用户" else dao.character(message.characterId.orEmpty())?.name ?: "角色",
                isUser = message.fromUser,
                body = message.body
            )
        }
    )

    private fun privateFollowUpPrompt(
        character: CharacterEntity,
        groupWindow: GroupConversationWindowResult,
        previouslyUsedMessageIds: Set<String>,
        privateHistory: List<String>,
        sharedContext: List<String>,
        socialContext: String,
        activityContext: List<String>
    ): String = buildString {
        appendLine(UserRealityBoundaryInstruction.text())
        appendLine("You are ${character.name}. Your personality is fixed: ${character.personalityPrompt}")
        if (character.relationshipPrompt.isNotBlank()) {
            appendLine("Your relationship with the user: ${character.relationshipPrompt}")
        }
        appendLine("This is a private follow-up inspired by a real visible group conversation. You personally saw only the group transcript below.")
        appendLine("The transcript is the latest visible group window, not a delta since your last private chat. It includes both the user's latest messages and every character's latest messages.")
        appendLine("You may mention only what this character actually saw in that group. Do not pretend the user privately told you anything. Do not rewrite another character's words as the user's private words. Do not reveal another private chat and do not invent a missing group exchange.")
        appendLine("A private message is optional. Send only when the current visible group exchange gives you a natural emotional or practical reason to contact the user privately. Do not repeat an old answer merely because the user is quiet.")
        appendLine("Every SEND must include source_message_ids containing one or more message_id values from the visible transcript that actually inspired this private message. Messages listed as already used are background only and must never be cited again. If no unused visible message gives a natural reason, return IGNORE.")
        appendLine("Return JSON only: {\"decision\":\"SEND\" or \"IGNORE\",\"message\":\"one natural private message when SEND\",\"source_message_ids\":[\"visible-message-id\"]}. IGNORE must not produce a message.")
        appendLine("ALREADY_USED_GROUP_MESSAGE_IDS_FOR_THIS_CHARACTER:")
        appendLine(previouslyUsedMessageIds.sorted().joinToString(",").ifBlank { "(none)" })
        appendLine("VISIBLE GROUP CONTEXT WINDOW:")
        appendLine(groupWindow.renderedTranscript)
        if (sharedContext.isNotEmpty()) {
            appendLine("SHARED GROUP FACTS:")
            sharedContext.forEach(::appendLine)
        }
        if (socialContext.isNotBlank()) {
            appendLine("THIS CHARACTER'S PUBLIC-FEED CONTINUITY:")
            appendLine(socialContext)
        }
        if (activityContext.isNotEmpty()) {
            appendLine("RECENT COMPLETED IN-APP ACTIVITY:")
            activityContext.forEach(::appendLine)
        }
        if (privateHistory.isNotEmpty()) {
            appendLine("THIS CHARACTER'S PRIVATE CHAT HISTORY (separate from the group):")
            privateHistory.forEach(::appendLine)
        }
    }

    private companion object {
        const val MIN_DELAY_MS = 15_000L
        const val PRIVATE_MESSAGE_LIMIT = 24
    }
}
