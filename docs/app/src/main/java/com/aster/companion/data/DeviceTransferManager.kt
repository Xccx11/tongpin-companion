package com.aster.companion.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.EventState
import com.aster.companion.domain.MemoryKind
import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.domain.SnapshotReason
import com.aster.companion.media.CompanionPrivateImageStore
import com.aster.companion.proactive.SystemModeState
import com.aster.companion.proactive.SystemModeTransferSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceTransferResult(
    val characters: Int,
    val threads: Int,
    val messages: Int,
    val images: Int
)

/**
 * Cross-device transfer is logical, never a copy of the SQLCipher database or a
 * device Keystore record. Credentials are re-encrypted by the destination app.
 */
@Singleton
class DeviceTransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CompanionDatabase,
    private val dao: CompanionDao,
    private val backupCipher: CompanionBackupCipher,
    private val secretCipher: CompanionSecretCipher,
    private val privateImageStore: CompanionPrivateImageStore,
    private val systemModeState: SystemModeState,
    private val snapshotManager: CompanionSnapshotManager
) {
    suspend fun export(output: OutputStream, password: String): DeviceTransferResult = withContext(Dispatchers.IO) {
        val payload = capture()
        val assets = collectAssets(payload)
        val archive = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(json.encodeToString(payload.copy(assets = assets)).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                assets.forEach { asset ->
                    zip.putNextEntry(ZipEntry(asset.entryName))
                    openSource(asset.sourceUri).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
        output.use { it.write(backupCipher.encrypt(archive, password)) }
        DeviceTransferResult(payload.characters.size, payload.threads.size, payload.messages.size, assets.size)
    }

    suspend fun import(input: InputStream, password: String): DeviceTransferResult = withContext(Dispatchers.IO) {
        val entries = unzip(backupCipher.decrypt(input.use { it.readBytes() }, password))
        val payload = json.decodeFromString<TransferPayload>(
            requireNotNull(entries.remove(MANIFEST_ENTRY)) { "Invalid transfer archive" }.toString(Charsets.UTF_8)
        )
        require(payload.version in 1..TRANSFER_VERSION) { "Unsupported transfer archive" }
        require(payload.assets.all { entries.containsKey(it.entryName) }) { "Transfer media is incomplete" }

        // Stage media first; no database content changes until every image is readable.
        val media = payload.assets.associate { asset ->
            val bytes = requireNotNull(entries[asset.entryName])
            asset.sourceUri to privateImageStore.save(ByteArrayInputStream(bytes), asset.contentType).getOrThrow()
        }
        snapshotManager.create(SnapshotReason.BeforeUpgrade)
        database.withTransaction {
            payload.characters.forEach { item ->
                dao.upsertCharacter(CharacterEntity(item.id, item.name, media[item.avatarUri], item.personalityPrompt, item.appearancePrompt, media[item.referenceImageUri], item.createdAt, item.updatedAt, media[item.styleReferenceImageUri], item.relationshipPrompt))
            }
            payload.threads.forEach { item ->
                dao.upsertThread(ThreadEntity(item.id, ConversationType.valueOf(item.type), item.title, item.createdAt, item.updatedAt, item.contextVersion, item.memorySince.takeIf { it > 0 } ?: item.createdAt))
            }
            payload.participants.forEach { dao.upsertParticipant(ThreadParticipantEntity(it.threadId, it.characterId, it.ordinal)) }
            payload.messages.forEach { item ->
                dao.insertMessage(MessageEntity(item.id, item.threadId, item.characterId, item.body, media[item.imageUri], item.fromUser, item.createdAt, item.readAt))
            }
            payload.memories.forEach { item ->
                dao.upsertMemory(
                    MemoryEntity(
                        id = item.id,
                        characterId = item.characterId,
                        threadId = item.threadId,
                        kind = MemoryKind.valueOf(item.kind),
                        content = item.content,
                        blockedFromMention = item.blockedFromMention,
                        createdAt = item.createdAt,
                        updatedAt = item.updatedAt,
                        importance = item.importance,
                        confidence = item.confidence
                    )
                )
            }
            payload.diaryPhotos.forEach { item ->
                dao.insertDiaryPhoto(DiaryPhotoEntity(item.id, item.characterId, item.threadId, requireNotNull(media[item.imageUri]) { "Diary photo is missing" }, item.createdAt))
            }
            payload.events.forEach { item ->
                dao.upsertEvent(EventEntity(item.id, item.source, item.summary, DeviceTransferPolicy.importEventState(EventState.valueOf(item.state)), item.occurredAt, null, System.currentTimeMillis()))
            }
            payload.recipients.forEach { item ->
                dao.insertEventRecipients(listOf(EventRecipientEntity(item.eventId, item.characterId, EventState.CONSUMED, null, System.currentTimeMillis())))
            }
            payload.turns.forEach { item -> dao.insertTurn(AgentTurnEntity(item.id, item.threadId, item.eventId, item.snapshotVersion, item.decisionJson, item.createdAt)) }
            payload.audits.forEach { item -> dao.insertAudit(ActionAuditEntity(item.id, item.source, item.capabilityId, item.transport, item.confirmation, item.resultSummary, item.createdAt)) }
            payload.pendingActions.forEach { item ->
                dao.upsertPendingAction(PendingActionEntity(item.id, item.eventId, item.threadId, item.capabilityId, item.transport, item.argumentsJson, DeviceTransferPolicy.importActionState(item.state), item.createdAt, item.resolvedAt, item.resultSummary))
            }
            payload.modelProfiles.forEach { item ->
                dao.upsertModel(ModelProfileEntity(item.id, item.kind, item.baseUrl, item.modelName, item.enabled, item.timeoutSeconds, item.apiKey?.let(secretCipher::encrypt), item.lastTestResult, item.temperature, item.thinkingEnabled, item.reasoningEffort, item.topP.coerceIn(0.0, 1.0), item.frequencyPenalty.coerceIn(-2.0, 2.0), item.presencePenalty.coerceIn(-2.0, 2.0), item.imageProtocol))
            }
            payload.imageApiPool.forEach { item ->
                dao.upsertImageApiPool(ImageApiPoolEntity(item.id, item.label, item.baseUrl, item.modelName, secretCipher.encrypt(item.apiKey), item.enabled, item.createdAt, imageProtocol = item.imageProtocol))
            }
            payload.timeline.forEach { item -> dao.upsertTimelineEvent(TimelineEventEntity(item.id, item.threadId, item.characterId, item.source, item.sensitiveLevel, item.summary, item.payloadJson, item.occurredAt, item.createdAt)) }
            payload.worldStates.forEach { item -> dao.upsertWorldState(WorldStateEntity(item.id, item.characterId, item.threadId, item.scope, item.content, item.updatedAt)) }
            payload.semanticMemories.forEach { item -> dao.upsertSemanticMemory(SemanticMemoryEntity(item.id, item.characterId, item.threadId, item.timelineEventId, item.content, item.embeddingBase64?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }, item.importance, item.retrievable, item.createdAt, item.updatedAt)) }
            payload.durableTasks.forEach { item -> dao.upsertDurableTask(DurableTaskEntity(item.id, item.type, item.threadId, item.characterId, item.payloadJson, DeviceTransferPolicy.importActionState(item.state), item.attempts, item.nextRunAt, item.startedAt, item.completedAt, item.errorSummary)) }
            payload.agentRuns.forEach { item -> dao.upsertAgentRun(AgentRunEntity(item.id, item.triggerType, item.threadId, item.characterId, item.timelineEventId, item.state, item.planJson, item.resultJson, item.createdAt, item.completedAt)) }
            payload.momentPosts.forEach { item -> dao.upsertMomentPost(MomentPostEntity(item.id, item.authorType, item.authorCharacterId, item.body, item.visibility, item.source, item.status, item.createdAt, item.updatedAt)) }
            payload.momentMedia.forEach { item -> dao.upsertMomentMedia(MomentMediaEntity(item.postId, item.ordinal, requireNotNull(media[item.imageUri]) { "Moment media is missing" }, item.source)) }
            payload.momentReactions.forEach { item -> dao.upsertMomentReaction(MomentReactionEntity(item.postId, item.actorType, item.actorId, item.kind, item.createdAt)) }
            payload.momentComments.forEach { item -> dao.insertMomentComment(MomentCommentEntity(item.id, item.postId, item.authorType, item.authorCharacterId, item.body, item.replyToCommentId, item.createdAt)) }
            payload.familyGroupFacts.forEach { item ->
                dao.upsertFamilyGroupFact(FamilyGroupFactEntity(item.id, item.groupThreadId, item.ownerCharacterId, item.summary, item.sourceThreadId, item.sourceKind, item.createdAt, item.expiresAt, item.sensitivity, item.allowedInGroup))
            }
            payload.groupMemberStates.forEach { item ->
                dao.upsertGroupMemberState(GroupMemberStateEntity(item.groupThreadId, item.characterId, item.attention, item.affection, item.jealousy, item.unspokenIntent, item.lastSpokeAt))
            }
            payload.groupSettings.forEach { item ->
                dao.upsertGroupSettings(
                    GroupSettingsEntity(
                        groupThreadId = item.groupThreadId,
                        atmosphere = GroupAtmosphere.valueOf(item.atmosphere),
                        updatedAt = item.updatedAt,
                        proactiveEnabled = item.proactiveEnabled,
                        groupType = item.groupType
                    )
                )
            }
            payload.groupPrivateFollowUpUses.forEach { item ->
                dao.insertGroupPrivateFollowUpUses(listOf(GroupPrivateFollowUpUseEntity(item.groupThreadId, item.characterId, item.messageId, item.usedAt)))
            }
            payload.groupDerivedMemoryLinks.forEach { item ->
                dao.upsertGroupDerivedMemoryLink(GroupDerivedMemoryLinkEntity(item.factId, item.characterId, item.semanticMemoryId))
            }
            payload.livingStates.forEach { item ->
                dao.upsertLivingState(CharacterLivingStateEntity(item.characterId, item.mood, item.currentActivity, item.activityStartedAt, item.sleepUntil, item.scheduleJson, item.updatedAt, item.locationId, item.evidence))
            }
            payload.lifeStates.forEach { item ->
                dao.upsertCharacterLifeState(CharacterLifeStateEntity(item.characterId, item.energy, item.stress, item.loneliness, item.socialNeed, item.currentGoal, item.hiddenGoal, item.updatedAt))
            }
            payload.personalities.forEach { item ->
                dao.upsertPersonality(CharacterPersonalityEntity(item.characterId, item.care, item.initiative, item.conversation, item.independence, item.warmth, item.coreDescription, item.behaviorTendency, item.updatedAt))
            }
            payload.threadSummaries.forEach { item ->
                dao.upsertThreadSummary(ThreadSummaryEntity(item.threadId, item.role, item.summary, item.summarizedUpToMessageId, item.updatedAt, item.revision, item.failureCount, item.lastError, item.lastAttemptAt))
            }
        }
        systemModeState.restoreTransferSnapshot(payload.systemMode.toRuntime())
        DeviceTransferResult(payload.characters.size, payload.threads.size, payload.messages.size, media.size)
    }

    private suspend fun capture(): TransferPayload = TransferPayload(
        createdAt = System.currentTimeMillis(),
        characters = dao.allCharacters().map { TransferCharacter(it.id, it.name, it.avatarUri, it.personalityPrompt, it.appearancePrompt, it.referenceImageUri, it.createdAt, it.updatedAt, it.styleReferenceImageUri, it.relationshipPrompt) },
        threads = dao.allThreads().map { TransferThread(it.id, it.type.name, it.title, it.createdAt, it.updatedAt, it.contextVersion, it.memorySince) },
        participants = dao.allParticipants().map { TransferParticipant(it.threadId, it.characterId, it.ordinal) },
        messages = dao.allMessages().map { TransferMessage(it.id, it.threadId, it.characterId, it.body, it.imageUri, it.fromUser, it.createdAt, it.readAt) },
        memories = dao.allMemories().map {
            TransferMemory(
                id = it.id,
                characterId = it.characterId,
                threadId = it.threadId,
                kind = it.kind.name,
                content = it.content,
                blockedFromMention = it.blockedFromMention,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                importance = it.importance,
                confidence = it.confidence
            )
        },
        diaryPhotos = dao.allDiaryPhotos().map { TransferDiaryPhoto(it.id, it.characterId, it.threadId, it.imageUri, it.createdAt) },
        events = dao.allEvents().map { TransferEvent(it.id, it.source, it.summary, it.state.name, it.occurredAt) },
        recipients = dao.allEventRecipients().map { TransferRecipient(it.eventId, it.characterId) },
        turns = dao.allAgentTurns().map { TransferTurn(it.id, it.threadId, it.eventId, it.snapshotVersion, it.decisionJson, it.createdAt) },
        audits = dao.allAudits().map { TransferAudit(it.id, it.source, it.capabilityId, it.transport, it.confirmation, it.resultSummary, it.createdAt) },
        pendingActions = dao.allPendingActions().map { TransferPendingAction(it.id, it.eventId, it.threadId, it.capabilityId, it.transport, it.argumentsJson, it.state, it.createdAt, it.resolvedAt, it.resultSummary) },
        modelProfiles = dao.allModelProfiles().map { TransferModelProfile(it.id, it.kind, it.baseUrl, it.modelName, it.enabled, it.timeoutSeconds, it.encryptedApiKey?.let(secretCipher::decrypt), it.lastTestResult, it.temperature, it.thinkingEnabled, it.reasoningEffort, it.topP, it.frequencyPenalty, it.presencePenalty, it.imageProtocol) },
        imageApiPool = dao.allImageApiPool().map { TransferImageApiPool(it.id, it.label, it.baseUrl, it.modelName, secretCipher.decrypt(it.encryptedApiKey), it.enabled, it.createdAt, it.imageProtocol) },
        timeline = dao.allTimelineEvents().map { TransferTimeline(it.id, it.threadId, it.characterId, it.source, it.sensitiveLevel, it.summary, it.payloadJson, it.occurredAt, it.createdAt) },
        worldStates = dao.allWorldStates().map { TransferWorldState(it.id, it.characterId, it.threadId, it.scope, it.content, it.updatedAt) },
        semanticMemories = dao.allSemanticMemories().map { TransferSemanticMemory(it.id, it.characterId, it.threadId, it.timelineEventId, it.content, it.embedding?.let { bytes -> android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) }, it.importance, it.retrievable, it.createdAt, it.updatedAt) },
        durableTasks = dao.allDurableTasks().map { TransferDurableTask(it.id, it.type, it.threadId, it.characterId, it.payloadJson, it.state, it.attempts, it.nextRunAt, it.startedAt, it.completedAt, it.errorSummary) },
        agentRuns = dao.allAgentRuns().map { TransferAgentRun(it.id, it.triggerType, it.threadId, it.characterId, it.timelineEventId, it.state, it.planJson, it.resultJson, it.createdAt, it.completedAt) },
        momentPosts = dao.allMomentPosts().map { TransferMomentPost(it.id, it.authorType, it.authorCharacterId, it.body, it.visibility, it.source, it.status, it.createdAt, it.updatedAt) },
        momentMedia = dao.allMomentMedia().map { TransferMomentMedia(it.postId, it.ordinal, it.imageUri, it.source) },
        momentReactions = dao.allMomentReactions().map { TransferMomentReaction(it.postId, it.actorType, it.actorId, it.kind, it.createdAt) },
        momentComments = dao.allMomentComments().map { TransferMomentComment(it.id, it.postId, it.authorType, it.authorCharacterId, it.body, it.replyToCommentId, it.createdAt) },
        familyGroupFacts = dao.allFamilyGroupFacts().map { TransferFamilyGroupFact(it.id, it.groupThreadId, it.ownerCharacterId, it.summary, it.sourceThreadId, it.sourceKind, it.createdAt, it.expiresAt, it.sensitivity, it.allowedInGroup) },
        groupMemberStates = dao.allGroupMemberStates().map { TransferGroupMemberState(it.groupThreadId, it.characterId, it.attention, it.affection, it.jealousy, it.unspokenIntent, it.lastSpokeAt) },
        groupSettings = dao.allGroupSettings().map { TransferGroupSettings(it.groupThreadId, it.atmosphere.name, it.updatedAt, it.groupType, it.proactiveEnabled) },
        groupPrivateFollowUpUses = dao.allGroupPrivateFollowUpUses().map { TransferGroupPrivateFollowUpUse(it.groupThreadId, it.characterId, it.messageId, it.usedAt) },
        groupDerivedMemoryLinks = dao.allGroupDerivedMemoryLinks().map { TransferGroupDerivedMemoryLink(it.factId, it.characterId, it.semanticMemoryId) },
        livingStates = dao.allLivingStates().map { TransferLivingState(it.characterId, it.mood, it.currentActivity, it.activityStartedAt, it.sleepUntil, it.scheduleJson, it.updatedAt, it.locationId, it.evidence) },
        lifeStates = dao.allLifeStates().map { TransferLifeState(it.characterId, it.energy, it.stress, it.loneliness, it.socialNeed, it.currentGoal, it.hiddenGoal, it.updatedAt) },
        personalities = dao.allPersonalities().map { TransferPersonality(it.characterId, it.care, it.initiative, it.conversation, it.independence, it.warmth, it.coreDescription, it.behaviorTendency, it.updatedAt) },
        threadSummaries = dao.allThreadSummaries().map { TransferThreadSummary(it.threadId, it.role, it.summary, it.summarizedUpToMessageId, it.updatedAt, it.revision, it.failureCount, it.lastError, it.lastAttemptAt) },
        systemMode = TransferSystemMode.from(systemModeState.transferSnapshot())
    )

    private fun collectAssets(payload: TransferPayload): List<TransferAsset> = payload.imageUris()
        .distinct().mapIndexed { index, uri -> TransferAsset(uri, "media/$index", contentTypeFor(uri)) }

    private fun openSource(rawUri: String): InputStream {
        val uri = Uri.parse(rawUri)
        return when (uri.scheme) {
            "content" -> requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open media" }
            "file" -> requireNotNull(uri.path).let(::File).inputStream()
            else -> throw IllegalArgumentException("Unsupported media URI")
        }
    }

    private fun contentTypeFor(rawUri: String): String = context.contentResolver.getType(Uri.parse(rawUri))
        ?: when {
            rawUri.endsWith(".png", true) -> "image/png"
            rawUri.endsWith(".webp", true) -> "image/webp"
            else -> "image/jpeg"
        }

    private fun unzip(bytes: ByteArray): MutableMap<String, ByteArray> = mutableMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name !in entries) { "Invalid transfer archive" }
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
    }

    private companion object {
        const val TRANSFER_VERSION = 2
        const val MANIFEST_ENTRY = "transfer.json"
        val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    }
}

