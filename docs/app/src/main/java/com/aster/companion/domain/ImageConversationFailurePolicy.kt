package com.aster.companion.domain

/** Keeps provider and transport failures out of the companion conversation. */
object ImageConversationFailurePolicy {
    fun reply(): String = "我这次没看清这张图，你晚点再发我一次好不好？"

    fun reply(error: Throwable): String {
        val details = generateSequence(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        return when {
            details.contains("Image understanding model is not configured", ignoreCase = true) ->
                "我现在还没有接上视觉模型，暂时看不到这张图。到设置 > 模型 > 视觉模型配置后再发给我吧。"
            details.contains("Image understanding model is disabled", ignoreCase = true) ->
                "我的视觉模型现在被关掉了，打开后我就能认真看看你发的图。"
            else -> reply()
        }
    }
}
