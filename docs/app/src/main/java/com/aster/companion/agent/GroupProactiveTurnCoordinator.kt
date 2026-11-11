package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionRepository
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.CompanionTimelineRecorder
import com.aster.companion.data.MessageEntity
import com.aster.companion.domain.ConversationContextVersion
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.domain.GroupMemberContext
import com.aster.companion.domain.GroupMemberContextFormatter
import com.aster.companion.domain.GroupProactivePolicy
import com.aster.companion.proactive.ProactiveAttemptResult
import com.aster.companion.proactive.ProactiveDeliveryStateStore
import com.aster.companion.proactive.LifeSnapshotFactory
import com.aster.companion.proactive.SystemModeState
import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Creates an optional, in-app-only family-group turn after the same idle interval used by private chats. */
@Singleton
class GroupProactiveTurnCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val repository: CompanionRepository,
    private val cipher: CompanionSecretCipher,
    private val client: OpenAiCompatibleClient,
    private val systemModeState: SystemModeState,
    private val deliveryState: ProactiveDeliveryStateStore,
    private val continuityContext: CompanionContinuityContextAssembler,
    private val timeline: CompanionTimelineRecorder,
    private val lifeSnapshotFactory: LifeSnapshotFactory,
    private val imagePromptDebugSettings: ImagePromptDebugSettings,
    private val imageRuleResolver: ImageGenerationRuleResolver
) {
    private val crossMessageLocks = ConcurrentHashMap<String, Mutex>()

    /** Lets an actual cross-chat group message receive the same short, model-led character exchange as a native group turn. */
    suspend fun reactToCrossConversationMessage(threadId: String, messageId: String): Boolean =
        crossMessageLocks.getOrPut(threadId) { Mutex() }.withLock {
            val thread = dao.thread(threadId) ?: return@withLock false
            if (thread.type != ConversationType.GROUP) return@withLock false
            val source = dao.message(messageId) ?: return@withLock false
            val memberIds = dao.participantIds(threadId).distinct()
            if (source.threadId != threadId || source.fromUser || source.characterId !in memberIds ||
                dao.recentMessages(threadId, 1).firstOrNull()?.id != messageId
            ) return@withLock false

            val atmosphere = dao.groupSettings(threadId)?.atmosphere ?: GroupAtmosphere.DAILY
            val maxFollowUps = minOf(atmosphere.maxFollowUps, CROSS_MESSAGE_MAX_FOLLOW_UPS)
            if (maxFollowUps == 0) return@withLock false
            val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
                ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
                ?: return@withLock false
            val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
            val history = dao.recentMessages(threadId, GroupConversationWindow.DEFAULT_MAX_MESSAGES).asReversed().toMutableList()
            var previousSpeakerId = requireNotNull(source.characterId)
            var followUpsUsed = 0
            var sent = false

            while (followUpsUsed < maxFollowUps) {
                val nextSpeakerId = selectContinuation(
                    profile = profile,
                    apiKey = apiKey,
                    eligibleMemberIds = memberIds.toSet(),
                    previousSpeakerId = previousSpeakerId,
                    turnHistory = history,
                    followUpsUsed = followUpsUsed,
                    maxFollowUps = maxFollowUps
                ) ?: break
                val character = dao.character(nextSpeakerId) ?: break
                val transcript = groupTranscript(history)
                val continuity = continuityContext.forGroup(threadId, memberIds.toSet(), System.currentTimeMillis(), nextSpeakerId)
                val ownPrivateConversation = dao.latestPrivateThread(nextSpeakerId)?.let { privateThread ->
                    GroupPrivateConversationContextPolicy.render(
                        ownerCharacterId = nextSpeakerId,
                        lines = dao.recentMessages(privateThread.id, PRIVATE_GROUP_CONTEXT_LIMIT)
                            .asReversed()
                            .map { message ->
                                GroupPrivateConversationContextPolicy.Line(message.characterId, message.fromUser, message.body)
                            }
                    )
                }.orEmpty()
                val directive = "The immediately previous character message is the only trigger. Reply to it naturally if it merits an answer; do not restart the group or turn it into a long exchange."
                val routedReply = client.chat(
                    profile = profile,
                    apiKey = apiKey,
                    systemPrompt = groupPrompt(
                        imageRule = imageRuleResolver.resolve(),
                        name = character.name,
                        personality = character.personalityPrompt,
                        appearance = character.appearancePrompt,
                        relationship = character.relationshipPrompt,
                        atmosphere = atmosphere,
                        groupTranscript = transcript,
                        groupContext = continuity.groupContext + listOfNotNull(continuity.momentContext.takeIf(String::isNotBlank)) + continuity.activityContext,
                        ownPrivateConversation = ownPrivateConversation,
                        lifeContext = emptyList(),
                        proactiveDirective = directive
                    ),
                    userPrompt = GroupTranscriptFormatter.replyRequest(
                        responderId = nextSpeakerId,
                        responderName = character.name,
                        userText = "No new user message. React only to the immediately previous character message when it naturally calls for you.",
                        transcript = transcript,
                        proactiveDirective = directive,
                        outputContract = GroupReplyDeliveryPlanParser.OUTPUT_CONTRACT
                    )
                ).getOrNull()?.let(GroupReplyDeliveryPlanParser::parseOrGroup) ?: break

                val message = persistRoutedReply(threadId, nextSpeakerId, routedReply) ?: continue
                if (routedReply.delivery == GroupReplyDelivery.PRIVATE && dao.groupSettings(threadId)?.groupType != "FAMILY") {
                    sent = true
                    break
                }
                history += message
                sent = true
                previousSpeakerId = nextSpeakerId
                followUpsUsed += 1
            }
            Log.i(TAG, "cross_message_reaction sent=$sent followups_used=$followUpsUsed")
            sent
        }

    suspend fun process(threadId: String, now: Long = System.currentTimeMillis()): Boolean {
        val thread = dao.thread(threadId) ?: return false
        if (thread.type != ConversationType.GROUP) return false
        val groupSettings = dao.groupSettings(threadId)
        // 家庭模式：组装情景剧场描述（面对面/距离感知/私密规则），注入角色主动说话时
        val familyDescription = if (groupSettings?.groupType == "FAMILY") {
            val memberIds = dao.participantIds(threadId)
            val locationLines = memberIds.mapNotNull { id ->
                val name = dao.character(id)?.name ?: return@mapNotNull null
                val activity = dao.livingState(id)?.currentActivity.orEmpty()
                val location = extractLocation(activity)
                name to location
            }
            buildString {
                appendLine("「${thread.title}」是你们的家——这不是远程群聊，是真实家庭的情景剧场：面对面说话，或者在别的房间就喊一声/走到一起说。说话方式要符合各自此刻的状态。")
                appendLine("距离感知：你大致知道家里人在哪（同一个屋檐下的自然感知），但不知道她们具体在做什么：")
                locationLines.forEach { (name, loc) -> appendLine("- $name 在${loc ?: "家里某处"}") }
                appendLine("私密规则：用户可能小声对某人说话或偷偷做事，你也可以（用「（小声对XX说）」或「（偷偷）」）——只有当事人知道内容/原因；其他人只察觉有动静，可以有自然反应但不能说破。看到带「（小声对XX说）」的消息是别人的悄悄话，你只能感觉有动静、听不清内容。")
            }.toString()
        } else ""
        val members = dao.participantIds(threadId)
        val eligibleMembers = GroupProactivePolicy.eligibleMembers(
            memberIds = members,
            pausedMemberIds = members.filter(systemModeState::isProactivePaused).toSet()
        )
        if (!GroupProactivePolicy.canStart(systemModeState.enabled, eligibleMembers.isEmpty(), members.size >= 2, groupSettings?.proactiveEnabled ?: true)) return false
        val last = dao.recentMessages(threadId, 1).firstOrNull() ?: return false
        if (now - last.createdAt < IDLE_INTERVAL_MS) return false
        val profile = dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return false
        val groupKey = "group:$threadId"
        val eventKey = "group-presence:${last.id}:${now / IDLE_INTERVAL_MS}"
        when (deliveryState.begin(groupKey, eventKey, now, urgent = false)) {
            ProactiveAttemptResult.Started -> Unit
            else -> return false
        }

        val contextVersion = thread.contextVersion
        val atmosphere = groupSettings?.atmosphere ?: GroupAtmosphere.DAILY
        val history = dao.recentMessages(threadId, GroupConversationWindow.DEFAULT_MAX_MESSAGES).asReversed()
        val lifeContext = GroupProactiveLifeContextPolicy.lines(lifeSnapshotFactory.capture(now))
        val initialTranscript = groupTranscript(history)
        val continuity = continuityContext.forGroup(threadId, members.toSet(), now)
        val context = buildList {
            addAll(continuity.groupContext)
            continuity.momentContext.takeIf(String::isNotBlank)?.let(::add)
            addAll(continuity.activityContext)
        }
        val memberContexts = eligibleMembers.mapNotNull { id ->
            dao.character(id)?.let { GroupMemberContext(it.id, it.name, it.relationshipPrompt) }
        }
        val selectionResult = client.chat(
            profile,
            cipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            GroupProactivePrompt.selectionSystemPrompt(atmosphere),
            buildString {
                appendLine("Members: ${GroupMemberContextFormatter.render(memberContexts)}")
                appendLine("Current authoritative group context window:")
                appendLine(initialTranscript)
                context.forEach(::appendLine)
                if (lifeContext.isNotEmpty()) {
                    appendLine("Current group-safe life signals; use only when naturally relevant:")
                    lifeContext.forEach(::appendLine)
                }
            }
        )
        if (selectionResult.isFailure) {
            deliveryState.recordFailure(groupKey, now)
            return false
        }
        val selection = selectionResult.getOrThrow()
        val plan = GroupTurnPlanParser.parse(
            raw = selection,
            memberIds = eligibleMembers.toSet(),
            atmosphere = atmosphere,
            memberNameToId = groupMemberNameToId(memberContexts)
        )
        Log.i(
            TAG,
            "proactive_plan raw_length=${selection.length} main=${plan.mainSpeakerId ?: "none"} followups=${plan.followUpSpeakerIds.joinToString(",")} intent=${plan.intent}"
        )
        val speakerQueue = ArrayDeque(GroupDynamicTurnPolicy.initialSpeakers(plan))
        if (speakerQueue.isEmpty()) {
            deliveryState.recordDecision(groupKey, eventKey, now)
            return false
        }

        var sent = false
        var modelFailure = false
        val turnHistory = history.toMutableList()
        var followUpsUsed = 0
        val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
        while (speakerQueue.isNotEmpty()) {
            val characterId = speakerQueue.removeFirst()
            val current = dao.thread(threadId)
            if (!ConversationContextVersion.isCurrent(contextVersion, current?.contextVersion ?: -1) ||
                characterId !in dao.participantIds(threadId) ||
                systemModeState.isProactivePaused(characterId)
            ) {
                break
            }
            val character = dao.character(characterId) ?: break
            val previousSpeaker = GroupProactivePrompt.immediatePreviousSpeakerName(
                groupLines(turnHistory)
            )
            val proactiveDirective = if (sent) {
                GroupProactivePrompt.continuationDirective(characterId, character.name, previousSpeaker)
            } else {
                GroupProactivePrompt.replyDirective(plan, characterId, character.name, previousSpeaker)
            }
            val roleContinuity = continuityContext.forGroup(threadId, members.toSet(), now, characterId)
            val roleContext = buildList {
                addAll(roleContinuity.groupContext)
                roleContinuity.momentContext.takeIf(String::isNotBlank)?.let(::add)
                addAll(roleContinuity.activityContext)
            }
            val ownPrivateConversation = dao.latestPrivateThread(characterId)?.let { privateThread ->
                GroupPrivateConversationContextPolicy.render(
                    ownerCharacterId = characterId,
                    lines = dao.recentMessages(privateThread.id, PRIVATE_GROUP_CONTEXT_LIMIT)
                        .asReversed()
                        .map { message ->
                            GroupPrivateConversationContextPolicy.Line(
                                characterId = message.characterId,
                                fromUser = message.fromUser,
                                body = message.body
                            )
                        }
                )
            }.orEmpty()
            val groupTranscript = groupTranscript(turnHistory)
            val replyResult = client.chat(
                profile,
                apiKey,
                groupPrompt(imageRuleResolver.resolve(), character.name, character.personalityPrompt, character.appearancePrompt, character.relationshipPrompt, atmosphere, groupTranscript, roleContext, ownPrivateConversation, lifeContext, proactiveDirective, familyDescription = familyDescription),
                GroupTranscriptFormatter.replyRequest(
                    responderId = characterId,
                    responderName = character.name,
                    userText = "No new user message; this is a planned proactive group turn.",
                    transcript = groupTranscript,
                    proactiveDirective = proactiveDirective,
                    outputContract = GroupReplyDeliveryPlanParser.OUTPUT_CONTRACT
                )
            )
            if (replyResult.isFailure) {
                modelFailure = true
                break
            }
            val routedReply = GroupReplyDeliveryPlanParser.parseOrGroup(replyResult.getOrThrow()) ?: break
            val message = persistRoutedReply(threadId, characterId, routedReply) ?: continue
            if (routedReply.delivery == GroupReplyDelivery.PRIVATE && dao.groupSettings(threadId)?.groupType != "FAMILY") {
                sent = true
                break
            }
            turnHistory += message
            sent = true
            if (followUpsUsed < atmosphere.maxFollowUps) {
                selectContinuation(
                    profile = profile,
                    apiKey = apiKey,
                    eligibleMemberIds = eligibleMembers.toSet(),
                    previousSpeakerId = characterId,
                    turnHistory = turnHistory,
                    followUpsUsed = followUpsUsed,
                    maxFollowUps = atmosphere.maxFollowUps
                )?.let { nextSpeakerId ->
                    speakerQueue.addLast(nextSpeakerId)
                    followUpsUsed += 1
                }
            }
        }
        when {
            sent -> deliveryState.recordDelivery(groupKey, eventKey, now)
            modelFailure -> deliveryState.recordFailure(groupKey, now)
            else -> deliveryState.recordDecision(groupKey, eventKey, now)
        }
        return sent
    }

    private suspend fun selectContinuation(
        profile: com.aster.companion.data.ModelProfileEntity,
        apiKey: String,
        eligibleMemberIds: Set<String>,
        previousSpeakerId: String,
        turnHistory: List<MessageEntity>,
        followUpsUsed: Int,
        maxFollowUps: Int
    ): String? {
        if (followUpsUsed >= maxFollowUps) return null
        val members = eligibleMemberIds.mapNotNull { id ->
            dao.character(id)?.let { GroupMemberContext(it.id, it.name, it.relationshipPrompt) }
        }
        val transcript = groupTranscript(turnHistory)
        val raw = client.chat(
            profile = profile,
            apiKey = apiKey,
            systemPrompt = """
                Decide whether one different character should naturally speak next in this live group chat.
                Return JSON only: {"next_speaker_id":"member id or null"}.
                Select a member only when the newest visible character message gives them a real reason to react, answer, disagree, continue, or close the exchange. If the newest character explicitly names, asks, or invites another current member, select that member unless a reply is impossible from the transcript. Do not select the previous speaker, do not make a roster speak, and do not invent an omitted user message or event. Return null when another message would feel forced.
                These are people who know each other (family/close group): casual back-and-forth between members is natural and welcome, like real family chatting in a group. Prefer continuing the exchange when it is alive; do not cut it off early.
            """.trimIndent(),
            userPrompt = buildString {
                appendLine("Current members: ${GroupMemberContextFormatter.render(members)}")
                appendLine("Previous speaker id: $previousSpeakerId")
                appendLine("Follow-ups already used: $followUpsUsed of $maxFollowUps")
                appendLine("AUTHORITATIVE GROUP CONTEXT WINDOW:")
                append(transcript)
            }
        ).getOrNull()
        val next = raw?.let {
            GroupContinuationPlanParser.parse(
                raw = it,
                memberIds = eligibleMemberIds,
                previousSpeakerId = previousSpeakerId,
                memberNameToId = groupMemberNameToId(members)
            )
        } ?: turnHistory.lastOrNull()?.takeIf { message -> !message.fromUser }?.let { message ->
            GroupDirectAddressPolicy.questionedMemberId(message.body, previousSpeakerId, members)
        }
        Log.i(TAG, "proactive_continuation_selected=${next ?: "none"} previous=$previousSpeakerId followups_used=$followUpsUsed response_present=${!raw.isNullOrBlank()} model_result_valid=${next != null}")
        return next
    }

    private fun groupMemberNameToId(members: List<GroupMemberContext>): Map<String, String> = buildMap {
        members.forEach { member ->
            val name = member.name.trim()
            if (name.isBlank()) return@forEach
            put(name, member.id)
            Regex("（([^）]+)）|\\(([^)]+)\\)").findAll(name).forEach { match ->
                match.groupValues.drop(1).firstOrNull(String::isNotBlank)?.trim()?.let { alias -> put(alias, member.id) }
            }
        }
    }

    private suspend fun groupLines(messages: List<MessageEntity>): List<GroupTranscriptLine> = messages.map { message ->
        GroupTranscriptLine(
            messageId = message.id,
            speakerId = message.characterId,
            speakerName = if (message.fromUser) "用户" else dao.character(message.characterId.orEmpty())?.name ?: "角色",
            isUser = message.fromUser,
            body = message.body
        )
    }

    private suspend fun groupTranscript(messages: List<MessageEntity>): String =
        GroupConversationWindow.forModel(groupLines(messages)).renderedTranscript

    private suspend fun persistRoutedReply(
        groupThreadId: String,
        characterId: String,
        reply: GroupReplyDeliveryPlan
    ): MessageEntity? {
        val cleanedBody = RoleplayResponseSanitizer.clean(reply.message)
        if (reply.delivery == GroupReplyDelivery.GROUP && isRecentPrivateDuplicate(characterId, cleanedBody)) {
            Log.i(TAG, "suppressed_group_echo_of_private character=$characterId")
            return null
        }
        val isFamily = dao.groupSettings(groupThreadId)?.groupType == "FAMILY"
        val effectiveDelivery = if (isFamily) GroupReplyDelivery.GROUP else reply.delivery
        val targetThreadId = if (effectiveDelivery == GroupReplyDelivery.PRIVATE) {
            repository.ensurePrivateThread(characterId).id
        } else {
            groupThreadId
        }
        val message = MessageEntity(
            UUID.randomUUID().toString(),
            targetThreadId,
            characterId,
            cleanedBody,
            null,
            false,
            System.currentTimeMillis()
        )
        dao.insertMessage(message)
        timeline.recordMessage(message)
        dao.touchThread(targetThreadId, message.createdAt)
        if (reply.delivery == GroupReplyDelivery.GROUP) {
            dao.groupMemberStates(groupThreadId).firstOrNull { it.characterId == characterId }?.let { state ->
                dao.upsertGroupMemberState(state.copy(attention = (state.attention + 1).coerceAtMost(100), lastSpokeAt = message.createdAt))
            }
        }
        return message
    }

    private suspend fun isRecentPrivateDuplicate(characterId: String, groupCandidate: String): Boolean {
        val privateThread = dao.latestPrivateThread(characterId) ?: return false
        val cutoff = System.currentTimeMillis() - PRIVATE_ECHO_DEDUP_WINDOW_MS
        val recentBodies = dao.recentMessages(privateThread.id, PRIVATE_ECHO_DEDUP_MESSAGE_LIMIT)
            .filter { !it.fromUser && it.characterId == characterId && it.createdAt >= cutoff }
            .map { it.body }
        return CrossChatDuplicatePolicy.isRecentDuplicate(groupCandidate, recentBodies)
    }

    /** 从「在厨房煮面」这类状态里提取地点。 */
    private fun extractLocation(activity: String): String? {
        val trimmed = activity.trim().removePrefix("此刻正在").removePrefix("正在").trim()
        if (!trimmed.startsWith("在")) return null
        val after = trimmed.removePrefix("在")
        val commaIdx = after.indexOfFirst { it == '，' || it == ',' || it == '、' }
        if (commaIdx > 0) return after.substring(0, commaIdx).trim().takeIf(String::isNotBlank)
        val actionTerms = listOf("做", "煮", "看", "洗", "浇", "擦", "切", "坐", "躺", "站", "听", "拿", "喂", "拖", "扫", "收", "折", "晾", "叠", "铺", "换", "摆", "倒", "烧", "炖", "炒", "淘", "刷", "冲", "晒", "理", "找", "发", "回", "收拾", "整理", "打扫", "翻", "选", "挑", "泡", "喝", "吃", "揉", "拍", "挂", "套", "盖", "掀", "关", "开")
        for (i in after.indices) {
            if (actionTerms.any { after.startsWith(it, i) }) return after.substring(0, i).trim().takeIf(String::isNotBlank)
        }
        return null
    }

    private fun groupPrompt(
        imageRule: String,
        name: String,
        personality: String,
        appearance: String,
        relationship: String,
        atmosphere: GroupAtmosphere,
        groupTranscript: String,
        groupContext: List<String>,
        ownPrivateConversation: List<String>,
        lifeContext: List<String>,
        proactiveDirective: String,
        syncedPrivateContext: String = "",
        familyDescription: String = ""
    ): String = buildString {
        imageRule.ifBlank { ImagePromptDebugInstructionComposer.forSceneModel(imagePromptDebugSettings.config) }
            .takeIf(String::isNotBlank)
            ?.let {
                appendLine(it)
                appendLine()
            }
        appendLine(UserRealityBoundaryInstruction.text())
        appendLine("You are $name. Your personality is fixed: $personality")
        appendLine("IMAGE IDENTITY ANCHOR: if you propose an image, this saved appearance is mandatory and must not be altered: ${appearance.take(1_800)}")
        appendLine(ComfyUiEnglishPromptPolicy.hardRule(name))
        if (relationship.isNotBlank()) appendLine("Your relationship with the user: $relationship")
        appendLine("This is a family group, not a private chat. Speak only from your own perspective. You may be lightly jealous, playful, warm, or quiet, but never pressure, shame, isolate, or make the user choose between members.")
        appendLine("私密规则：你可以小声对某位家人单独说话（用「（小声对XX说）内容」格式），或偷偷做点什么（用「（偷偷）...」标注）——只有当事人知道内容；其他角色只察觉有动静，可以有自然反应但绝不能说出私密内容或点破是谁干的。看到带「（小声对XX说）」的消息时，那是别人的悄悄话，你只能感觉有动静、听不清内容。")
        appendLine("The transcript below is the only authoritative record. Do not claim user actions, plans, feelings, locations, or shared experiences unless explicitly present there. Never expose or imply private-chat content.")
        appendLine("A character message is not a user message. You must read the latest visible character message before replying. You may answer another character directly when the transcript gives you something to answer, but you must not invent a missing user question or a missing exchange.")
        appendLine("Choose delivery=GROUP for a message that belongs in this visible family group. Everyone is physically present here, so NEVER choose delivery=PRIVATE in this family group—say everything directly out loud in the group. (delivery=PRIVATE is only available in a remote group-chat mode.) GROUP is always the default here.")
        appendLine("Atmosphere: ${atmosphere.name}. This is one planned group turn. Follow the directive below instead of defaulting to a generic greeting. Keep the message natural and concise.")
        appendLine("PLANNED DIRECTIVE: $proactiveDirective")
        appendLine("Group context:")
        groupContext.forEach(::appendLine)
        if (ownPrivateConversation.isNotEmpty()) {
            appendLine("你和用户私下聊过的事（你在群里说话时可以自然用到，像家人自然说起彼此知道的事，不要整段复述原文、不要点破这是私聊）：")
            ownPrivateConversation.forEach(::appendLine)
        }
        if (syncedPrivateContext.isNotBlank()) {
            appendLine("群里同步过来的私聊内容（某位成员和用户私下聊过的事，已同步到群里让大家知道——自然了解，必要时接话/关心，不要点破这是私聊记录）：")
            appendLine(syncedPrivateContext)
        }
        if (familyDescription.isNotBlank()) {
            appendLine(familyDescription)
        }
        appendLine(GroupProactivePrompt.roleContextInstruction(name))
        if (lifeContext.isNotEmpty()) {
            appendLine("CURRENT GROUP-SAFE LIFE SIGNALS:")
            lifeContext.forEach(::appendLine)
            appendLine("These are background signals, not a report. Mention one only when it naturally fits; never mention raw notification, SMS, package, coordinate, or sensor payloads.")
        }
        appendLine("AUTHORITATIVE GROUP TRANSCRIPT:")
        appendLine(groupTranscript)
        appendLine(GroupReplyDeliveryPlanParser.OUTPUT_CONTRACT)
    }

    private companion object {
        const val IDLE_INTERVAL_MS = 3 * 60 * 1000L
        const val CROSS_MESSAGE_MAX_FOLLOW_UPS = 2
        const val PRIVATE_GROUP_CONTEXT_LIMIT = 24
        const val PRIVATE_ECHO_DEDUP_MESSAGE_LIMIT = 6
        const val PRIVATE_ECHO_DEDUP_WINDOW_MS = 5 * 60 * 1_000L
        const val TAG = "TongpinGroupTurn"
    }
}