@Serializable private data class TransferPayload(
    val version: Int = 2, val createdAt: Long, val characters: List<TransferCharacter>, val threads: List<TransferThread>, val participants: List<TransferParticipant>, val messages: List<TransferMessage>, val memories: List<TransferMemory>, val diaryPhotos: List<TransferDiaryPhoto>, val events: List<TransferEvent>, val recipients: List<TransferRecipient>, val turns: List<TransferTurn>, val audits: List<TransferAudit>, val pendingActions: List<TransferPendingAction>, val modelProfiles: List<TransferModelProfile>, val imageApiPool: List<TransferImageApiPool>, val timeline: List<TransferTimeline>, val worldStates: List<TransferWorldState>, val semanticMemories: List<TransferSemanticMemory>, val durableTasks: List<TransferDurableTask>, val agentRuns: List<TransferAgentRun>, val momentPosts: List<TransferMomentPost> = emptyList(), val momentMedia: List<TransferMomentMedia> = emptyList(), val momentReactions: List<TransferMomentReaction> = emptyList(), val momentComments: List<TransferMomentComment> = emptyList(), val familyGroupFacts: List<TransferFamilyGroupFact> = emptyList(), val groupMemberStates: List<TransferGroupMemberState> = emptyList(), val groupSettings: List<TransferGroupSettings> = emptyList(), val groupPrivateFollowUpUses: List<TransferGroupPrivateFollowUpUse> = emptyList(), val groupDerivedMemoryLinks: List<TransferGroupDerivedMemoryLink> = emptyList(), val livingStates: List<TransferLivingState> = emptyList(), val lifeStates: List<TransferLifeState> = emptyList(), val personalities: List<TransferPersonality> = emptyList(), val threadSummaries: List<TransferThreadSummary> = emptyList(), val systemMode: TransferSystemMode, val assets: List<TransferAsset> = emptyList()
) {
    fun imageUris(): List<String> = buildList {
        characters.forEach { listOfNotNull(it.avatarUri, it.referenceImageUri, it.styleReferenceImageUri).forEach(::add) }
        messages.mapNotNullTo(this, TransferMessage::imageUri)
        diaryPhotos.mapTo(this, TransferDiaryPhoto::imageUri)
        momentMedia.mapTo(this, TransferMomentMedia::imageUri)
    }
}

