package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考开关是 **App 自己**发的参数（跟电脑端网关无关），按服务地址自动选格式。
 * 关掉时必须一个参数都不发——那才是"由模型服务自己决定"的原行为。
 */
class ConversationThinkingPolicyTest {

    private fun profile(baseUrl: String, thinking: Boolean, model: String = "neutral-model") = ModelProfileEntity(
        id = "conversation",
        kind = "conversation",
        baseUrl = baseUrl,
        modelName = model,
        enabled = true,
        timeoutSeconds = 60,
        encryptedApiKey = "x",
        lastTestResult = null,
        thinkingEnabled = thinking
    )

    private fun payload(baseUrl: String, thinking: Boolean, model: String = "neutral-model"): String =
        ConversationRequestPolicy.payload(
            profile = profile(baseUrl, thinking, model),
            messages = listOf(ConversationRequestPolicy.Message("user", "hi")),
            stream = false
        )

    @Test
    fun `关掉开关就一个思考参数都不发`() {
        val body = payload("https://relay.example.com/v1", thinking = false)
        assertFalse(body.contains("reasoning_effort"))
        assertFalse(body.contains("enable_thinking"))
        assertFalse(body.contains("chat_template_kwargs"))
    }

    @Test
    fun `OpenAI 官方只发 reasoning_effort`() {
        val body = payload("https://api.openai.com/v1", thinking = true)
        assertTrue(body.contains("\"reasoning_effort\":\"medium\""))
        // OpenAI 官方对未列出的参数硬报错，所以多一个都不能发。
        assertFalse(body.contains("enable_thinking"))
        assertFalse(body.contains("chat_template_kwargs"))
    }

    @Test
    fun `中转站与自建服务发 enable_thinking 加模板参数`() {
        val body = payload("https://my-relay.example.com/v1", thinking = true)
        assertTrue(body.contains("\"enable_thinking\":true"))
        assertTrue(body.contains("\"reasoning_effort\":\"medium\""))
        assertTrue(body.contains("chat_template_kwargs"))
        assertTrue(body.contains("\"thinking\":true"))
    }

    @Test
    fun `DashScope 只发顶层的 enable_thinking`() {
        val body = payload("https://dashscope.aliyuncs.com/compatible-mode/v1", thinking = true)
        assertTrue(body.contains("\"enable_thinking\":true"))
        assertFalse(body.contains("chat_template_kwargs"))
    }

    @Test
    fun `DeepSeek V4 默认就思考，关闭时必须显式禁用`() {
        // 不传参数 = 思考开着（effort 默认 high），所以"关"必须发 thinking.type=disabled。
        val off = payload("https://my-relay.example.com/v1", thinking = false, model = "deepseek-v4")
        assertTrue(off.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertFalse("不能给 DeepSeek 塞 Qwen 那套" , off.contains("chat_template_kwargs"))
        assertFalse(off.contains("enable_thinking"))

        val on = payload("https://my-relay.example.com/v1", thinking = true, model = "deepseek-v4")
        assertTrue(on.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(on.contains("\"reasoning_effort\":\"medium\""))
        assertFalse(on.contains("chat_template_kwargs"))
    }

    @Test
    fun `自建与中转链路上带 tools 也照样按用户设置思考`() {
        // App 的请求体只有 {role, content}，从不回放 assistant 的 tool_calls 消息，
        // 所以"带 tools 必须回传 reasoning_content"这条要求在自建/中转链路上是空条件。
        val payload = Json.parseToJsonElement(
            ConversationRequestPolicy.payload(
                profile = profile("http://127.0.0.1:1234/v1", thinking = true, model = "deepseek-v4"),
                messages = listOf(ConversationRequestPolicy.Message("user", "hi")),
                stream = false,
                withTools = true
            )
        ).jsonObject

        assertEquals("enabled", payload["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("medium", payload["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `官方 DeepSeek 带 tools 仍然禁用思考`() {
        // 官方域名保留保守行为：思考模式的工具调用有文档级限制，宁可这一轮不思考。
        val payload = Json.parseToJsonElement(
            ConversationRequestPolicy.payload(
                profile = profile("https://api.deepseek.com/v1", thinking = true, model = "deepseek-v4"),
                messages = listOf(ConversationRequestPolicy.Message("user", "hi")),
                stream = false,
                withTools = true
            )
        ).jsonObject

        assertEquals(
            "disabled",
            payload["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content
        )
        assertFalse("禁用思考时不该再带档位", payload.containsKey("reasoning_effort"))
    }

    @Test
    fun `官方 DeepSeek 不带 tools 时思考照常开`() {
        val payload = Json.parseToJsonElement(
            ConversationRequestPolicy.payload(
                profile = profile("https://api.deepseek.com/v1", thinking = true, model = "deepseek-v4"),
                messages = listOf(ConversationRequestPolicy.Message("user", "hi")),
                stream = false,
                withTools = false
            )
        ).jsonObject

        assertEquals("enabled", payload["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `grok 关不掉思考，关闭时压到最低档 low`() {
        // 不传 reasoning_effort 的话 grok-4.6 用默认 high（一直在猛思考），所以"关"必须显式发低档。
        val off = payload("https://my-relay.example.com/v1", thinking = false, model = "grok-4.6")
        assertTrue(off.contains("\"reasoning_effort\":\"low\""))
        // 不能给 xAI 塞 Qwen 那套参数。
        assertFalse(off.contains("enable_thinking"))
        assertFalse(off.contains("chat_template_kwargs"))

        val on = payload("https://my-relay.example.com/v1", thinking = true, model = "grok-4.6")
        assertTrue(on.contains("\"reasoning_effort\":\"medium\""))
        assertFalse(on.contains("chat_template_kwargs"))
    }
}
