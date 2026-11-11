package com.aster.companion.lite

import android.content.Context
import com.aster.companion.agent.ScenePromptFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「用哪一套生图规则」的显式覆盖：设置页可指定，空 = 按模型名自动判断。
 *
 * 做成全局 volatile 是因为 [ScenePromptFormat.forModel] 是静态纯函数、注入不进 Hilt。
 */
object ImageRuleFormatOverride {
    @Volatile
    private var value: ScenePromptFormat? = null

    fun set(format: ScenePromptFormat?) {
        value = format
    }

    fun current(): ScenePromptFormat? = value
}

/**
 * 方案 A：把「规则从哪来」和「用哪套规则」从**地址**与**模型名**里解耦出来。
 *
 * 1) [ruleBaseUrl]：拉 `prompts.json` 的地址。留空 = 跟随图像模型地址（老行为）。
 *    这样对话/图像都换成别家官方 API 时，规则照样从自己的网关同步，不再只剩缓存。
 * 2) [formatOverride]：强制用哪一套生图规则（自动 / Krea 2 / Z-Image / WAI）。
 *    换成官方图像 API 或没见过的模型名时，也能继续用调好的那套规则，
 *    不再因为 `forModel` 认不出名字就掉到英文 tag（wai）那套。
 */
@Singleton
class ImageRuleSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 规则网关地址；空 = 跟随图像模型 profile 的地址。 */
    @Volatile
    var ruleBaseUrl: String = prefs.getString(KEY_BASE, "").orEmpty()
        private set

    /** 规则集：见 [FORMATS]。 */
    @Volatile
    var formatOverride: String = normalizeFormat(prefs.getString(KEY_FORMAT, FORMAT_AUTO).orEmpty())
        private set

    init {
        publishOverride()
    }

    fun setRuleBaseUrl(value: String) {
        ruleBaseUrl = value.trim()
        prefs.edit().putString(KEY_BASE, ruleBaseUrl).apply()
    }

    fun setFormatOverride(value: String) {
        formatOverride = normalizeFormat(value)
        prefs.edit().putString(KEY_FORMAT, formatOverride).apply()
        publishOverride()
    }

    private fun publishOverride() {
        ImageRuleFormatOverride.set(formatOf(formatOverride))
    }

    companion object {
        const val FORMAT_AUTO = "auto"
        const val FORMAT_KREA2 = "krea2"
        const val FORMAT_ZIMAGE = "z-image"
        const val FORMAT_WAI = "wai"

        val FORMATS = listOf(FORMAT_AUTO, FORMAT_KREA2, FORMAT_ZIMAGE, FORMAT_WAI)

        fun labelFor(format: String): String = when (normalizeFormat(format)) {
            FORMAT_KREA2 -> "Krea 2（英文自然语言）"
            FORMAT_ZIMAGE -> "Z-Image（中文自然语言）"
            FORMAT_WAI -> "WAI / Illustrious（英文 tag）"
            else -> "自动（按模型名判断）"
        }

        fun normalizeFormat(value: String): String =
            value.trim().lowercase().takeIf { it in FORMATS } ?: FORMAT_AUTO

        /** 覆盖值 → 形态；`auto` 返回 null（交给 [ScenePromptFormat.forModel] 按模型名判断）。 */
        fun formatOf(value: String): ScenePromptFormat? = when (normalizeFormat(value)) {
            FORMAT_KREA2 -> ScenePromptFormat.ENGLISH_NATURAL
            FORMAT_ZIMAGE -> ScenePromptFormat.CHINESE_NATURAL
            FORMAT_WAI -> ScenePromptFormat.ENGLISH_TAGS
            else -> null
        }

        private const val PREFS = "tongpin_image_rules"
        private const val KEY_BASE = "rule_base_url"
        private const val KEY_FORMAT = "rule_format"
    }
}