@Serializable private data class TransferAsset(val sourceUri: String, val entryName: String, val contentType: String)
@Serializable private data class TransferCharacter(val id: String, val name: String, val avatarUri: String?, val personalityPrompt: String, val appearancePrompt: String, val referenceImageUri: String?, val createdAt: Long, val updatedAt: Long, val styleReferenceImageUri: String? = null, val relationshipPrompt: String = "")
@Serializable private data class TransferThread(val id: String, val type: String, val title: String, val createdAt: Long, val updatedAt: Long, val contextVersion: Long, val memorySince: Long = 0)
@Serializable private data class TransferParticipant(val threadId: String, val characterId: String, val ordinal: Int)
@Serializable private data class TransferMessage(val id: String, val threadId: String, val characterId: String?, val body: String, val imageUri: String?, val fromUser: Boolean, val createdAt: Long, val readAt: Long?)
@Serializable private data class TransferMemory(val id: String, val characterId: String?, val threadId: String?, val kind: String, val content: String, val blockedFromMention: Boolean, val createdAt: Long, val updatedAt: Long, val importance: Int = 0, val confidence: Double = 0.0)
@Serializable private data class TransferDiaryPhoto(val id: String, val characterId: String, val threadId: String, val imageUri: String, val createdAt: Long)
@Serializable private data class TransferEvent(val id: String, val source: String, val summary: String, val state: String, val occurredAt: Long)
@Serializable private data class TransferRecipient(val eventId: String, val characterId: String)
@Serializable private data class TransferTurn(val id: String, val threadId: String?, val eventId: String?, val snapshotVersion: Long, val decisionJson: String, val createdAt: Long)
@Serializable private data class TransferAudit(val id: String, val source: String, val capabilityId: String, val transport: String, val confirmation: String, val resultSummary: String, val createdAt: Long)
@Serializable private data class TransferPendingAction(val id: String, val eventId: String?, val threadId: String?, val capabilityId: String, val transport: String, val argumentsJson: String, val state: String, val createdAt: Long, val resolvedAt: Long?, val resultSummary: String?)
@Serializable private data class TransferModelProfile(val id: String, val kind: String, val baseUrl: String, val modelName: String, val enabled: Boolean, val timeoutSeconds: Int, val apiKey: String?, val lastTestResult: String?, val temperature: Double = com.aster.companion.domain.ConversationTemperature.DEFAULT, val thinkingEnabled: Boolean = false, val reasoningEffort: String = com.aster.companion.domain.ConversationReasoning.DEFAULT_EFFORT, val topP: Double = 0.85, val frequencyPenalty: Double = 0.30, val presencePenalty: Double = 0.20, val imageProtocol: String = "AUTO")
@Serializable private data class TransferImageApiPool(val id: String, val label: String, val baseUrl: String, val modelName: String, val apiKey: String, val enabled: Boolean, val createdAt: Long, val imageProtocol: String = "OPENAI_IMAGES")
@Serializable private data class TransferTimeline(val id: String, val threadId: String?, val characterId: String?, val source: String, val sensitiveLevel: String, val summary: String, val payloadJson: String?, val occurredAt: Long, val createdAt: Long)
@Serializable private data class TransferWorldState(val id: String, val characterId: String?, val threadId: String?, val scope: String, val content: String, val updatedAt: Long)
@Serializable private data class TransferSemanticMemory(val id: String, val characterId: String?, val threadId: String?, val timelineEventId: String?, val content: String, val embeddingBase64: String?, val importance: Int, val retrievable: Boolean, val createdAt: Long, val updatedAt: Long)
@Serializable private data class TransferDurableTask(val id: String, val type: String, val threadId: String?, val characterId: String?, val payloadJson: String, val state: String, val attempts: Int, val nextRunAt: Long, val startedAt: Long?, val completedAt: Long?, val errorSummary: String?)
@Serializable private data class TransferAgentRun(val id: String, val triggerType: String, val threadId: String?, val characterId: String?, val timelineEventId: String?, val state: String, val planJson: String?, val resultJson: String?, val createdAt: Long, val completedAt: Long?)
@Serializable private data class TransferMomentPost(val id: String, val authorType: String, val authorCharacterId: String?, val body: String, val visibility: String, val source: String, val status: String, val createdAt: Long, val updatedAt: Long)
@Serializable private data class TransferMomentMedia(val postId: String, val ordinal: Int, val imageUri: String, val source: String)
@Serializable private data class TransferMomentReaction(val postId: String, val actorType: String, val actorId: String, val kind: String, val createdAt: Long)
@Serializable private data class TransferMomentComment(val id: String, val postId: String, val authorType: String, val authorCharacterId: String?, val body: String, val replyToCommentId: String?, val createdAt: Long)
@Serializable private data class TransferFamilyGroupFact(val id: String, val groupThreadId: String, val ownerCharacterId: String?, val summary: String, val sourceThreadId: String, val sourceKind: String, val createdAt: Long, val expiresAt: Long?, val sensitivity: String, val allowedInGroup: Boolean)
@Serializable private data class TransferGroupMemberState(val groupThreadId: String, val characterId: String, val attention: Int, val affection: Int, val jealousy: Int, val unspokenIntent: String, val lastSpokeAt: Long?)
@Serializable private data class TransferGroupSettings(
    val groupThreadId: String,
    val atmosphere: String,
    val updatedAt: Long,
    /**
     * 群组类型（`"FAMILY"` / `"GENERAL"`）与主动开关。
     *
     * 以前这两个字段没有参与迁移：导入端只构造 3 个参数，`groupType` 回落到默认 `"GENERAL"`、
     * `proactiveEnabled` 回落 `true`。数据看着还在，但家庭群的成员互知生活状态规则**静默失效**。
     */
    val groupType: String = "GENERAL",
    val proactiveEnabled: Boolean = true
)
@Serializable private data class TransferGroupPrivateFollowUpUse(val groupThreadId: String, val characterId: String, val messageId: String, val usedAt: Long)
@Serializable private data class TransferGroupDerivedMemoryLink(val factId: String, val characterId: String, val semanticMemoryId: String)

