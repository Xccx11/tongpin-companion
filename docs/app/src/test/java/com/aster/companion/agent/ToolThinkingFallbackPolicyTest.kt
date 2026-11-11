package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归背景：第 24 轮起，"带 tools 的请求"在自建/中转链路上不再强制关思考。
 * 万一某家服务端仍然拒（官方那条 `reasoning_content` 回传要求），不能整轮对话挂掉：
 * 非流式当场去掉思考重试，流式记住这家服务、下次直接不带思考。
 */
class ToolThinkingFallbackPolicyTest {

    private fun profile(baseUrl: String, model: String, thinking: Boolean = true) = ModelProfileEntity(
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

    @Test
    fun `只认指向思考与工具组合的报错`() {
        assertTrue(
            ToolThinkingFallbackPolicy.shouldRetryWithoutThinking(
                "HTTP 400: {\"error\":{\"message\":\"reasoning_content is required for assistant messages with tool_calls\"}}"
            )
        )
        assertTrue(ToolThinkingFallbackPolicy.shouldRetryWithoutThinking("thinking mode does not support tools"))
        // 普通参数错误不该被当成"关掉思考就好"，否则会掩盖真正的问题。
        assertFalse(ToolThinkingFallbackPolicy.shouldRetryWithoutThinking("HTTP 400: invalid model name"))
        assertFalse(ToolThinkingFallbackPolicy.shouldRetryWithoutThinking("HTTP 401: unauthorized"))
        assertFalse(ToolThinkingFallbackPolicy.shouldRetryWithoutThinking(null))
        assertFalse(ToolThinkingFallbackPolicy.shouldRetryWithoutThinking(""))
    }

    @Test
    fun `记下之后同一家服务不再带思考，其它服务不受影响`() {
        ToolThinkingFallbackPolicy.reset()
        val rejected = profile("http://127.0.0.1:1234/v1", "deepseek-v4")
        val other = profile("http://127.0.0.1:1234/v1", "gemma-3-12b")
        try {
            assertTrue(ToolThinkingFallbackPolicy.profileForToolRequest(rejected).thinkingEnabled)

            ToolThinkingFallbackPolicy.markUnsupported(rejected)

            assertFalse(
                "同一个 地址+模型 再发带 tools 的请求要自动关思考",
                ToolThinkingFallbackPolicy.profileForToolRequest(rejected).thinkingEnabled
            )
            assertTrue(
                "别的模型不受影响，思考照常开",
                ToolThinkingFallbackPolicy.profileForToolRequest(other).thinkingEnabled
            )
        } finally {
            ToolThinkingFallbackPolicy.reset()
        }
    }

    @Test
    fun `关着思考的 profile 一切照旧`() {
        ToolThinkingFallbackPolicy.reset()
        val thinkingOff = profile("https://api.deepseek.com/v1", "deepseek-v4", thinking = false)
        ToolThinkingFallbackPolicy.markUnsupported(thinkingOff)
        assertEquals(thinkingOff, ToolThinkingFallbackPolicy.profileForToolRequest(thinkingOff))
        ToolThinkingFallbackPolicy.reset()
    }
}
