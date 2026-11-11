package com.aster.companion.agent

import android.util.Log
import com.aster.companion.data.CharacterEntity
import com.aster.companion.data.CompanionDao
import com.aster.companion.data.CompanionSecretCipher
import com.aster.companion.data.ModelProfileEntity
import com.aster.companion.lite.LiteProductConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

internal object PhotoReplyLines {

    data class Lines(val tags: String, val why: String?)

    fun split(raw: String, format: ScenePromptFormat = ScenePromptFormat.ENGLISH_TAGS): Lines {
        val match = WHY_LINE.find(raw) ?: return Lines(ImageGenerationPromptPolicy.singleLine(raw, format), null)
        val candidate = match.groupValues[1].trim().take(REASON_MAX_CHARS)
        if (!candidate.any { it.code in CJK_RANGE }) {
            return Lines(ImageGenerationPromptPolicy.singleLine(raw, format), null)
        }
        // 正文压成一行：tag 形态按逗号切，自然语言形态只压空白。
        val tags = ImageGenerationPromptPolicy.singleLine(raw.removeRange(match.range), format)
        return Lines(tags, candidate)
    }

    /** 「为什么」这一行最长留多少字（只给人看）。 */
    private const val REASON_MAX_CHARS = 60

    private val CJK_RANGE = 0x3400..0x9FFF

    /** WHY: / WHY： / 为什么： / 原因： 开头的整行。 */
    private val WHY_LINE = Regex("(?im)^[ \\t]*(?:WHY|为什么|原因)[ \\t]*[:：][ \\t]*(.+?)[ \\t]*$")
}

