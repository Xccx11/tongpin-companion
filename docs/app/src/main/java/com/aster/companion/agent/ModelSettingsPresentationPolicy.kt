package com.aster.companion.agent

/** Conversation sampling and thinking are owned by the computer-side gateway. */
object ModelSettingsPresentationPolicy {
    const val showConversationSamplingControls: Boolean = false
    const val documentedConversationModel: String = "qwen35-nothink:latest"
    const val documentedImageModel: String = "wai-nsfw-illustrious"
    const val documentedImageSize: String = "768x768"
    const val conversationParametersNotice: String =
        "采样参数（Temperature、Top-P、频率惩罚、存在惩罚）由模型服务自己决定，App 不覆盖；「思考模式」开关由 App 控制——打开后按服务地址自动选参数格式发给模型服务。"
    const val gatewayConnectionNotice: String =
        "Base URL 必须填写当前电脑网关地址（ngrok 域名可能变化，App 不会写死）；API Key 使用任意非空字符串即可，例如 ollama。"
}
