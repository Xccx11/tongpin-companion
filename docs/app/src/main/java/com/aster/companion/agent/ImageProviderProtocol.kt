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
    fun resolve(profile: ModelProfileEntity): ImageProviderProtocol {
        val explicit = profile.imageProtocol.trim().uppercase()
            .let { value -> runCatching { ImageProviderProtocol.valueOf(value) }.getOrNull() }
            ?.takeUnless { it == ImageProviderProtocol.AUTO }
        if (explicit != null) return explicit

        // Compatibility for profiles written before protocol was persisted. New documented
        // gateway model names remain standard OpenAI Images unless a protocol is explicit.
        val model = profile.modelName.trim().lowercase()
        return when {
            model == "wai-illustrious-v1" -> ImageProviderProtocol.COMFY_GATEWAY_TASK
            model in setOf(ComfyUiProtocol.QWEN_IMAGE_EDIT_MODEL_NAME, ComfyUiProtocol.ILLUSTRIOUS_XL_MODEL_NAME) -> ImageProviderProtocol.COMFY_UI
            model.startsWith("qwen-image") && profile.baseUrl.lowercase().let { it.contains("dashscope.aliyuncs.com") || it.contains(".maas.aliyuncs.com") } -> ImageProviderProtocol.QWEN_DASHSCOPE
            else -> ImageProviderProtocol.OPENAI_IMAGES
        }
    }
}

/** 所有 Provider 都接收「外貌原样 + 当刻场景」，保证人物一致。 */
object ImageProviderPromptPolicy {
    fun forProvider(
        profile: ModelProfileEntity,
        characterAuthoredPrompt: String,
        appWorkflowPrompt: String
    ): String = appWorkflowPrompt.ifBlank { characterAuthoredPrompt }
}
