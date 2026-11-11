package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRequestPolicyTest {
    @Test
    fun `conversation payload leaves sampling to the service but carries the app thinking switch`() {
        val profile = ModelProfileEntity(
            id = "conversation",
            kind = "conversation",
            baseUrl = "https://gateway.example/v1",
            modelName = "qwen35-nothink:latest",
            enabled = true,
            timeoutSeconds = 60,
            encryptedApiKey = "key",
            lastTestResult = null,
            temperature = 0.12,
            thinkingEnabled = true,
            reasoningEffort = "high",
            topP = 0.11,
            frequencyPenalty = 0.99,
            presencePenalty = 0.88
        )

        val payload = Json.parseToJsonElement(
            ConversationRequestPolicy.payload(
                profile = profile,
                messages = listOf(
                    ConversationRequestPolicy.Message("system", "You are a character."),
                    ConversationRequestPolicy.Message("user", "Hello")
                ),
                stream = true
            )
        ).jsonObject

        assertEquals("qwen35-nothink:latest", payload["model"]?.jsonPrimitive?.content)
        assertTrue(payload["stream"]?.jsonPrimitive?.content?.toBoolean() == true)
        assertTrue(payload.containsKey("messages"))
        // 采样参数仍由模型服务自己决定。
        listOf("temperature", "top_p", "frequency_penalty", "presence_penalty").forEach { field ->
            assertFalse("unexpected field: $field", payload.containsKey(field))
        }
        // 思考开关是 App 的：网关地址（中转站/自建）拿到 effort + enable_thinking + 模板参数。
        assertEquals("high", payload["reasoning_effort"]?.jsonPrimitive?.content)
        assertTrue(payload["enable_thinking"]?.jsonPrimitive?.content?.toBoolean() == true)
        assertEquals(
            true,
            payload["chat_template_kwargs"]?.jsonObject?.get("thinking")?.jsonPrimitive?.content?.toBoolean()
        )
    }

    @Test
    fun `short term history is represented as ordered messages outside system`() {
        val profile = ModelProfileEntity(
            id = "conversation",
            kind = "conversation",
            baseUrl = "https://gateway.example/v1",
            modelName = "qwen35-nothink:latest",
            enabled = true,
            timeoutSeconds = 60,
            encryptedApiKey = "key",
            lastTestResult = null
        )

        val payload = Json.parseToJsonElement(
            ConversationRequestPolicy.payload(
                profile = profile,
                systemPrompt = "角色设定\n长期记忆：老朋友",
                userPrompt = "继续刚才的话题",
                history = listOf(
                    ConversationRequestPolicy.Message("user", "昨天去了花园"),
                    ConversationRequestPolicy.Message("assistant", "我记得那场散步")
                ),
                stream = true
            )
        ).jsonObject

        val messages = payload["messages"]!!.jsonArray
        assertEquals("角色设定\n长期记忆：老朋友", messages[0].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("昨天去了花园", messages[1].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("assistant", messages[2].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("我记得那场散步", messages[2].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("user", messages[3].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("继续刚才的话题", messages[3].jsonObject["content"]?.jsonPrimitive?.content)
        assertFalse(messages[0].jsonObject["content"]?.jsonPrimitive?.content.orEmpty().contains("昨天去了花园"))
    }
}
