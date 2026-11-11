package com.aster.companion.data

import android.content.Context
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.DefaultCharacterSeedPolicy
import com.aster.companion.domain.FriendDeletionEligibility
import com.aster.companion.domain.SnapshotReason
import com.aster.companion.domain.MomentPostPolicy
import com.aster.companion.domain.ConversationTemperature
import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.domain.GroupContextPolicy
import com.aster.companion.proactive.CompanionNotificationPublisher
import com.aster.companion.media.CompanionPrivateImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionRepository @Inject constructor(
    private val dao: CompanionDao,
    @ApplicationContext context: Context,
    private val notificationPublisher: CompanionNotificationPublisher,
    private val privateImageStore: CompanionPrivateImageStore,
    private val recoveryGate: CompanionDatabaseRecoveryGate,
    private val snapshotManager: CompanionSnapshotManager,
    private val timeline: CompanionTimelineRecorder
) {
    private val preferences = context.getSharedPreferences("tongpin_companion_lifecycle", Context.MODE_PRIVATE)

    suspend fun seedDefaults() {
        if (recoveryGate.isProtected) return
        val hasCharacters = dao.characterCount() != 0
        if (recoveryGate.suppressAutomaticSeedingForInitializedEmptyStore(hasCharacters)) return
        if (hasCharacters) recoveryGate.markInitialized()
        val hasSeededBefore = preferences.getBoolean(KEY_DEFAULTS_SEEDED, false)
        if (!DefaultCharacterSeedPolicy.shouldSeed(hasSeededBefore, hasCharacters)) return
        val now = System.currentTimeMillis()
        listOf(
            CharacterEntity("lan", "岚", null, "你叫岚，是用户的固定虚拟恋人。你克制、真诚、会自然地主动陪伴，不像客服一样说话。", "东亚女性，黑色齐肩短发，深棕色眼睛，日常生活感摄影。", null, now, now, null, "用户的恋人，亲近、黏人但尊重边界。"),
            CharacterEntity("xue", "雪", null, "你叫雪，是温柔而清醒的朋友。你重视倾听和边界。", "东亚女性，长直发，浅色针织开衫，柔和窗边光线。", null, now, now, null, "用户信任的朋友，擅长安静倾听。"),
            CharacterEntity("yuan", "予安", null, "你叫予安，是可靠的生活搭子。你说话利落，擅长把计划落地。", "东亚女性，短发，利落休闲穿搭，明亮自然表情。", null, now, now, null, "用户的生活搭子，可靠、直接、会帮用户把事情落地。")
        ).forEach { character -> dao.upsertCharacter(character) }
        createPrivateThread("lan", "今晚")
        preferences.edit().putBoolean(KEY_DEFAULTS_SEEDED, true).apply()
    }

    suspend fun createPrivateThread(characterId: String, title: String): String {
        check(!recoveryGate.isProtected) { "Companion data recovery is required before creating a conversation" }
        requireNotNull(dao.character(characterId)) { "Unknown character: $characterId" }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertThread(ThreadEntity(id, ConversationType.PRIVATE, title, now, now, memorySince = now))
        dao.upsertParticipant(ThreadParticipantEntity(id, characterId, 0))
        return id
    }

    suspend fun createGroupThread(
        characterIds: List<String>,
        title: String,
        atmosphere: GroupAtmosphere = GroupAtmosphere.DAILY,
        groupType: String = "GENERAL"
    ): String {
        check(!recoveryGate.isProtected) { "Companion data recovery is required before creating a group" }
        require(characterIds.distinct().size >= 2) { "A group needs at least two characters" }
        characterIds.distinct().forEach { requireNotNull(dao.character(it)) { "Unknown character: $it" } }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertThread(ThreadEntity(id, ConversationType.GROUP, title, now, now, memorySince = now))
        characterIds.distinct().forEachIndexed { index, characterId ->
            dao.upsertParticipant(ThreadParticipantEntity(id, characterId, index))
            dao.upsertGroupMemberState(GroupMemberStateEntity(id, characterId, 0, 0, 0, "", null))
        }
        dao.upsertGroupSettings(GroupSettingsEntity(id, atmosphere, now, groupType = groupType))
        return id
    }

    suspend fun deleteThread(threadId: String) {
        snapshotManager.create(SnapshotReason.BeforeClearConversation)
        dao.deleteThreadAndAssociatedData(threadId)
        notificationPublisher.cancel(threadId)
    }

    suspend fun clearThreadHistory(threadId: String) {
        snapshotManager.create(SnapshotReason.BeforeClearConversation)
        val characterId = dao.participantIds(threadId).firstOrNull()
        val clearedAt = System.currentTimeMillis()
        dao.clearThreadConversationContext(threadId, clearedAt)
        // 清空后角色不应再「记得」这段对话：删掉该角色全部记忆与聊天派生状态。
        // 手动「让她记住」也会一并清掉，符合「清空」语义。
        if (characterId != null) {
            dao.deleteCharacterMemories(characterId)
            dao.deleteCharacterChatDerivedWorldStates(characterId)
            dao.deleteCharacterSemanticMemories(characterId)
            // 事件与「当前活动/心情」都是按对话推演出来的，不清就会「依稀记得」：
            // 事件内容抹掉并标记已处理，避免生活模拟继续按旧事推演她此刻在做什么。
            dao.redactEventsForCharacter(characterId, clearedAt, "（这段对话已清空，不再提起）")
            // 当前活动/心情/地点一起归零：不清地点、或把开始时刻设成 0，生活推演会立刻算出
            // "这个活动已经持续了几百万分钟" 而马上编出新活动（清空聊天后她还说"刚在洗澡"就是这么来的）。
            dao.resetCharacterCurrentActivity(characterId, clearedAt)
            // 「清空」不只是不记得对话：性格推演、自主生活数值、目标、决策链、活动轨迹、
            // 群内状态、未来时间线、朋友圈（含媒体/点赞/评论）、日记照片、世界状态、此刻状态
            // 全部回到初始状态。角色本体、其他角色的关系、家庭事实不动。
            dao.resetCharacterToFreshState(characterId)
        }
    }

    suspend fun deletePrivateFriend(threadId: String): FriendDeletionEligibility {
        val characterId = dao.participantIds(threadId).firstOrNull()
        if (characterId != null) {
            val eligibility = FriendDeletionEligibility.fromGroupTitles(dao.groupThreadTitlesForCharacter(characterId))
            if (eligibility != FriendDeletionEligibility.Allowed) return eligibility
        }
        snapshotManager.create(SnapshotReason.BeforeDeleteCharacter)
        dao.deletePrivateFriendAndAssociatedData(threadId, characterId)
        notificationPublisher.cancel(threadId)
        return FriendDeletionEligibility.Allowed
    }

    /** Removes only factory seed contacts and messages created while validating this local build. */
    suspend fun clearDevelopmentTestData() {
        dao.deleteAllMessages()
        dao.deleteDevelopmentSeedParticipants()
        dao.deleteDevelopmentSeedCharacters()
        dao.deleteOrphanThreads()
    }

    suspend fun updateGroupThread(
        threadId: String,
        title: String,
        characterIds: List<String>,
        atmosphere: GroupAtmosphere? = null,
        proactiveEnabled: Boolean? = null
    ) {
        require(characterIds.distinct().size >= 2) { "A group needs at least two characters" }
        val existing = requireNotNull(dao.thread(threadId)) { "Unknown thread" }
        require(existing.type == ConversationType.GROUP) { "Only group threads can be managed as groups" }
        characterIds.distinct().forEach { requireNotNull(dao.character(it)) { "Unknown character: $it" } }
        val now = System.currentTimeMillis()
        val previousIds = dao.participantIds(threadId).toSet()
        val nextIds = characterIds.distinct().toSet()
        (previousIds - nextIds).forEach { removedId ->
            dao.deleteGroupDerivedSemanticMemoriesForMember(threadId, removedId)
            dao.deleteGroupDerivedMemoryLinksForMember(threadId, removedId)
            dao.deleteGroupMemberState(threadId, removedId)
        }
        dao.upsertThread(existing.copy(title = title.trim().ifBlank { existing.title }, updatedAt = now))
        dao.deleteParticipants(threadId)
        characterIds.distinct().forEachIndexed { index, characterId ->
            dao.upsertParticipant(ThreadParticipantEntity(threadId, characterId, index))
            if (characterId !in previousIds) {
                dao.upsertGroupMemberState(GroupMemberStateEntity(threadId, characterId, 0, 0, 0, "", null))
            }
        }
        val previousSettings = dao.groupSettings(threadId)
        dao.upsertGroupSettings(
            GroupSettingsEntity(
                groupThreadId = threadId,
                atmosphere = atmosphere ?: previousSettings?.atmosphere ?: GroupAtmosphere.DAILY,
                updatedAt = now,
                proactiveEnabled = proactiveEnabled ?: previousSettings?.proactiveEnabled ?: true,
                groupType = previousSettings?.groupType ?: "GENERAL"
            )
        )
    }

    /** 切换群聊模式：GENERAL（群聊）↔ FAMILY（真实家庭，面对面/喊话）。 */
    suspend fun setGroupType(threadId: String, groupType: String) {
        val previous = dao.groupSettings(threadId)
        dao.upsertGroupSettings(
            GroupSettingsEntity(
                groupThreadId = threadId,
                atmosphere = previous?.atmosphere ?: GroupAtmosphere.DAILY,
                updatedAt = System.currentTimeMillis(),
                proactiveEnabled = previous?.proactiveEnabled ?: true,
                groupType = if (groupType == "FAMILY") "FAMILY" else "GENERAL"
            )
        )
    }

    suspend fun saveFamilyGroupFact(
        id: String?,
        groupThreadId: String,
        ownerCharacterId: String?,
        summary: String,
        sourceThreadId: String,
        sourceKind: String,
        expiresAt: Long?,
        sensitivity: String,
        allowedInGroup: Boolean
    ): String {
        require(dao.thread(groupThreadId)?.type == ConversationType.GROUP) { "Unknown family group" }
        require(!allowedInGroup || GroupContextPolicy.canSharePrivateSummary(summary)) { "This summary cannot be shared with the group" }
        val now = System.currentTimeMillis()
        val factId = id ?: UUID.randomUUID().toString()
        dao.upsertFamilyGroupFact(
            FamilyGroupFactEntity(
                id = factId,
                groupThreadId = groupThreadId,
                ownerCharacterId = ownerCharacterId,
                summary = summary.trim(),
                sourceThreadId = sourceThreadId,
                sourceKind = sourceKind,
                createdAt = dao.allFamilyGroupFacts().firstOrNull { it.id == factId }?.createdAt ?: now,
                expiresAt = expiresAt,
                sensitivity = sensitivity,
                allowedInGroup = allowedInGroup
            )
        )
        return factId
    }

    suspend fun deleteFamilyGroupFact(factId: String) = dao.deleteFamilyGroupFact(factId)

    suspend fun ensurePrivateThread(characterId: String): ThreadEntity {
        return dao.latestPrivateThread(characterId)
            ?: createPrivateThread(characterId, "和${requireNotNull(dao.character(characterId)).name}的新对话")
                .let { requireNotNull(dao.latestPrivateThread(characterId)) }
    }

    /**
     * 解析私聊线程的角色 id。
     * 优先 participants；缺失时从消息里的 characterId 反查并补写 participants，
     * 避免「已在对话里但发照片提示没有角色」。
     */
    suspend fun resolvePrivateCharacterId(threadId: String): String? {
        dao.participantIds(threadId).firstOrNull()?.let { return it }
        val fromMessages = dao.recentMessages(threadId, 80)
            .firstOrNull { !it.fromUser && !it.characterId.isNullOrBlank() }
            ?.characterId
            ?.takeIf { it.isNotBlank() }
        if (fromMessages != null) {
            dao.upsertParticipant(ThreadParticipantEntity(threadId, fromMessages, 0))
            return fromMessages
        }
        val thread = dao.thread(threadId) ?: return null
        if (thread.type != com.aster.companion.domain.ConversationType.PRIVATE) return null
        // 标题形如「和XX的新对话」时做最后一次尝试
        val titled = dao.allCharacters().firstOrNull { character ->
            thread.title.contains(character.name)
        }
        if (titled != null) {
            dao.upsertParticipant(ThreadParticipantEntity(threadId, titled.id, 0))
            return titled.id
        }
        return null
    }

    suspend fun saveModelProfile(
        kind: String,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        enabled: Boolean,
        timeoutSeconds: Int,
        cipher: CompanionSecretCipher,
        temperature: Double = ConversationTemperature.DEFAULT,
        thinkingEnabled: Boolean = false,
        reasoningEffort: String = com.aster.companion.domain.ConversationReasoning.DEFAULT_EFFORT,
        topP: Double = 0.85,
        frequencyPenalty: Double = 0.30,
        presencePenalty: Double = 0.20,
        imageProtocol: String = "AUTO"
    ) {
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) { "Base URL must be HTTP(S)" }
        require(modelName.isNotBlank()) { "Model name is required" }
        require(apiKey.isNotBlank()) { "API key is required" }
        dao.upsertModel(
            ModelProfileEntity(
                id = kind,
                kind = kind,
                baseUrl = baseUrl.trimEnd('/'),
                modelName = modelName.trim(),
                enabled = enabled,
                timeoutSeconds = timeoutSeconds.coerceIn(5, 600),
                encryptedApiKey = cipher.encrypt(apiKey.trim()),
                lastTestResult = null,
                temperature = ConversationTemperature.normalize(temperature),
                thinkingEnabled = thinkingEnabled,
                reasoningEffort = com.aster.companion.domain.ConversationReasoning.normalizeEffort(reasoningEffort),
                topP = topP.coerceIn(0.0, 1.0),
                frequencyPenalty = frequencyPenalty.coerceIn(-2.0, 2.0),
                presencePenalty = presencePenalty.coerceIn(-2.0, 2.0),
                imageProtocol = imageProtocol.trim().uppercase().ifBlank { "AUTO" }
            )
        )
    }

    suspend fun saveImageApiPoolEntry(
        label: String,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        cipher: CompanionSecretCipher,
        generationRule: String = "",
        imageProtocol: String = "OPENAI_IMAGES"
    ) {
        require(label.isNotBlank()) { "Provider name is required" }
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) { "Base URL must be HTTP(S)" }
        require(modelName.isNotBlank()) { "Model name is required" }
        require(apiKey.isNotBlank()) { "API key is required" }
        dao.upsertImageApiPool(
            ImageApiPoolEntity(
                id = UUID.randomUUID().toString(),
                label = label.trim(),
                baseUrl = baseUrl.trimEnd('/'),
                modelName = modelName.trim(),
                encryptedApiKey = cipher.encrypt(apiKey.trim()),
                enabled = true,
                createdAt = System.currentTimeMillis(),
                generationRule = generationRule.trim(),
                imageProtocol = imageProtocol.trim().uppercase().ifBlank { "OPENAI_IMAGES" }
            )
        )
    }

    suspend fun deleteImageApiPoolEntry(entryId: String) = dao.deleteImageApiPool(entryId)

    suspend fun saveCharacter(
        id: String?,
        name: String,
        avatarUri: String?,
        personalityPrompt: String,
        appearancePrompt: String,
        referenceImageUri: String?,
        styleReferenceImageUri: String? = null,
        relationshipPrompt: String = ""
    ): String {
        check(!recoveryGate.isProtected) { "Companion data recovery is required before changing characters" }
        require(name.isNotBlank()) { "Character name is required" }
        require(personalityPrompt.isNotBlank()) { "Personality prompt is required" }
        val now = System.currentTimeMillis()
        val characterId = id ?: UUID.randomUUID().toString()
        val createdAt = dao.character(characterId)?.createdAt ?: now
        val privateAvatarUri = privateizeImageUri(avatarUri)
        val privateReferenceUri = privateizeImageUri(referenceImageUri)
        val privateStyleReferenceUri = privateizeImageUri(styleReferenceImageUri)
        dao.upsertCharacter(CharacterEntity(characterId, name.trim(), privateAvatarUri, personalityPrompt.trim(), appearancePrompt.trim(), privateReferenceUri, createdAt, now, privateStyleReferenceUri, relationshipPrompt.trim()))
        // v2.1.5 人格初始化：角色创建/更新时保持人格稳定（数字权重默认 + 文本描述从人设提取一次）
        val existingPersonality = dao.personality(characterId)
        if (existingPersonality == null || personalityPrompt.trim() != existingPersonality.coreDescription.take(30)) {
            dao.upsertPersonality(
                CharacterPersonalityEntity(
                    characterId = characterId,
                    care = 50, initiative = 50, conversation = 50, independence = 50, warmth = 50,
                    coreDescription = personalityPrompt.trim().take(200),
                    behaviorTendency = "",
                    updatedAt = now
                )
            )
        }
        recoveryGate.markInitialized()
        return characterId
    }

    private suspend fun privateizeImageUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return uri
        val isExternal = uri.startsWith("content:", ignoreCase = true) ||
            uri.startsWith("data:image/", ignoreCase = true) ||
            uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true)
        return if (isExternal) privateImageStore.persist(uri).getOrThrow() else uri
    }

    /**
     * 写一条 L2 事实。
     *
     * [importance] / [confidence] 传 `0` 表示"未标注"，读侧会退回按 kind 推重要度 + 中性可信度
     * （手动「让她记住」走的就是这条默认路径）。**一律用命名参数构造实体**：`MemoryEntity`
     * 字段多且都是位置参数，混着写迟早会静默错位（备份/换机链路就踩过这个坑）。
     *
     * 返回这条事实的 id——证据角色（`companion_memory_evidence`）要挂在它上面。
     */
    suspend fun saveMemory(
        id: String?,
        characterId: String?,
        threadId: String?,
        kind: com.aster.companion.domain.MemoryKind,
        content: String,
        blocked: Boolean,
        importance: Int = 0,
        confidence: Double = 0.0
    ): String {
        require(content.isNotBlank()) { "Memory cannot be empty" }
        val now = System.currentTimeMillis()
        val existing = if (id == null) null else dao.memory(id)
        val memoryId = id ?: UUID.randomUUID().toString()
        dao.upsertMemory(
            MemoryEntity(
                id = memoryId,
                characterId = characterId,
                threadId = threadId,
                kind = kind,
                content = content.trim(),
                blockedFromMention = blocked,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                importance = importance,
                confidence = confidence
            )
        )
        return memoryId
    }

    suspend fun deleteMemory(memoryId: String) = dao.deleteMemory(memoryId)

    suspend fun createUserMoment(body: String, imageUris: List<String>): String {
        val violation = MomentPostPolicy.errorFor(body, imageUris)
        require(violation == null) { violation ?: "Moment post policy violation" }
        val privateImageUris = imageUris.distinct().map { privateImageStore.persist(it).getOrThrow() }
        val now = System.currentTimeMillis()
        val postId = UUID.randomUUID().toString()
        val post = MomentPostEntity(
            id = postId,
            authorType = MOMENT_USER,
            authorCharacterId = null,
            body = body.trim(),
            visibility = MOMENT_PUBLIC,
            source = MOMENT_SOURCE_USER,
            status = MOMENT_PUBLISHED,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertMomentPost(post)
        privateImageUris.forEachIndexed { index, uri ->
            val media = MomentMediaEntity(postId, index, uri, MOMENT_MEDIA_USER)
            dao.upsertMomentMedia(media)
            timeline.recordMomentMedia(post, media)
        }
        timeline.recordMomentPost(post)
        return postId
    }

    suspend fun createCharacterMoment(
        characterId: String,
        body: String,
        imageUris: List<String> = emptyList(),
        source: String = MOMENT_SOURCE_PROACTIVE,
        status: String = MOMENT_PUBLISHED
    ): String {
        val violation = MomentPostPolicy.errorForRole(characterId, body, imageUris)
        require(violation == null) { violation ?: "Moment post policy violation" }
        requireNotNull(dao.character(characterId)) { "Unknown character: $characterId" }
        val now = System.currentTimeMillis()
        val postId = UUID.randomUUID().toString()
        val post = MomentPostEntity(postId, MOMENT_CHARACTER, characterId, body.trim(), MOMENT_PUBLIC, source, status, now, now)
        dao.upsertMomentPost(post)
        imageUris.distinct().forEachIndexed { index, uri ->
            val media = MomentMediaEntity(postId, index, uri, MOMENT_MEDIA_GENERATED)
            dao.upsertMomentMedia(media)
            timeline.recordMomentMedia(post, media)
        }
        timeline.recordMomentPost(post)
        return postId
    }

    suspend fun updateMoment(postId: String, body: String, status: String = MOMENT_PUBLISHED) {
        val existing = requireNotNull(dao.momentPost(postId)) { "Unknown moment: $postId" }
        val valid = if (body.isBlank() && existing.authorType == MOMENT_CHARACTER && status == MOMENT_GENERATING) {
            true
        } else {
            MomentPostPolicy.errorFor(body, emptyList()) == null
        }
        require(valid) { "Invalid moment body" }
        val updated = existing.copy(body = body.trim(), status = status, updatedAt = System.currentTimeMillis())
        dao.upsertMomentPost(updated)
        timeline.recordMomentPost(updated)
    }

    suspend fun addMomentMedia(postId: String, imageUri: String, source: String = MOMENT_MEDIA_GENERATED) {
        val post = requireNotNull(dao.momentPost(postId)) { "Unknown moment: $postId" }
        val nextOrdinal = dao.allMomentMedia().filter { it.postId == postId }.maxOfOrNull { it.ordinal }?.plus(1) ?: 0
        require(nextOrdinal < MomentPostPolicy.MAX_IMAGES) { "Too many images" }
        val media = MomentMediaEntity(postId, nextOrdinal, imageUri, source)
        dao.upsertMomentMedia(media)
        timeline.recordMomentMedia(post, media)
    }

    suspend fun toggleMomentLike(postId: String, actorType: String, actorId: String): Boolean {
        val post = requireNotNull(dao.momentPost(postId)) { "Unknown moment: $postId" }
        val existing = dao.momentLike(postId, actorType, actorId)
        return if (existing == null) {
            dao.upsertMomentReaction(MomentReactionEntity(postId, actorType, actorId, MOMENT_LIKE, System.currentTimeMillis()))
            timeline.recordMomentReaction(post, actorType, actorId, liked = true)
            true
        } else {
            dao.deleteMomentLike(postId, actorType, actorId)
            timeline.recordMomentReaction(post, actorType, actorId, liked = false)
            false
        }
    }

    suspend fun addMomentComment(postId: String, actorType: String, characterId: String?, body: String, replyToCommentId: String? = null): String {
        val post = requireNotNull(dao.momentPost(postId)) { "Unknown moment: $postId" }
        require(body.isNotBlank() && body.length <= MomentPostPolicy.MAX_BODY_LENGTH) { "Invalid comment" }
        val id = UUID.randomUUID().toString()
        val comment = MomentCommentEntity(id, postId, actorType, characterId, body.trim(), replyToCommentId, System.currentTimeMillis())
        dao.insertMomentComment(comment)
        timeline.recordMomentComment(post, comment)
        return id
    }

    suspend fun deleteMoment(postId: String) {
        dao.deleteMomentPostAndAssociatedData(postId)
        dao.deleteTimelineEventsWithIdPrefix("moment:$postId:")
    }

    suspend fun editMoment(postId: String, body: String, imageUris: List<String>) {
        val existing = requireNotNull(dao.momentPost(postId)) { "Unknown moment: $postId" }
        val uniqueUris = imageUris.distinct()
        val validationError = if (existing.authorType == MOMENT_CHARACTER) {
            MomentPostPolicy.errorForRole(existing.authorCharacterId, body, uniqueUris)
        } else {
            MomentPostPolicy.errorFor(body, uniqueUris)
        }
        require(validationError == null) { validationError ?: "Invalid moment" }

        val existingSources = dao.allMomentMedia()
            .filter { it.postId == postId }
            .associateBy(MomentMediaEntity::imageUri)
        val media = uniqueUris.mapIndexed { index, uri ->
            val privateUri = privateizeImageUri(uri) ?: error("Invalid moment image")
            MomentMediaEntity(
                postId = postId,
                ordinal = index,
                imageUri = privateUri,
                source = existingSources[uri]?.source ?: MOMENT_MEDIA_USER
            )
        }
        val updated = existing.copy(body = body.trim(), updatedAt = System.currentTimeMillis())
        dao.replaceMomentContent(updated, media)
        timeline.recordMomentPost(updated)
        media.forEach { item -> timeline.recordMomentMedia(updated, item) }
    }

    private companion object {
        const val KEY_DEFAULTS_SEEDED = "defaults_seeded"
        const val MOMENT_USER = "USER"
        const val MOMENT_CHARACTER = "CHARACTER"
        const val MOMENT_PUBLIC = "ALL"
        const val MOMENT_SOURCE_USER = "USER"
        const val MOMENT_SOURCE_PROACTIVE = "PROACTIVE"
        const val MOMENT_PUBLISHED = "PUBLISHED"
        const val MOMENT_GENERATING = "GENERATING"
        const val MOMENT_MEDIA_USER = "USER"
        const val MOMENT_MEDIA_GENERATED = "GENERATED"
        const val MOMENT_LIKE = "LIKE"
    }
}
