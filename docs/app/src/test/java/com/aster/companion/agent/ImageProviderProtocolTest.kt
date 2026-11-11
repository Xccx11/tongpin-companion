package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProviderProtocolTest {
    private fun profile(model: String, protocol: String = ImageProviderProtocol.AUTO.name) =
        ModelProfileEntity(
            id = "image",
            kind = "image_generation",
            baseUrl = "https://gateway.example/v1",
            modelName = model,
            enabled = true,
            timeoutSeconds = 120,
            encryptedApiKey = "key",
            lastTestResult = null,
            imageProtocol = protocol
        )

    @Test
    fun `documented wai model defaults to standard openai images`() {
        assertEquals(
            ImageProviderProtocol.OPENAI_IMAGES,
            ImageProviderProtocolResolver.resolve(profile("wai-nsfw-illustrious"))
        )
    }

    @Test
    fun `all profiles resolve to OpenAI Images`() {
        assertEquals(
            ImageProviderProtocol.OPENAI_IMAGES,
            ImageProviderProtocolResolver.resolve(profile("wai-nsfw-illustrious", ImageProviderProtocol.COMFY_GATEWAY_TASK.name))
        )
        assertEquals(
            ImageProviderProtocol.OPENAI_IMAGES,
            ImageProviderProtocolResolver.resolve(profile("wai-nsfw-illustrious"))
        )
    }
}
