package com.aster.companion.domain

/** Detects a character's expressed wish to send a photo when the model omitted a tool call. */
object CharacterImageIntentPolicy {
    fun expressesImageDesire(message: String): Boolean {
        val text = message.lowercase().replace(Regex("\\s+"), "")
        if (text.isBlank() || text.contains("\u4f60\u53d1\u6765\u7684\u56fe\u7247")) return false
        val visual = listOf("\u7167\u7247", "\u56fe\u7247", "\u81ea\u62cd", "\u751f\u56fe", "\u4e00\u5f20", "\u4e00\u5f20\u7167\u7247").any(text::contains)
        if (!visual) return false
        val senderIntent = listOf(
            "\u6211\u60f3\u7ed9\u4f60\u53d1", "\u60f3\u53d1\u7ed9\u4f60", "\u7ed9\u4f60\u53d1", "\u53d1\u7ed9\u4f60",
            "\u6211\u60f3\u62cd", "\u7b49\u6211\u62cd", "\u62cd\u4e00\u5f20", "\u62cd\u5f20", "\u8981\u4e0d\u8981\u770b\u6211", "\u60f3\u4e0d\u60f3\u770b"
        ).any(text::contains)
        return senderIntent
    }
}
