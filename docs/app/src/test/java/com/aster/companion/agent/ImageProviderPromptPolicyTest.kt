package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProviderPromptPolicyTest {
    @Test
    fun `standard gateway receives the app composed prompt`() {
        val profile = profile(
            model = "wai-nsfw-illustrious",
            protocol = ImageProviderProtocol.OPENAI_IMAGES
        )
        val authored = "one woman in a red coat, walking through rain, cinematic night light"

        // App 侧已经拼好十段结构：优先发它，只有它为空时才回退角色手写的那句。
        assertEquals(
            "APP WRAPPED PROMPT",
            ImageProviderPromptPolicy.forProvider(profile, authored, "APP WRAPPED PROMPT")
        )
        assertEquals(
            authored,
            ImageProviderPromptPolicy.forProvider(profile, authored, "")
        )
    }

    @Test
    fun `legacy direct ComfyUI keeps its workflow prompt wrapper`() {
        val profile = profile(
            model = ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME,
            protocol = ImageProviderProtocol.COMFY_UI
        )

        assertEquals(
            "APP WRAPPED PROMPT",
            ImageProviderPromptPolicy.forProvider(profile, "character authored prompt", "APP WRAPPED PROMPT")
        )
    }

    private fun profile(model: String, protocol: ImageProviderProtocol) = ModelProfileEntity(
        id = "image_generation",
        kind = "image_generation",
        baseUrl = "https://gateway.example/v1",
        modelName = model,
        enabled = true,
        timeoutSeconds = 120,
        encryptedApiKey = "key",
        lastTestResult = null,
        imageProtocol = protocol.name
    )
}
