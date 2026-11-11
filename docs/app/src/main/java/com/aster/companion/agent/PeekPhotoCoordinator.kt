package com.aster.companion.agent

import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.MessageEntity
import com.aster.companion.agent.OpenAiCompatibleClient
import com.aster.companion.proactive.CompanionNotificationPublisher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 偷看照片消息的标记（对话里显示为偷看图标发出的照片）。 */
const val PEEK_MARKER = "👁"

/**
 * 偷看：用户主动查看角色此刻的样子，生成一张照片放进对话，角色完全不知情。
 * 复用现有生图链路（角色英文提示词 + ImageGenerationCoordinator），
 * 偷看不会触发角色可见回复，但提示词仍由角色模型根据完整上下文生成。
 */
@Singleton
class PeekPhotoCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val client: OpenAiCompatibleClient,
    private val secretCipher: com.aster.companion.data.CompanionSecretCipher,
    private val imageGeneration: ImageGenerationCoordinator,
    private val diaryPhotoStore: com.aster.companion.media.DiaryPhotoStore,
    private val notificationPublisher: CompanionNotificationPublisher
) {
    /** 私聊：偷看该角色；群聊：偷看所有成员合照。照片直接进对话 + 通知；任务进"设置→生成任务"可查。 */
    suspend fun peek(threadId: String, targetCharacterId: String? = null): Result<Unit> = runCatching {
        val failureNotify: suspend (String) -> Unit = { reason ->
            val name = dao.participantIds(threadId).firstOrNull()?.let { dao.character(it)?.name } ?: "她"
            notificationPublisher.publish(name, "偷看失败：$reason", threadId)
        }
        val participants = dao.participantIds(threadId)
        require(participants.isNotEmpty()) { "Thread has no participants" }
        // 指定了目标角色（即使群聊）→ 只偷看她一个
        val singleId = if (targetCharacterId != null && participants.contains(targetCharacterId)) targetCharacterId else if (participants.size == 1) participants.first() else null
        val now = System.currentTimeMillis()
        val taskId = java.util.UUID.randomUUID().toString()
        // 先组装真实画面描述（任务详情里要能看到"她此刻在做什么"），再建任务。
        val sceneForTask: String
        val uri = try {
            if (singleId != null) {
                val characterId = singleId
                val character = requireNotNull(dao.character(characterId)) { "Character unavailable" }
                // 偷看 scene：标准 wai 网关使用英文提示词；旧 z-image 兼容路径保留自己的格式。
                val generatedScene = peekSceneDescription(characterId, character)
                    ?: error("角色没有生成合格的英文 ComfyUI 提示词")
                sceneForTask = generatedScene
                val visualBrief = generatedScene
                dao.upsertPendingAction(
                    com.aster.companion.data.PendingActionEntity(
                        id = taskId,
                        eventId = null,
                        threadId = threadId,
                        capabilityId = "companion.peek_image",
                        transport = "LOCAL",
                        argumentsJson = encodeArguments(mapOf("peek" to "true", "scene" to sceneForTask, "character_id" to characterId)),
                        state = "RUNNING",
                        createdAt = now,
                        resolvedAt = null,
                        resultSummary = null
                    )
                )
                imageGeneration.generateStandaloneImage(characterId, visualBrief, taskId).getOrThrow()
            } else {
                val characters = participants.mapNotNull { id -> dao.character(id) }
                require(characters.size == participants.size) { "Group peek character unavailable" }
                val scene = peekGroupSceneDescription(threadId, characters)
                    ?: error("角色没有生成合格的英文合照提示词")
                sceneForTask = scene
                dao.upsertPendingAction(
                    com.aster.companion.data.PendingActionEntity(
                        id = taskId,
                        eventId = null,
                        threadId = threadId,
                        capabilityId = "companion.peek_image",
                        transport = "LOCAL",
                        argumentsJson = encodeArguments(mapOf("peek" to "true", "scene" to sceneForTask, "character_id" to participants.first())),
                        state = "RUNNING",
                        createdAt = now,
                        resolvedAt = null,
                        resultSummary = null
                    )
                )
                val result = imageGeneration.generateForGroup(
                    threadId = threadId,
                    senderCharacterId = participants.first(),
                    participantCharacterIds = participants,
                    request = scene,
                    actionId = taskId
                )
                when (result) {
                    is com.aster.companion.agent.ImageGenerationResult.Generated -> result.imageUrl
                    is com.aster.companion.agent.ImageGenerationResult.ModelNotConfigured ->
                        throw IllegalStateException("Image generation model is not configured")
                    is com.aster.companion.agent.ImageGenerationResult.Failed ->
                        throw IllegalStateException(result.reason)
                }
            }
        } catch (error: Throwable) {
            dao.resolvePendingAction(taskId, "FAILED", System.currentTimeMillis(), (error.message?.take(200)).orEmpty())
            failureNotify(error.message?.takeIf { it.length < 60 } ?: "图片生成失败，请稍后再试")
            throw error
        }
        dao.resolvePendingAction(taskId, "COMPLETED", System.currentTimeMillis(), uri)
        val senderId = singleId ?: participants.first()
        insertPeekMessage(threadId, senderId, uri, now)
        // 用户要求：照片只走聊天里那条消息，不再额外发系统通知（生图链路早就去掉了，偷看这条一直漏着）。
        if (singleId != null) {
            diaryPhotoStore.archive(singleId, threadId, uri, now)
            // 让聊天模型知道「我刚给他看过什么」：偷看走的是独立出图链路，原来不写 LAST_PHOTO，
            // 导致她之后提起照片时对不上（连拍一致性也接不上）。
            runCatching {
                dao.upsertWorldState(
                    com.aster.companion.data.WorldStateEntity(
                        id = "last-photo:$singleId",
                        characterId = singleId,
                        threadId = threadId,
                        scope = "LAST_PHOTO",
                        content = sceneForTask.take(200),
                        updatedAt = now
                    )
                )
            }
        } else {
            participants.forEach { cid -> diaryPhotoStore.archive(cid, threadId, uri, now) }
        }
    }

    /** 画面描述：由角色模型基于完整可见上下文生成最终英文 ComfyUI 提示词。 */
    private suspend fun peekSceneDescription(characterId: String, character: CharacterEntity): String? {
        val state = dao.livingState(characterId)
        val profile = ImagePromptModel.resolve(dao)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return null
        val privateThread = dao.latestPrivateThread(characterId)
        val memorySince = privateThread?.let(ConversationMemoryVisibilityPolicy::boundaryFor) ?: 0L
        val visibleHistory = privateThread?.let {
            GatewayConversationHistoryPolicy.visibleMessages(dao.allMessagesForThread(it.id), memorySince)
        }.orEmpty()
        val context = buildString {
            appendLine("她在${state?.currentActivity ?: "家里安静待着"}（心情：${state?.mood ?: "平静"}）")
            if (state?.sleepUntil != null) appendLine("她正在睡觉。")
            appendLine("COMPLETE VISIBLE CONVERSATION:")
            if (visibleHistory.isEmpty()) appendLine("(no earlier conversation)")
            visibleHistory.forEach { message ->
                val speaker = if (message.fromUser) "User" else character.name
                appendLine("$speaker: ${message.body}")
            }
            appendLine("AUTHORITATIVE APPEARANCE: ${character.appearancePrompt.take(1_800)}")
        }
        return client.chat(
            profile = profile,
            apiKey = secretCipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            systemPrompt = buildString {
                appendLine(ComfyUiEnglishPromptPolicy.hardRule(character.name))
                appendLine("Return only one final English ComfyUI positive production prompt for this character's current visible moment.")
            },
            userPrompt = context
        ).getOrNull()?.let { ComfyUiEnglishPromptPolicy.finalPromptOrNull(it, 1_000) }
    }

    private suspend fun peekGroupSceneDescription(threadId: String, characters: List<CharacterEntity>): String? {
        val profile = ImagePromptModel.resolve(dao)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return null
        val memorySince = dao.thread(threadId)?.let(ConversationMemoryVisibilityPolicy::boundaryFor) ?: 0L
        val visibleHistory = GatewayConversationHistoryPolicy.visibleMessages(dao.allMessagesForThread(threadId), memorySince)
        val transcript = buildString {
            if (visibleHistory.isEmpty()) appendLine("(no earlier conversation)")
            visibleHistory.forEach { message ->
                val speaker = if (message.fromUser) "User" else characters.firstOrNull { it.id == message.characterId }?.name ?: "Character"
                appendLine("$speaker: ${message.body}")
            }
        }
        val state = buildString {
            characters.forEachIndexed { index, character ->
                if (index > 0) append('\n')
                val living = dao.livingState(character.id)
                append("${character.name}: appearance=${character.appearancePrompt.take(1_200)}; activity=${living?.currentActivity ?: "quietly resting"}; mood=${living?.mood ?: "calm"}")
            }
        }
        val generated = client.chat(
            profile = profile,
            apiKey = secretCipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            systemPrompt = buildString {
                appendLine(ComfyUiEnglishPromptPolicy.hardRule(characters.firstOrNull()?.name ?: "the character"))
                appendLine("Return only one final English ComfyUI positive production prompt for a single shared group photo. Include every listed member, their grounded current states, composition, clothing and setting. Do not describe light sources, lighting, brightness or shadows.")
            },
            userPrompt = "GROUP MEMBERS AND CURRENT STATES:\n$state\nCOMPLETE VISIBLE GROUP CONVERSATION:\n$transcript"
        ).getOrNull()
        return generated?.let { ComfyUiEnglishPromptPolicy.finalPromptOrNull(it, 1_000) }
    }

    private fun encodeArguments(arguments: Map<String, String>): String = buildJsonObject {
        arguments.forEach { (key, value) -> put(key, value) }
    }.toString()

    private suspend fun insertPeekMessage(threadId: String, characterId: String, uri: String, now: Long) {
        dao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                characterId = characterId,
                body = PEEK_MARKER,
                imageUri = uri,
                fromUser = false,
                createdAt = now
            )
        )
        dao.touchThread(threadId, now)
    }
}
