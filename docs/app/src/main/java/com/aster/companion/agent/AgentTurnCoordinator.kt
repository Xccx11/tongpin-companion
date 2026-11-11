package com.aster.companion.agent

import com.aster.companion.data.AgentTurnEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionRepository
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.EventEntity
import com.aster.companion.data.EventRecipientEntity
import com.aster.companion.data.MessageEntity
import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.data.AgentRunEntity
import com.aster.companion.data.CompanionTimelineRecorder
import com.aster.companion.domain.CharacterCard
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.CompanionInvariants
import com.aster.companion.domain.DecisionValidation
import com.aster.companion.domain.EventState
import com.aster.companion.domain.ProactiveDeliveryGuard
import com.aster.companion.domain.ProactiveMessageCooldown
import com.aster.companion.domain.ProactiveEventFreshness
import com.aster.companion.domain.ConversationContextVersion
import com.aster.companion.domain.ConversationResetGuard
import com.aster.companion.system.CapabilitySnapshotFactory
import com.aster.companion.system.ShizukuStatusProvider
import com.aster.companion.proactive.CompanionNotificationPublisher
import com.aster.companion.proactive.ProactiveCharacterSelector
import com.aster.companion.proactive.SystemModeState
import com.aster.companion.proactive.UnansweredEventTarget
import com.aster.companion.proactive.LifeSnapshotFactory
import com.aster.companion.proactive.LifeSnapshotFact
import com.aster.companion.proactive.NotificationReplyMarkerPolicy
import com.aster.companion.proactive.NotificationReplyMarkerStore
import com.aster.companion.proactive.ProactiveAttemptResult
import com.aster.companion.proactive.ProactiveDeliveryStateStore
import com.aster.companion.system.CompanionActionCoordinator
import com.aster.companion.system.ShizukuFixedActionRouter
import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AgentTurnResult {
    data object ModelNotConfigured : AgentTurnResult
    data object InvalidDecision : AgentTurnResult
    data object Ignored : AgentTurnResult
    data class Sent(val threadId: String) : AgentTurnResult
    data class Deferred(val until: Long) : AgentTurnResult
    data class Failed(val reason: String) : AgentTurnResult
}