/**
 * 「她此刻在做什么 / 心情 / 作息 / 睡到几点 / 为什么是这个状态」。
 *
 * 之前这个表没有参与换机迁移，导入后新机只能靠 [com.aster.companion.agent.life.LifeTicker] 重新推演，
 * 结果就是换机后她的当前活动、心情、睡眠时间全部重置 —— 对话里正在聊的"你现在在干嘛"会前后对不上。
 */
@Serializable private data class TransferLivingState(val characterId: String, val mood: String, val currentActivity: String, val activityStartedAt: Long, val sleepUntil: Long?, val scheduleJson: String, val updatedAt: Long, val locationId: String = "home.unknown", val evidence: String = "")

/** 自主生活数值（能量/压力/孤独/社交需求/当前目标/隐藏目标）。缺了会让她的主动行为立刻变成"满电"。 */
@Serializable private data class TransferLifeState(val characterId: String, val energy: Int = 80, val stress: Int = 40, val loneliness: Int = 30, val socialNeed: Int = 40, val currentGoal: String = "", val hiddenGoal: String = "", val updatedAt: Long = 0L)

/** 人格权重与核心描述。缺了 `CharacterCognitionEngine` 会按"全 50 路人"来推演她的反应。 */
@Serializable private data class TransferPersonality(val characterId: String, val care: Int = 50, val initiative: Int = 50, val conversation: Int = 50, val independence: Int = 50, val warmth: Int = 50, val coreDescription: String = "", val behaviorTendency: String = "", val updatedAt: Long = 0L)

/** 会话滚动总结：窗口外的历史被压缩成的摘要，缺了会让长对话"更早之前聊过"的记忆消失。 */
@Serializable private data class TransferThreadSummary(val threadId: String, val role: String, val summary: String, val summarizedUpToMessageId: String?, val updatedAt: Long, val revision: Int = 0, val failureCount: Int = 0, val lastError: String? = null, val lastAttemptAt: Long = 0L)
@Serializable private data class TransferSystemMode(val enabled: Boolean, val proactiveCharacterIds: List<String>, val selectionConfigured: Boolean, val pausedCharacterIds: List<String>) {
    fun toRuntime() = SystemModeTransferSnapshot(enabled, proactiveCharacterIds.toSet(), selectionConfigured, pausedCharacterIds.toSet())
    companion object { fun from(value: SystemModeTransferSnapshot) = TransferSystemMode(value.enabled, value.proactiveCharacterIds.sorted(), value.selectionConfigured, value.pausedCharacterIds.sorted()) }
}
