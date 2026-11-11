package com.aster.companion.lite.proactive

/**
 * 主动探活输出的校验（分册 §6.4）：只接受 SILENT 或一条自然的微信体正文。
 * 出现列表、markdown、系统腔或明显超长时判为不合格，由调用方决定是否重试一次。
 */
object LiteProactiveReplyPolicy {

    const val SILENT = "SILENT"

    sealed interface Verdict {
        /** 角色选择不说话，不落库、不通知。 */
        data object Silent : Verdict
        /** 可以落库的正文。 */
        data class Speak(val text: String) : Verdict
        /** 主动想给用户看一张照片：只带中文意图，英文 tag 由 Scene 流水线另出。 */
        data class Photo(val intent: String) : Verdict
        /** 不合格，原因用于日志与状态展示。 */
        data class Invalid(val reason: String) : Verdict
    }

    fun parse(raw: String, settings: LiteProactiveSettings = LiteProactiveSettings()): Verdict {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Verdict.Invalid("empty")
        if (trimmed.equals(SILENT, ignoreCase = true)) return Verdict.Silent
        // 主动生图意图（分册 §3 二期的可选行为）。
        PHOTO_INTENT.find(trimmed)?.let { match ->
            val intent = match.groupValues[1].trim().take(PHOTO_INTENT_CHARS)
            return if (intent.isEmpty()) Verdict.Invalid("empty_photo_intent") else Verdict.Photo(intent)
        }
        // 围栏要在剥离包裹反引号之前判断，否则 ``` 会被 removeSurrounding 吃掉。
        if (trimmed.contains("```")) return Verdict.Invalid("markdown_fence")
        val text = trimmed.removeSurrounding("`").removeSurrounding("\"").trim()
        if (text.isEmpty()) return Verdict.Invalid("empty")
        if (MARKDOWN_LINE.containsMatchIn(text)) return Verdict.Invalid("markdown_line")
        if (SYSTEM_TONE.containsMatchIn(text)) return Verdict.Invalid("system_tone")
        if (text.length > settings.hardMaxMessageChars) return Verdict.Invalid("too_long")
        val body = text.lines().first { it.isNotBlank() }.trim()
        if (body.isEmpty()) return Verdict.Invalid("empty")
        return Verdict.Speak(body)
    }

    /** 主动发照片的冷却（分册 §3：默认只出文字，想发照片要更克制）。 */
    fun canSendPhoto(lastPhotoAt: Long, now: Long, settings: LiteProactiveSettings): Boolean {
        if (!settings.photoEnabled) return false
        if (lastPhotoAt <= 0L) return true
        return now - lastPhotoAt >= settings.photoCooldownMs
    }

    /** 正文是否保持在建议长度内（超出只提示，不否决）。 */
    fun exceedsPreferredLength(text: String, settings: LiteProactiveSettings = LiteProactiveSettings()): Boolean =
        text.length > settings.maxMessageChars

    private const val PHOTO_INTENT_CHARS = 120
    private val PHOTO_INTENT = Regex("^PHOTO\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val MARKDOWN_LINE = Regex("(?m)^\\s*(#{1,6}\\s|[-*+]\\s|\\d+[.、)]\\s)")
    private val SYSTEM_TONE = Regex("作为\\s*AI|作为人工智能|语言模型|系统提示|提示词|assistant", RegexOption.IGNORE_CASE)
}