/** Runs a single persisted proactive turn. It never assigns emotional importance locally. */
@Singleton
class AgentTurnCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val repository: CompanionRepository,
    private val secretCipher: CompanionSecretCipher,
    private val client: OpenAiCompatibleClient,
    private val shizukuStatusProvider: ShizukuStatusProvider,
    private val shizukuRouter: ShizukuFixedActionRouter,
    private val systemModeState: SystemModeState,
    private val notificationPublisher: CompanionNotificationPublisher,
    private val actionCoordinator: CompanionActionCoordinator,
    private val semanticMemoryRetriever: SemanticMemoryRetriever,
    private val timeline: CompanionTimelineRecorder,
    private val lifeSnapshotFactory: LifeSnapshotFactory,
    private val notificationReplyMarkers: NotificationReplyMarkerStore,
    private val deliveryState: ProactiveDeliveryStateStore,
    private val continuityContext: CompanionContinuityContextAssembler,
    private val imageWorkGate: ImageWorkGate
) {
    private val processingGate = EventProcessingGate()

    suspend fun process(eventId: String): AgentTurnResult {
        if (!processingGate.tryAcquire(eventId)) {
            Log.d(TAG, "Proactive event already being processed: $eventId")
            return AgentTurnResult.Ignored
        }
        return try {
            processOnce(eventId)
        } finally {
            processingGate.release(eventId)
        }
    }

    private suspend fun processOnce(eventId: String): AgentTurnResult {
        val event = dao.event(eventId) ?: return AgentTurnResult.Failed("Unknown event")
        if (event.state != EventState.NEW && event.state != EventState.DEFERRED) return AgentTurnResult.Ignored
        if (event.source == "system:unanswered") {
            dao.updateEventState(event.id, EventState.CONSUMED, null, System.currentTimeMillis())
            return AgentTurnResult.Ignored
        }
        val receivedAt = System.currentTimeMillis()
        if (!ProactiveEventFreshness.mayEnterProactivePlanning(event.source, event.occurredAt, receivedAt)) {
            dao.updateEventState(event.id, EventState.CONSUMED, null, receivedAt)
            Log.i(TAG, "Expired notification ignored for event=${event.id}")
            return AgentTurnResult.Ignored
        }
        // 出图正在进行：这时候去调对话模型会把正在出的图拖慢/打断（网关要在两种模式间装卸模型、
        // 一块显卡装不下两边）。感知事件先放到 DEFERRED，等出图结束再处理 —— 不丢，只是晚一点。
        if (imageWorkGate.busy) {
            dao.updateEventState(event.id, EventState.DEFERRED, receivedAt + IMAGE_BUSY_RETRY_MS, null)
            Log.i(TAG, "出图进行中，感知事件延后处理 event=${event.id}")
            return AgentTurnResult.Ignored
        }
        val relationshipPulse = UnansweredEventTarget.parse(event.source)        // Battery, weather, media, and network can arrive in the same sensing pass.
        // The relationship planner receives the full fresh snapshot and decides what matters;
        // do not let the final insert order discard the other current facts.
        val profile = plannerProfile() ?: run {
            dao.updateEventState(event.id, EventState.DEFERRED, receivedAt + MODEL_UNAVAILABLE_RETRY_MS, null)
            return AgentTurnResult.ModelNotConfigured
        }
        val snapshot = CapabilitySnapshotFactory.create(shizukuStatusProvider.current(shizukuRouter.isCommandChannelAlive))
        val now = System.currentTimeMillis()
        val lifeContext = lifeSnapshotFactory.capture(now, event.id)
        val activeCharacterIds = ProactiveCharacterSelector
            .resolveAll(systemModeState.proactiveCharacterIds, dao.characterIds(), systemModeState.proactiveSelectionConfigured)
            .filterNot(systemModeState::isProactivePaused)
            .let { characters -> relationshipPulse?.let { target -> characters.filter { it == target.characterId } } ?: characters }
        if (activeCharacterIds.isEmpty()) {
            dao.updateEventState(event.id, EventState.CONSUMED, null, now)
            return AgentTurnResult.Ignored
        }
        dao.insertEventRecipients(activeCharacterIds.map { EventRecipientEntity(event.id, it, EventState.NEW, null, null) })

        val recipientResults = ConcurrentModelWork.mapCatching(dao.pendingRecipients(event.id, now)) { recipient ->
            when {
                systemModeState.isProactivePaused(recipient.characterId) -> {
                    dao.updateRecipientState(event.id, recipient.characterId, EventState.CONSUMED, null, now)
                    AgentTurnResult.Ignored
                }
                else -> {
                    val character = dao.character(recipient.characterId)
                    if (character == null) {
                        dao.updateRecipientState(event.id, recipient.characterId, EventState.CONSUMED, null, now)
                        AgentTurnResult.Ignored
                    } else {
                        processForCharacter(event, character, profile, snapshot, lifeContext)
                    }
                }
            }
        }
        recipientResults.filter { it.isFailure }.forEach { result ->
            Log.w(TAG, "Independent proactive recipient failed for event=${event.id}", result.exceptionOrNull())
        }
        val result = recipientResults.mapNotNull { it.getOrNull() }
            .firstOrNull { it !is AgentTurnResult.Ignored }
            ?: AgentTurnResult.Ignored
        if (dao.unresolvedRecipientCount(event.id) == 0) {
            dao.updateEventState(event.id, EventState.CONSUMED, null, now)
        } else {
            dao.updateEventState(event.id, EventState.DEFERRED, dao.nextRecipientDue(event.id) ?: now + FALLBACK_RECIPIENT_RETRY_MS, null)
        }
        return result
    }

    private suspend fun processForCharacter(
        event: EventEntity,
        character: CharacterEntity,
        profile: ModelProfileEntity,
        snapshot: com.aster.companion.domain.CompanionCapabilitySnapshot,
        lifeContext: List<LifeSnapshotFact>
    ): AgentTurnResult {
        val notificationKey = event.takeIf { it.source.startsWith("notification:") }
            ?.let { NotificationReplyMarkerPolicy.key(it.source, it.summary) }
        if (notificationKey != null && notificationReplyMarkers.contains(notificationKey)) {
            dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, System.currentTimeMillis())
            Log.i(TAG, "Notification already replied; ignored for event=${event.id}")
            return AgentTurnResult.Ignored
        }
        val thread = repository.ensurePrivateThread(character.id)
        val unansweredTarget = UnansweredEventTarget.parse(event.source)
        if (unansweredTarget != null && unansweredTarget.threadId != thread.id) {
            dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, System.currentTimeMillis())
            return AgentTurnResult.Ignored
        }
        val contextVersion = thread.contextVersion
        val now = System.currentTimeMillis()
        // A cleared thread advances updatedAt. Old queued events must never recreate its deleted context.
        if (!ConversationResetGuard.mayEnterConversation(event.occurredAt, thread.updatedAt)) {
            dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, now)
            return AgentTurnResult.Ignored
        }
        val urgent = ProactiveMessageCooldown.isUrgent(event.source, event.summary)
        // Each relationship pulse is a distinct moment. Hashing only its generic summary
        // used to suppress every later attempt for a full week.
        val eventKey = if (unansweredTarget != null) "relationship:${event.id}" else deliveryState.eventKey(event.source, event.summary)
        when (val attempt = deliveryState.begin(character.id, eventKey, now, urgent)) {
            ProactiveAttemptResult.Started -> Unit
            ProactiveAttemptResult.AlreadyDelivered,
            ProactiveAttemptResult.Suppressed -> {
                dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, now)
                return AgentTurnResult.Ignored
            }
            is ProactiveAttemptResult.WaitUntil -> {
                dao.updateRecipientState(event.id, character.id, EventState.DEFERRED, attempt.at, null)
                return AgentTurnResult.Deferred(attempt.at)
            }
        }
        val apiKey = requireNotNull(profile.encryptedApiKey)
        val recentMessages = dao.recentMessages(thread.id, RECENT_CONVERSATION_LIMIT).asReversed()
        val recent = recentMessages.map { message ->
            if (message.fromUser) "用户：${message.body}" else "${character.name}：${message.body}"
        }
        val lastUser = recentMessages.lastOrNull { it.fromUser }?.body?.take(500)
        val lastCharacterIndex = recentMessages.indexOfLast { !it.fromUser }
        val lastCharacter = recentMessages.getOrNull(lastCharacterIndex)?.body?.take(500)
        val userHasRepliedAfterCharacter = lastCharacterIndex >= 0 && recentMessages.drop(lastCharacterIndex + 1).any { it.fromUser }
        val userSavedMemories = dao.trustedCharacterMemories(character.id, 12).map { "记忆：${it.content}" }
        val semanticQuery = SemanticMemoryQueryBuilder.build(recent, event.summary, lifeContext)
        val semanticMemories = semanticMemoryRetriever.retrieve(character.id, semanticQuery, SEMANTIC_MEMORY_LIMIT)
            .map { "Long-term relationship diary or memory: ${it.content}" }
        val privateWorld = dao.worldStates(character.id, "PRIVATE")
            .take(WORLD_STATE_LIMIT)
            .map { "Private character continuity only, not user reality: ${it.content}" }
        val sharedWorld = dao.worldStates(character.id, "SHARED")
            .filter { it.threadId == thread.id }
            .take(WORLD_STATE_LIMIT)
            .map { "Explicitly shared facts or plans only: ${it.content}" }
        val continuity = continuityContext.forPrivateCharacter(character.id, thread.id, now)
        val availableGroupTargets = if (snapshot.capabilities.any { it.id == "companion.send_group_message" }) {
            dao.allThreads()
                .filter { group ->
                    group.type == com.aster.companion.domain.ConversationType.GROUP &&
                        character.id in dao.participantIds(group.id)
                }
                .map { group -> "group_thread_id=${group.id}; title=${group.title}" }
        } else emptyList()
        val prompt = PersonaPromptBuilder.build(
            CharacterCard(character.id, character.name, character.personalityPrompt, character.appearancePrompt, character.referenceImageUri, character.relationshipPrompt),
            snapshot,
            ProactiveContextPolicy.planningContext(
                recentConversation = recent,
                userSavedMemories = userSavedMemories,
                semanticMemories = semanticMemories,
                privateWorld = privateWorld,
                sharedWorld = sharedWorld
            ),
            buildList {
                add(ProactiveDecisionInstruction.forEvent(event.source, event.summary, unansweredTarget?.kind))
                add(
                    "Runtime state: system mode=${if (systemModeState.enabled) "enabled" else "disabled"}; " +
                        "this character is enabled and not paused; local frequency, de-duplication, and safety guards already passed. " +
                        "The capability list is the real-time permission snapshot."
                )
                add("主动上下文事实：最近用户消息=${lastUser ?: "无"}; 最近角色消息=${lastCharacter ?: "无"}; 角色最后发言后用户是否再次说话=$userHasRepliedAfterCharacter。若为 false，用户已经收到过角色对旧话题的回复，禁止再次回答或改写那条旧问题。")
                add("当前触发：来源=${event.source}; 时间=${TimeLabel.of(event.occurredAt)}; 内容=${event.summary}")
                lifeContext.forEach { related ->
                    add("近期生活上下文：来源=${related.source}; 时间=${TimeLabel.of(related.observedAt)}; 内容=${related.summary}")
                }
            },
            groupContext = continuity.groupContext,
            socialContext = continuity.momentContext,
            activityContext = continuity.activityContext,
            availableGroupTargets = availableGroupTargets
        )
        val raw = client.chat(profile, decrypt(apiKey), prompt, "请对这个新事件作出一次主动陪伴决策。")
            .getOrElse { error ->
                Log.w(TAG, "Proactive planner request failed for source=${event.source}, character=${character.id}", error)
                deferAfterFailure(event.id, character.id, now)
                return AgentTurnResult.Failed(error.message ?: "Model request failed")
            }
        dao.upsertAgentRun(
            AgentRunEntity(
                id = UUID.randomUUID().toString(),
                triggerType = "proactive_event",
                threadId = thread.id,
                characterId = character.id,
                timelineEventId = event.id,
                state = "PLANNED",
                planJson = raw.take(12_000),
                resultJson = null,
                createdAt = now,
                completedAt = null
            )
        )
        val parsedResult = runCatching { AgentDecisionParser.parse(raw) }
        val proactiveImageAction = ProactiveImageIntentPolicy.proposalAction(
            characterId = character.id,
            characterName = character.name,
            text = parsedResult.getOrNull()?.message?.takeIf(String::isNotBlank) ?: raw,
            currentContext = event.summary
        )
        val parsedDecision = parsedResult.getOrNull()?.let { decision ->
            if (proactiveImageAction == null || decision.actions.any { action ->
                    action.capabilityId == "companion.generate_image" || action.capabilityId == "companion.propose_image"
                }
            ) {
                decision
            } else {
                ProactiveImageIntentPolicy.attachProposal(decision, proactiveImageAction)
            }
        } ?: proactiveImageAction?.let { action ->
            com.aster.companion.domain.AgentDecision(
                kind = com.aster.companion.domain.AgentDecisionKind.TOOL_CALLS,
                threadType = ConversationType.PRIVATE,
                actions = listOf(action)
            )
        }
        parsedResult.exceptionOrNull()?.let { error ->
            Log.w(TAG, "Proactive planner returned an invalid decision for source=${event.source}, character=${character.id}: ${error.message}")
        }
        val isConfirmedDelivery = urgent
        val usesDeliveryFallback = parsedDecision == null ||
            UrgentProactiveDeliveryPolicy.needsFallback(isConfirmedDelivery, parsedDecision?.kind ?: com.aster.companion.domain.AgentDecisionKind.IGNORE)
        val decision = when {
            parsedDecision == null && !isConfirmedDelivery -> {
                persistTurn(thread.id, event.id, snapshot.version, raw)
                deferAfterFailure(event.id, character.id, now)
                return AgentTurnResult.InvalidDecision
            }
            usesDeliveryFallback -> {
                val fallbackMessage = client.chat(
                    profile,
                    decrypt(apiKey),
                    "You are ${character.name}. Follow this personality: ${character.personalityPrompt}\nA confirmed delivery notification just arrived. Write exactly one short, natural in-character private reminder. Mention only the delivery fact. Do not invent prior conversation, actions, locations, or stage directions.",
                    event.summary
                ).getOrNull()?.trim()?.takeIf(String::isNotEmpty)
                if (fallbackMessage == null) {
                    persistTurn(thread.id, event.id, snapshot.version, raw)
                    deferAfterFailure(event.id, character.id, now)
                    return AgentTurnResult.Failed("Unable to compose delivery reminder")
                }
                com.aster.companion.domain.AgentDecision(
                    com.aster.companion.domain.AgentDecisionKind.SEND_NOW,
                    com.aster.companion.domain.ConversationType.PRIVATE,
                    fallbackMessage,
                    null,
                    emptyList()
                )
            }
            else -> parsedDecision
        }
        val currentThread = dao.thread(thread.id)
        if (systemModeState.isProactivePaused(character.id) ||
            !ProactiveDeliveryGuard.canDeliver(dao.character(character.id) != null, currentThread != null) ||
            !ConversationContextVersion.isCurrent(contextVersion, currentThread?.contextVersion ?: -1)
        ) {
            deliveryState.clearAttempt(character.id, now)
            dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, System.currentTimeMillis())
            return AgentTurnResult.Ignored
        }
        persistTurn(thread.id, event.id, snapshot.version, raw)
        if (CompanionInvariants.validateDecision(decision, snapshot) !is DecisionValidation.Accepted) {
            deferAfterFailure(event.id, character.id, now)
            return AgentTurnResult.InvalidDecision
        }

        // 家庭模式：角色不主动私聊（一家人都在场，私聊改为在群里聊，或者不主动发）
        val inFamilyGroup = thread.type == com.aster.companion.domain.ConversationType.PRIVATE && runCatching {
            dao.allThreads().any { group ->
                group.type == com.aster.companion.domain.ConversationType.GROUP &&
                    dao.participantIds(group.id).contains(character.id) &&
                    dao.groupSettings(group.id)?.groupType == "FAMILY"
            }
        }.getOrDefault(false)

        return when (decision.kind) {
            com.aster.companion.domain.AgentDecisionKind.SEND_NOW -> {
                if (inFamilyGroup) {
                    // 家庭模式禁止角色私聊：改为在家庭群里自然说话（触发一次群聊自主），不在私聊里发。
                    deliveryState.clearAttempt(character.id, now)
                    dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, now)
                    return AgentTurnResult.Ignored
                }
                val candidate = requireNotNull(decision.message)
                val body = ProactiveDeliveryTextPolicy.finalText(candidate, null)
                if (!UrgentProactiveDeliveryPolicy.mayDeliverWithoutGenericGrounding(isConfirmedDelivery, usesDeliveryFallback) &&
                    !passesGroundingReview(profile, body, recent, userSavedMemories, lifeContext)
                ) {
                    Log.i(TAG, "Proactive message withheld by grounding review for character=${character.id}, event=${event.id}")
                    deliveryState.clearAttempt(character.id, now)
                    dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, now)
                    return AgentTurnResult.Ignored
                }
                val message = MessageEntity(UUID.randomUUID().toString(), thread.id, character.id, body, null, false, now)
                deliveryState.recordDelivery(character.id, eventKey, now)
                dao.insertMessage(message)
                timeline.recordMessage(message)
                dao.touchThread(thread.id, now)
                notificationKey?.let { notificationReplyMarkers.markReplied(it, now) }
                dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, now)
                notificationPublisher.publish(character.name, body, thread.id)
                Log.i(TAG, "Proactive message delivered for character=${character.id}, event=${event.id}")
                AgentTurnResult.Sent(thread.id)
            }
            com.aster.companion.domain.AgentDecisionKind.DEFER -> {
                val until = requireNotNull(decision.deferUntil)
                deliveryState.clearAttempt(character.id, now)
                dao.updateRecipientState(event.id, character.id, EventState.DEFERRED, until.coerceAtLeast(now + 1_000L), null)
                AgentTurnResult.Deferred(until)
            }
            com.aster.companion.domain.AgentDecisionKind.IGNORE -> {
                deliveryState.clearAttempt(character.id, now)
                dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, now)
                AgentTurnResult.Ignored
            }
            com.aster.companion.domain.AgentDecisionKind.TOOL_CALLS -> {
                val containsImageProposal = decision.actions.any { it.capabilityId == "companion.generate_image" || it.capabilityId == "companion.propose_image" }
                var deliveredMessage = false
                // 家庭模式禁止角色私聊：主动消息/私聊消息都不在私聊线程发
                val messageText = if (inFamilyGroup) null else (decision.message ?: if (containsImageProposal) "我刚好想给你发张照片，你想看吗？" else null)
                messageText?.let { message ->
                    val companionMessage = MessageEntity(UUID.randomUUID().toString(), thread.id, character.id, message, null, false, now)
                    deliveryState.recordDelivery(character.id, eventKey, now)
                    deliveredMessage = true
                    dao.insertMessage(companionMessage)
                    timeline.recordMessage(companionMessage)
                    dao.touchThread(thread.id, now)
                    notificationKey?.let { notificationReplyMarkers.markReplied(it, now) }
                    notificationPublisher.publish(character.name, message, thread.id)
                }
                decision.actions.filter { action -> !(inFamilyGroup && action.capabilityId == "companion.send_private_message") }.forEach { action ->
                    if (action.capabilityId == "companion.generate_image" || action.capabilityId == "companion.propose_image") {
                        val request = action.copy(
                            capabilityId = "companion.propose_image",
                            confirmation = com.aster.companion.domain.ConfirmationRequirement.REQUIRED,
                            arguments = action.arguments + ("character_id" to character.id)
                        )
                        actionCoordinator.submit(event.id, thread.id, request)
                    } else {
                        actionCoordinator.submit(event.id, thread.id, action)
                    }
                }
                if (!deliveredMessage) deliveryState.clearAttempt(character.id, now)
                dao.updateRecipientState(event.id, character.id, EventState.CONSUMED, null, now)
                AgentTurnResult.Ignored
            }
        }
    }

    private suspend fun persistTurn(threadId: String, eventId: String, snapshotVersion: Long, decisionJson: String) {
        dao.insertTurn(AgentTurnEntity(UUID.randomUUID().toString(), threadId, eventId, snapshotVersion, decisionJson, System.currentTimeMillis()))
    }

    private fun decrypt(ciphertext: String): String = secretCipher.decrypt(ciphertext)

    private suspend fun plannerProfile(): ModelProfileEntity? {
        val planning = dao.modelProfile(PLANNING_MODEL)
        if (planning?.enabled == true && !planning.encryptedApiKey.isNullOrBlank()) return planning
        return dao.modelProfile(CONVERSATION_MODEL)?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
    }

    private suspend fun passesGroundingReview(
        profile: ModelProfileEntity,
        candidate: String,
        recent: List<String>,
        userSavedMemories: List<String>,
        lifeContext: List<LifeSnapshotFact>
    ): Boolean {
        val realEvents = lifeContext.map { "${it.source}: ${it.summary}" }
        val facts = buildString {
            appendLine("TRUSTED FACTS ONLY:")
            ProactiveContextPolicy.groundingFacts(recent, userSavedMemories, realEvents).forEach(::appendLine)
        }
        val verdict = client.chat(
            profile,
            decrypt(requireNotNull(profile.encryptedApiKey)),
            "Audit a proposed proactive message. Return JSON only: {\"verdict\":\"allow\"} or {\"verdict\":\"reject\"}. Enforce this as a hard boundary: every claim about the user or shared reality must be supported by the supplied recent conversation, confirmed memories, or fresh device facts. Reject invented meetings, locations, user actions, feelings, promises, relationship progress, or any attempt to make the user participate in character fiction. Device facts belong to the user, never the character. A natural reaction to a supplied fact, a feeling, a gentle invitation, or a question that makes no unsupported factual claim must be allowed; do not reject merely because it is an affectionate companion message.",
            "PROPOSED MESSAGE:\n$candidate\n\nFACTS:\n$facts"
        ).getOrNull() ?: return false
        return ProactiveGroundingVerdict.allows(verdict)
    }

    private suspend fun deferAfterFailure(eventId: String, characterId: String, now: Long) {
        dao.updateRecipientState(eventId, characterId, EventState.DEFERRED, deliveryState.recordFailure(characterId, now), null)
    }

    companion object {
        const val CONVERSATION_MODEL = "conversation"
        const val PLANNING_MODEL = "planning"
        private const val FALLBACK_RECIPIENT_RETRY_MS = 2 * 60 * 1000L
        private const val MODEL_UNAVAILABLE_RETRY_MS = 15 * 60 * 1000L
        /** 出图期间感知事件延后多久再看（出图结束后很快会重试到）。 */
        private const val IMAGE_BUSY_RETRY_MS = 20 * 1000L
        private const val RECENT_CONVERSATION_LIMIT = 120
        private const val SEMANTIC_MEMORY_LIMIT = 12
        private const val WORLD_STATE_LIMIT = 2
        private const val TAG = "TongpinProactive"
    }
}
