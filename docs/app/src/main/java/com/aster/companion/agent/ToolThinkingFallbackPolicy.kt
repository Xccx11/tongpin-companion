package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 「带 tools 的请求 + 思考」在个别服务端会被直接拒（400）——DeepSeek 官方就要求把历史里
 * assistant 的 `reasoning_content` 原样回传，而这个 App 的请求体只有 `{role, content}`。
 *
 * 这类失败**不能让功能整体挂掉**：一旦某个 服务地址+模型 组合在这种请求上报过错，就把它记下来，
 * 之后所有带 tools 的请求都自动去掉思考（普通聊天不受影响，仍然按用户开关走）。
 *
 * 非流式那条路还会**当场重试一次**（去掉思考），用户看不到那个 400。
 */
internal object ToolThinkingFallbackPolicy {

    /** 记下「这家服务 + 这个模型」在思考模式下不支持工具调用。 */
    private val thinkingToolUnsupported: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun key(baseUrl: String, modelName: String): String =
        "${baseUrl.trimEnd('/')}/${modelName.trim()}".lowercase()

    /**
     * 报错文本是否指向"思考 + 工具"这个组合。
     *
     * 只在明摆着相关时才认（`reasoning_content` / `thinking` 这类字样），不去猜 400 的通用原因——
     * 猜错会把真正的参数错误也当成"关掉思考就好"，反而掩盖问题。
     */
    fun shouldRetryWithoutThinking(message: String?): Boolean {
        val text = message?.lowercase().orEmpty()
        if (text.isBlank()) return false
        return THINKING_TOOL_MARKERS.any(text::contains)
    }

    /** 带 tools 的请求实际该用的 profile：这家服务已经证明不支持"思考+工具"时，关掉思考。 */
    fun profileForToolRequest(profile: ModelProfileEntity): ModelProfileEntity =
        if (profile.thinkingEnabled && isUnsupported(key(profile.baseUrl, profile.modelName))) {
            profile.copy(thinkingEnabled = false)
        } else {
            profile
        }

    fun markUnsupported(profile: ModelProfileEntity) {
        thinkingToolUnsupported.add(key(profile.baseUrl, profile.modelName))
    }

    fun isUnsupported(key: String): Boolean = key in thinkingToolUnsupported

    /** 仅供测试。 */
    fun reset() {
        thinkingToolUnsupported.clear()
    }

    private val THINKING_TOOL_MARKERS = listOf(
        "reasoning_content",
        "reasoning content",
        "reasoning_effort",
        "thinking is not supported",
        "thinking mode"
    )
}
