package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity

enum class ImageProviderProtocol {
    AUTO,
    OPENAI_IMAGES,
    COMFY_GATEWAY_TASK,
    COMFY_UI,
    QWEN_DASHSCOPE
}

/** Resolves an explicit provider protocol, with AUTO retained only for old saved profiles. */
object ImageProviderProtocolResolver {
    /**
     * **出图只有一条通道**：OpenAI Images 兼容接口（2026-11-11 简化）。
     *
     * 本地网关和各家 API 都是 `POST {baseUrl}/v1/images/generations`，所以不需要再按模型名
     * 猜协议（原来会在 COMFY_GATEWAY_TASK / COMFY_UI / QWEN_DASHSCOPE 之间切）。
     *
     * 枚举值保留：`ComfyUiProtocol` / `ComfyGatewayTaskProtocol` / `QwenImageApi` 仍引用它们，
     * 只是那些分支永远不会被选中（`profile.imageProtocol` 字段也因此忽略，零迁移）。
     */
    fun resolve(@Suppress("UNUSED_PARAMETER") profile: ModelProfileEntity): ImageProviderProtocol =
        ImageProviderProtocol.OPENAI_IMAGES
}

/** 所有 Provider 都接收「外貌原样 + 当刻场景」，保证人物一致。 */
object ImageProviderPromptPolicy {
    fun forProvider(
        profile: ModelProfileEntity,
        characterAuthoredPrompt: String,
        appWorkflowPrompt: String
    ): String = appWorkflowPrompt.ifBlank { characterAuthoredPrompt }
}
