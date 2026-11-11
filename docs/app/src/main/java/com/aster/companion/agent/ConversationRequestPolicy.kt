package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds the OpenAI-compatible conversation envelope without overriding gateway-owned parameters. */
object ConversationRequestPolicy {
    data class Message(val role: String, val content: String)

    fun payload(
        profile: ModelProfileEntity,
        messages: List<Message>,
        stream: Boolean,
        withTools: Boolean = false,
        temperature: Double? = null,
        maxTokens: Int? = null
    ): String =
        buildJsonObject {
            putBase(this, profile, messages, stream, withTools = withTools)
            temperature?.let { put("temperature", it) }
            maxTokens?.let { put("max_tokens", it) }
        }.toString()

    fun payload(
        profile: ModelProfileEntity,
        systemPrompt: String,
        userPrompt: String,
        history: List<Message> = emptyList(),
        stream: Boolean,
        /**
         * 采样覆盖：**只有状态记录器那一路会填**。
         *
         * 普通对话仍不发采样参数（`ModelSettingsPresentationPolicy` 写明"由模型服务自己决定"）；
         * 状态记录器要的是"照抄事实"，必须把温度压下去，否则 RP 模型会把它写成小说。
         */
        temperature: Double? = null,
        maxTokens: Int? = null
    ): String = payload(
        profile = profile,
        messages = conversationMessages(systemPrompt, userPrompt, history),
        stream = stream,
        temperature = temperature,
        maxTokens = maxTokens
    )

    fun conversationMessages(
        systemPrompt: String,
        userPrompt: String,
        history: List<Message>
    ): List<Message> = buildList {
        add(Message("system", systemPrompt))
        addAll(
            history
                .filter { it.role != "system" && it.content.isNotBlank() }
                .map { message ->
                    if (message.content.length > 2000) message.copy(content = message.content.take(2000))
                    else message
                }
        )
        if (userPrompt.isNotBlank()) add(Message("user", userPrompt))
    }

    fun putBase(
        builder: JsonObjectBuilder,
        profile: ModelProfileEntity,
        messages: List<Message>,
        stream: Boolean,
        withTools: Boolean = false
    ) {
        builder.put("model", profile.modelName)
        builder.put("stream", stream)
        // 采样参数（温度、top_p）仍由模型服务自己决定，App 不发；
        // 但**思考开关是 App 的**：用户有时把 Base URL 换成别家官方 API，这件事不能交给后台。
        // 关闭时一个字段都不发（见 ConversationThinkingPolicy）。
        ConversationThinkingPolicy.apply(builder, profile, withTools = withTools)
        builder.put("messages", buildJsonArray {
            messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })
    }
}
