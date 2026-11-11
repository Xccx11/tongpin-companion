package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.MemoryEntity
import com.aster.companion.data.SemanticMemoryEntity
import com.aster.companion.data.WorldStateEntity
import com.aster.companion.domain.ConversationContextVersion
import com.aster.companion.domain.ConversationType
import com.aster.companion.domain.DiaryEntryPolicy
import com.aster.companion.domain.MemoryKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Writes one grounded, character-voiced diary page when the user says good night. */
@Singleton
class DiaryGenerationCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val cipher: CompanionSecretCipher,
    private val client: OpenAiCompatibleClient,
    private val continuityContext: CompanionContinuityContextAssembler
) {
    suspend fun writeAtBedtime(threadId: String): Result<Unit> = runCatching {
        val thread = requireNotNull(dao.thread(threadId)) { "Conversation unavailable" }
        require(thread.type == ConversationType.PRIVATE) { "Only private conversations have a relationship diary" }
        val characterId = requireNotNull(dao.participantIds(threadId).firstOrNull()) { "Character unavailable" }
        val character = requireNotNull(dao.character(characterId)) { "Character unavailable" }
        val profile = requireNotNull(dao.modelProfile(AgentTurnCoordinator.CONVERSATION_MODEL)) { "Conversation model is not configured" }
        require(profile.enabled && !profile.encryptedApiKey.isNullOrBlank()) { "Conversation model is disabled" }
        val contextVersion = thread.contextVersion
        val messages = dao.recentMessages(threadId, MESSAGE_LIMIT).asReversed()
        val events = dao.recentEvents(System.currentTimeMillis() - LIFE_CONTEXT_WINDOW_MS, EVENT_LIMIT)
        val continuity = continuityContext.forPrivateCharacter(characterId, threadId)
        require(messages.isNotEmpty() || events.isNotEmpty()) { "Nothing to write about yet" }
        val systemPrompt = buildString {
            appendLine("你是${character.name}。现在是一天结束后，你在写一页只给用户看的私人日记，不是在聊天。")
            appendLine(character.personalityPrompt)
            appendLine("角色的私人世界只能写作角色自身的连续想法，不能把用户代入其中；共同经历只能来自当天真实聊天或明确建立的计划。")
            appendLine("只根据给出的对话和生活事件，写一段 120 到 220 字的第一人称日记。要像回望今天的完整心情，而不是逐句复述聊天。")
            appendLine("像真人睡前随手写的那样：从今天具体的一件小事落笔（她看到/做到/想起的细节，比如那盆花终于开了、路上看到的小猫、你说过的一句话），带出她真实的情绪和心思；语气口语自然，像心里话，不像作文。允许轻轻的自嘲、小别扭、没来由的开心或低落，不必每一句都美好。")
            appendLine("禁止写对话、引号、括号动作、舞台说明、省略号、角色扮演台词或即时回复。不要凭空编造见面、地点、身体动作、礼物、行程或用户未说过的感受；不要写标题、日期、Markdown 或解释。")
            appendLine("这是给用户翻阅的内容，不能提及模型、提示词、系统、事件来源或工具。")
        }
        val source = buildString {
            appendLine("今天的对话：")
            messages.forEach { message -> appendLine(if (message.fromUser) "用户：${message.body}" else "${character.name}：${message.body}") }
            if (events.isNotEmpty()) {
                appendLine("今天真实发生的生活信息：")
                events.forEach { event -> appendLine(event.summary) }
            }
            if (continuity.groupContext.isNotEmpty()) {
                appendLine("Visible family-group continuity:")
                continuity.groupContext.forEach(::appendLine)
            }
            if (continuity.momentContext.isNotBlank()) {
                appendLine("Visible public-feed continuity:")
                appendLine(continuity.momentContext)
            }
            if (continuity.activityContext.isNotEmpty()) {
                appendLine("Completed in-app activity:")
                continuity.activityContext.forEach(::appendLine)
            }
        }
        val content = client.chat(profile, cipher.decrypt(requireNotNull(profile.encryptedApiKey)), systemPrompt, source)
            .getOrElse { throw it }
            .trim()
            .take(MAX_DIARY_LENGTH)
        require(content.isNotBlank()) { "Diary model returned an empty entry" }
        val currentThread = dao.thread(threadId)
        if (!ConversationContextVersion.isCurrent(contextVersion, currentThread?.contextVersion ?: -1)) return@runCatching
        val now = System.currentTimeMillis()
        val memories = dao.allMemories()
        val existingId = DiaryEntryPolicy.entryIdToRefresh(memories, threadId, now)
        val existing = if (existingId == null) null else dao.memory(existingId)
        dao.upsertMemory(MemoryEntity(existingId ?: UUID.randomUUID().toString(), characterId, threadId, MemoryKind.DIARY, content, false, existing?.createdAt ?: now, now))
        val embeddingProfile = dao.modelProfile(SemanticMemoryRetriever.EMBEDDING_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
        val embedding = embeddingProfile
            ?.let { client.embed(it, cipher.decrypt(requireNotNull(it.encryptedApiKey)), content).getOrNull() }
            ?.let(SemanticMemoryRetriever::encode)
        dao.upsertSemanticMemory(
            SemanticMemoryEntity(
                id = "diary:$threadId:${existingId ?: now}",
                characterId = characterId,
                threadId = threadId,
                timelineEventId = null,
                content = content,
                embedding = embedding,
                importance = 70,
                retrievable = true,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        val reflectionProfile = dao.modelProfile(AgentTurnCoordinator.PLANNING_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() } ?: profile
        val reflection = client.chat(
            reflectionProfile,
            cipher.decrypt(requireNotNull(reflectionProfile.encryptedApiKey)),
            "Return JSON only. private_world is the character's fictional personal continuity and must never claim to sense the user's real life. shared_world contains only facts or plans explicitly established in the supplied conversation. Do not invent meetings, places, gifts, or user actions.",
            "Character: ${character.name}\nDiary: $content"
        ).getOrNull()?.let { raw -> runCatching { WorldReflectionParser.parse(raw) }.getOrNull() }
        reflection?.let { world ->
            if (world.privateWorld.isNotBlank()) dao.upsertWorldState(WorldStateEntity("world:$characterId:private", characterId, null, "PRIVATE", world.privateWorld, now))
            if (world.sharedWorld.isNotBlank()) dao.upsertWorldState(WorldStateEntity("world:$characterId:shared", characterId, threadId, "SHARED", world.sharedWorld, now))
        }
    }

    private companion object {
        const val MESSAGE_LIMIT = 36
        const val EVENT_LIMIT = 12
        const val MAX_DIARY_LENGTH = 500
        const val LIFE_CONTEXT_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
