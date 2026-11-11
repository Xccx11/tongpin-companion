package com.aster.companion.domain

/** A photo action requires an actual request for the current character, not a photo mention. */
object ImageRequestPolicy {
    /** Explicit local language is a hard product signal; a semantic model cannot veto it. */
    fun shouldStartCharacterPhotoGeneration(localRequest: Boolean, semanticIntent: Boolean?): Boolean =
        localRequest || semanticIntent == true

    fun isExplicitPhotoRequest(message: String): Boolean {
        val text = normalize(message)
        return PHOTO_REQUEST_PATTERNS.any { it.containsMatchIn(text) }
    }

    /** A short assent only means "send a photo" when it answers the character's immediate photo offer. */
    fun isPhotoGenerationRequest(message: String, precedingCompanionMessage: String?): Boolean {
        if (isExplicitPhotoRequest(message)) return true
        val response = normalize(message)
        return precedingCompanionMessage != null &&
            PHOTO_ACCEPTANCE_TERMS.any(response::contains) &&
            isPhotoOffer(precedingCompanionMessage)
    }

    /** Keeps the semantic classifier away from sentences that merely mention an existing photo. */
    fun isPhotoReferenceOnly(message: String): Boolean {
        val text = normalize(message)
        if (PHOTO_TERMS.none(text::contains)) return false
        return PHOTO_REQUEST_CONTEXT_TERMS.none(text::contains)
    }

    private fun isPhotoOffer(message: String): Boolean {
        val text = normalize(message)
        return PHOTO_TERMS.any(text::contains) && PHOTO_OFFER_TERMS.any(text::contains)
    }

    private fun normalize(message: String): String = message.replace(Regex("\\s+"), "")

    private val PHOTO_REQUEST_PATTERNS = listOf(
        Regex("(给我|发我|发给我|让我看|让我看看|给我看看).{0,12}(你的|你|自拍|照片|图片|样子)"),
        Regex("(想看|要看|看看).{0,10}(你的|你|自拍|现在的样子|样子)"),
        Regex("(来一张|拍一张|拍张|生成一张|生成张|做一张).{0,12}(你的|你|自拍|照片|图片|样子)"),
        Regex("发.{0,12}(你的|你|自拍|照片|图片|样子).{0,6}给我"),
        Regex("再(发|来|拍|生成).{0,10}(你的|你|自拍|照片|图片|样子)"),
        Regex("(照片|自拍|图片|图).{0,4}(发|来|给我|看看)"),
        Regex("(发|来|给我).{0,6}(照片|自拍|图片|图)"),
        Regex("(要|想).{0,4}(照片|自拍|图片)")
    )
    private val PHOTO_REQUEST_CONTEXT_TERMS = listOf(
        "给我", "发我", "发给我", "让我", "想看", "要看", "看看", "要不要", "想不想",
        "来一张", "拍一张", "拍张", "生成", "做一张", "再发", "照片呢", "图片呢", "自拍呢"
    )
    private val PHOTO_TERMS = listOf("照片", "图片", "自拍", "拍的")
    private val PHOTO_OFFER_TERMS = listOf("要不要", "想不想", "想看", "给你发", "发给你", "发你", "给你看看", "拍给你")
    private val PHOTO_ACCEPTANCE_TERMS = listOf("看看", "想看", "试试", "重试", "好呀", "好啊", "可以", "行")
}
