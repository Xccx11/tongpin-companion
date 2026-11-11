package com.aster.companion.agent

/** Prevents a model's private image instruction from becoming a visible chat message. */
object ImageReplySanitizer {
    fun visibleReply(raw: String, characterName: String): String {
        if (raw.isBlank() || characterName.isBlank()) return raw.trim()
        val escapedName = Regex.escape(characterName.trim())
        val commandStart = Regex(
            pattern = "(?im)^\\s*我是\\s*$escapedName\\s*[，,。]\\s*(?:我)?(?:想|要|准备|拍|生成|做).{0,180}(?:照片|自拍|图片|图|画面)",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(raw)
        return if (commandStart == null) raw.trim() else raw.substring(0, commandStart.range.first).trim()
    }
}
