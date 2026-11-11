package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.domain.ImageRequestPolicy
import javax.inject.Inject
import javax.inject.Singleton

/** Lets the planning model recognize natural requests to see the character, without vision analysis. */
@Singleton
class DeepSeekPhotoIntentClassifier @Inject constructor(
    private val dao: CompanionDao,
    private val cipher: CompanionSecretCipher,
    private val client: OpenAiCompatibleClient
) {
    suspend fun wantsCharacterPhoto(message: String, recentConversation: List<String>): Boolean? = runCatching {
        if (ImageRequestPolicy.isPhotoReferenceOnly(message)) return@runCatching false
        val profile = dao.modelProfile(AgentTurnCoordinator.PLANNING_MODEL)
            ?.takeIf { it.enabled && !it.encryptedApiKey.isNullOrBlank() }
            ?: return null
        val response = client.chat(
            profile,
            cipher.decrypt(requireNotNull(profile.encryptedApiKey)),
            SYSTEM_PROMPT,
            buildString {
                if (recentConversation.isNotEmpty()) {
                    appendLine("Recent conversation:")
                    recentConversation.takeLast(MAX_CONTEXT_LINES).forEach { appendLine(it.take(MAX_CONTEXT_CHARS)) }
                }
                appendLine("New user message:")
                append(message.take(MAX_MESSAGE_CHARS))
            }
        ).getOrThrow()
        PhotoIntentDecisionParser.wantsCharacterPhoto(response)
    }.getOrNull()

    private companion object {
        const val MAX_CONTEXT_LINES = 8
        const val MAX_CONTEXT_CHARS = 220
        const val MAX_MESSAGE_CHARS = 1_000
        const val SYSTEM_PROMPT = """
            Decide whether the user is expressing that they want the current character to send or generate an image/photo of herself now.
            A photo/image word by itself is not a request. A sentence saying that the user saw, liked, sent, discussed, or described a photo is not a request either.
            Recognize natural indirect Chinese phrasing such as wanting to see what she looks like, what she is wearing, or her current appearance.
            Do not infer an image request from ordinary conversation, compliments, a reference to an existing image, or a request for a text description.
            Return JSON only: {"wants_character_photo":true} or {"wants_character_photo":false}.
        """
    }
}

object PhotoIntentDecisionParser {
    private val TRUE_DECISION = Regex("\"wants_character_photo\"\\s*:\\s*true", RegexOption.IGNORE_CASE)

    fun wantsCharacterPhoto(response: String): Boolean = TRUE_DECISION.containsMatchIn(response)
}
