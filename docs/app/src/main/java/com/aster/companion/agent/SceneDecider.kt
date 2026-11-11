package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.lite.LiteProductConfig
import javax.inject.Inject

/**
 * 场景**决策**：拍不拍、拍什么，以及读最近对话。`recentBrief` 一并搬进来
 * （它内部用 dao，留在协调器会形成传递依赖）。
 */
@javax.inject.Singleton
class SceneDecider @Inject constructor(
    private val dao: com.aster.companion.data.CompanionDao,
    private val transport: SceneTransport,
    private val cipher: com.aster.companion.data.CompanionSecretCipher,
    private val imageRuleResolver: ImageGenerationRuleResolver,
) {
    private val situationReader = SceneSituationReader(dao = dao)
    private val inputsReader = SceneInputsReader(dao = dao, imageRuleResolver = imageRuleResolver)
    private val WHITESPACE = Regex("\\s+")

    suspend fun decideShot(
        threadId: String,
        characterId: String,
        herOwnWords: String = "",
        useBackgroundGate: Boolean = false,
        inputs: ScenePromptCoordinator.ScenePromptInputs?
    ): CharacterPhotoShotPolicy.PhotoShot? {
        // ⚠️ 真机上 `resolved` 曾**以 null 到达下一行**（崩溃栈点名 `ScenePromptCoordinator.ScenePromptInputs.character`
        // 在 null 上被读）。源码的 `?:` 链在语义上兜住了，但这条链是 suspend + 返回 `Result`
        // （value class）的签名，实测在装箱/拆箱边界上会出现"类型上非空、运行时是 null"。
        // 这里再夹一道**运行时**检查：`as?` 遇到真 null 就返回 null —— 这次拍摄直接放弃，
        // 而不是 NPE 闪退。
        val resolved = (inputs ?: inputsReader.inputsFor(characterId)) as? ScenePromptCoordinator.ScenePromptInputs ?: return null
        val character = resolved.character
        val profile = resolved.conversationProfile ?: return null
        if (!profile.enabled || profile.encryptedApiKey.isNullOrBlank()) return null
        val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
        val input = CharacterPhotoShotPolicy.Input(
            characterName = character.name,
            personalityPrompt = character.personalityPrompt,
            relationshipPrompt = character.relationshipPrompt,
            currentSituation = situationReader.situationOf(characterId),
            timeOfDay = timeOfDay(),
            recent = recentBrief(threadId),
            herOwnWords = herOwnWords
        )
        val raw = transport.chatOnce(
            profile = profile,
            apiKey = apiKey,
            system = CharacterPhotoShotPolicy.system(input),
            user = CharacterPhotoShotPolicy.user(),
            useBackgroundGate = useBackgroundGate
        ) ?: return null
        return CharacterPhotoShotPolicy.parse(raw)
    }

    suspend fun recentBrief(threadId: String): List<String> = dao.recentMessages(threadId, LiteProductConfig.SCENE_RECENT_MESSAGES)
        .asReversed()
        .mapNotNull { message ->
            val body = message.body.trim().replace(WHITESPACE, " ")
            if (body.isEmpty() || message.id.startsWith("stream:")) return@mapNotNull null
            if (message.fromUser) {
                "User: ${body.take(LiteProductConfig.SCENE_BRIEF_CHARS)}"
            } else {
                val speaker = dao.character(message.characterId.orEmpty())?.name?.trim().orEmpty().ifBlank { "Character" }
                "$speaker: ${body.take(LiteProductConfig.SCENE_BRIEF_CHARS)}"
            }
        }
}
