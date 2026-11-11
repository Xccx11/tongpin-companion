package com.aster.companion.ui

/** Transient reply feedback only. Reasoning content is never persisted or rendered. */
enum class ReplyIndicatorState {
    THINKING,
    TYPING
}

object ReplyIndicatorPresentation {
    fun state(isReplying: Boolean, hasVisibleStream: Boolean): ReplyIndicatorState? = when {
        !isReplying -> null
        hasVisibleStream -> ReplyIndicatorState.TYPING
        else -> ReplyIndicatorState.THINKING
    }

    fun speakerLabel(isGroup: Boolean, activeCharacterName: String?, privateTitle: String): String? =
        if (isGroup) activeCharacterName?.trim()?.takeIf(String::isNotEmpty)
        else activeCharacterName?.trim()?.takeIf(String::isNotEmpty) ?: privateTitle.trim().takeIf(String::isNotEmpty)

    fun label(threadTitle: String, activeCharacterName: String?, isThinking: Boolean): String {
        val speaker = activeCharacterName?.trim()?.takeIf(String::isNotEmpty) ?: threadTitle.trim()
        return "$speaker ${if (isThinking) "正在思考" else "正在输入"}..."
    }
}
