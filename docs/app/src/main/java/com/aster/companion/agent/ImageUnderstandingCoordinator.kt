package com.aster.companion.agent

import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import kotlinx.coroutines.delay
import java.io.IOException
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageUnderstandingCoordinator @Inject constructor(
    private val dao: CompanionDao,
    private val cipher: CompanionSecretCipher,
    private val client: OpenAiCompatibleClient
) {
    suspend fun describe(imageUrl: String, instruction: String): Result<String> = runCatching {
        require(
            imageUrl.startsWith("https://") ||
                imageUrl.startsWith("content://") ||
                imageUrl.startsWith("file://") ||
                imageUrl.startsWith("data:image/")
        ) { "Choose an HTTPS URL or local image URI" }
        val profile = requireNotNull(dao.modelProfile(IMAGE_UNDERSTANDING_MODEL)) { "Image understanding model is not configured" }
        require(profile.enabled && !profile.encryptedApiKey.isNullOrBlank()) { "Image understanding model is disabled" }
        val apiKey = cipher.decrypt(requireNotNull(profile.encryptedApiKey))
        val prompt = instruction.ifBlank { "Describe this image accurately for a companion conversation." }
        var firstFailure: Throwable? = null
        repeat(2) { attempt ->
            try {
                return@runCatching client.analyzeImage(profile, apiKey, imageUrl, prompt).getOrThrow()
            } catch (error: Throwable) {
                if (attempt == 1 || !error.isRecoverableConnectionFailure()) throw error
                firstFailure = error
                delay(700)
            }
        }
        throw requireNotNull(firstFailure)
    }

    private fun Throwable.isRecoverableConnectionFailure(): Boolean {
        val message = generateSequence(this) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        return this is IOException || Regex("broken pipe|connection abort|connection reset|timeout", RegexOption.IGNORE_CASE).containsMatchIn(message)
    }

    companion object { const val IMAGE_UNDERSTANDING_MODEL = "image_understanding" }
}
