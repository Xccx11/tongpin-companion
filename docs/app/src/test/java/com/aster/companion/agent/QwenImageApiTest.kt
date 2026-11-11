package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenImageApiTest {

    private val qwenProfile = ModelProfileEntity(
        id = "qwen-image",
        kind = "image_generation",
        baseUrl = "https://ws-example.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
        modelName = "qwen-image",
        enabled = true,
        timeoutSeconds = 120,
        encryptedApiKey = null,
        lastTestResult = null
    )

    @Test
    fun `Qwen native endpoint not auto-selected, but URL still resolves`() {
        assertFalse(QwenImageApi.supports(qwenProfile))
        assertEquals(
            "https://ws-example.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            QwenImageApi.generationEndpoint(qwenProfile)
        )
    }

    @Test
    fun `does not route ordinary OpenAI image providers through Qwen`() {
        val openAiProfile = qwenProfile.copy(
            baseUrl = "https://api.openai.com/v1",
            modelName = "gpt-image-1"
        )

        assertFalse(QwenImageApi.supports(openAiProfile))
    }

    @Test
    fun `builds Qwen image request with text and data image reference`() {
        val payload = QwenImageApi.requestBody(
            model = "qwen-image-edit-plus",
            prompt = "test prompt",
            referenceDataUrl = "data:image/png;base64,aGVsbG8="
        )

        assertTrue(payload.contains("\"model\":\"qwen-image-edit-plus\""))
        assertTrue(payload.contains("\"text\":\"test prompt\""))
        assertTrue(payload.contains("\"image\":\"data:image/png;base64,aGVsbG8=\""))
        assertTrue(payload.contains("\"size\":\"1024*1024\""))
    }

    @Test
    fun `extracts completed Qwen task image url`() {
        val response = """
            {"output":{"task_status":"SUCCEEDED","results":[{"url":"https://cdn.example/image.png"}]}}
        """.trimIndent()

        assertEquals("https://cdn.example/image.png", QwenImageApi.completedImageUrl(response))
    }
}
