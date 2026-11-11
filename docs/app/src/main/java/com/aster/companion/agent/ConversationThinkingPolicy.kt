package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 「思考开关」的参数格式。
 *
 * 开关由 **App 自己**决定并发给模型服务（和电脑端网关无关：网关只是纯转发，
 * 见 `vram_arbiter.py` 的 `_forward_llm_chat_inner`；用户有时会把 Base URL 换成
 * 别家的官方 API，所以这件事不能交给后台）。
 *
 * 各家参数名不一样，按 Base URL 自动选；**关掉时一个字段都不发**——保持"模型自己决定"的原行为，
 * 也避免给不支持思考的模型塞不认识的参数。
 */
object ConversationThinkingPolicy {

    /**
     * 把思考参数写进请求体。
     *
     * [withTools]：这次请求带不带 `tools`。**只对 DeepSeek 有影响**——官方 API 在思考模式下
     * 要求把历史里 assistant 的 `reasoning_content` 原样回传。但本 App 的请求体只由
     * `{role, content}` 组成（见 [ConversationRequestPolicy.putBase]），**从不回放
     * assistant 的 tool_calls 消息**，所以那条要求在自建/中转链路上是空条件。
     * 因此只有**官方域名**才保留"带工具就关思考"的保守行为，自建与中转按用户设置来。
     */
    fun apply(builder: JsonObjectBuilder, profile: ModelProfileEntity, withTools: Boolean = false) {
        val endpoint = profile.baseUrl.lowercase()
        if (isXaiGrok(endpoint, profile.modelName)) {
            // grok-4.6 **关不掉思考**：reasoning_effort=none / thinking.disabled 都不支持，
            // 不传这个字段就是默认 high（一直在猛思考）。所以这里的「关」= 压到最低档 low，
            // 「开」= 用户选的那一档。实测矩阵见 hermes-agent#85779：low~xhigh 接受，max/ultra 会 400。
            builder.put(
                "reasoning_effort",
                if (profile.thinkingEnabled) profile.reasoningEffort else XAI_LOWEST_EFFORT
            )
            return
        }
        if (isDeepSeek(endpoint, profile.modelName)) {
            // DeepSeek V4：**思考默认就是开的**（effort 默认 high），所以「关」必须显式禁用，
            // 不传参数等于一直在猛思考。官方 OpenAI 格式：顶层 `thinking: {type: enabled/disabled}`。
            //
            // 带 `tools` 时：官方 API 的思考模式要求 assistant 的 reasoning_content 原样回传，
            // 而 App 的请求里根本没有 assistant 的 tool_calls 消息（工具结果是当作新的 user 消息
            // 再问一次的），所以自建服务（LM Studio / vLLM）和中转站上"思考 + 工具"是安全的，
            // 只有官方域名保留旧的保守做法，免得踩文档级的坑。
            val thinking = profile.thinkingEnabled && !(withTools && isOfficialDeepSeekApi(endpoint))
            builder.put(
                "thinking",
                buildJsonObject { put("type", if (thinking) "enabled" else "disabled") }
            )
            if (thinking) {
                // 官方 effort 只认 low/high/max；它把 medium 映射成 high，所以我们的三个值都能直接用。
                builder.put("reasoning_effort", profile.reasoningEffort)
            }
            return
        }
        if (!profile.thinkingEnabled) return
        // ① 所有支持思考的 OpenAI 兼容服务都认这个（o 系 / gpt-5 / OpenRouter / 兼容层）。
        builder.put("reasoning_effort", profile.reasoningEffort)
        // OpenAI 官方对**未列出的参数直接报错**，所以只给它这一个。
        if (isOpenAiOfficial(endpoint)) return
        // ② Qwen / DashScope 官方与多数国产兼容层（含中转站）：顶层 enable_thinking。
        builder.put("enable_thinking", true)
        if (isDashScope(endpoint)) return
        // ③ 自建推理服务（vLLM / LM Studio）和 DeepSeek-V3.1 这类混合模型走模板参数。
        builder.put(
            "chat_template_kwargs",
            buildJsonObject {
                put("enable_thinking", true)
                put("thinking", true)
            }
        )
    }

    /**
     * 是否是 xAI 的 grok 系（含中转站转发：域名看不出来，所以也认模型名）。
     */
    fun isXaiGrok(baseUrl: String, modelName: String): Boolean =
        baseUrl.lowercase().contains("x.ai") || modelName.lowercase().contains("grok")

    /**
     * 是否是 DeepSeek（含中转站转发：同样也认模型名 `deepseek-*`）。
     *
     * 认它是因为 V4 **默认开着思考**，跟其他家"不传就不思考"的默认值相反。
     */
    fun isDeepSeek(baseUrl: String, modelName: String): Boolean =
        baseUrl.lowercase().contains("deepseek") || modelName.lowercase().contains("deepseek")

    /**
     * 是否是 DeepSeek **官方** API。
     *
     * 只对它保留"带 tools 就关思考"：官方文档要求思考模式 + 工具调用时回传 `reasoning_content`，
     * 而自建/中转链路上 App 的请求里没有 assistant 的 tool_calls 消息，这条要求不成立
     * （也就不用白白牺牲用户的思考开关）。
     */
    fun isOfficialDeepSeekApi(baseUrl: String): Boolean =
        baseUrl.lowercase().contains("api.deepseek.com")

    /**
     * 是否是 OpenAI 官方（对未知请求字段硬报错，只能发 `reasoning_effort`）。
     *
     * 只看主机名，不看路径——中转站经常把 `api.openai.com` 写在自己的域名后面。
     */
    fun isOpenAiOfficial(baseUrl: String): Boolean =
        baseUrl.lowercase().let { it.startsWith("https://api.openai.com") || it.startsWith("http://api.openai.com") }

    /** 是否是 DashScope（通义官方：只认顶层 `enable_thinking`）。 */
    fun isDashScope(baseUrl: String): Boolean =
        baseUrl.lowercase().let { it.contains("dashscope") || it.contains("aliyuncs.com") }

    /**
     * grok「关思考」用的最低档。
     *
     * 用它而不是 `minimal`：xAI 文档只列了 low/medium/high/xhigh，`minimal` 虽然实测被
     * api.x.ai 接受，但中转站常按文档白名单校验，low 是保证被接受的最低档。
     */
    const val XAI_LOWEST_EFFORT = "low"
}
