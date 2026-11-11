package com.aster.companion.lite

import android.content.Context
import com.aster.companion.agent.ImageGenerationRequestPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lite 出图分辨率选项。默认竖屏 3:4，适合 vivo 等窄长屏手机全屏查看。 */
@Singleton
class LiteImageSizeStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 必须先于 [_size] 初始化 —— read() 里会用到它。
     * Kotlin 属性按声明顺序初始化；若放在 _size 之后，构造时这里还是 null，
     * read() 里的 allowedSizes.contains(...) 会抛 NPE（已造成过一次启动闪退）。
     */
    private val allowedSizes: Set<String> = OPTIONS.map { it.second }.toSet()

    private val _size = MutableStateFlow(read())

    val size: StateFlow<String> = _size

    fun set(size: String) {
        val normalized = if (isAcceptable(size)) size else LiteProductConfig.IMAGE_SIZE
        preferences.edit().putString(KEY, normalized).apply()
        _size.value = normalized
    }

    private fun read(): String {
        val saved = preferences.getString(KEY, null)
        return if (saved != null && isAcceptable(saved)) saved else LiteProductConfig.IMAGE_SIZE
    }

    /** 内置档位和设置页填的自定义尺寸都接受；其它一律回退默认值，避免存进一个跑不了的尺寸。 */
    private fun isAcceptable(value: String?): Boolean =
        value != null &&
            (allowedSizes.contains(value) || ImageGenerationRequestPolicy.isValidSize(value))

    companion object {
        private const val PREFS = "tongpin_lite_image_size"
        private const val KEY = "image_size"

        /** 设置页下拉里「自定义…」那一项的文案（点了开输入框，不是直接生效的尺寸）。 */
        const val CUSTOM_LABEL = "自定义…"

        /** label → WIDTHxHEIGHT。默认 768x1024：手机竖屏 3:4，清晰度/显存平衡。 */
        val OPTIONS: List<Pair<String, String>> = listOf(
            "768×1024 竖屏·手机推荐" to "768x1024",
            "832×1216 竖屏·更清晰" to "832x1216",
            "768×1344 竖屏·更长" to "768x1344",
            "1024×1024 方形" to "1024x1024",
            "1024×768 横屏" to "1024x768",
            "512×768 竖屏·省显存" to "512x768"
        )

        /** 内置档位显示它的中文标签；自定义尺寸显示「自定义 1024×1024」，让用户看得到实际值。 */
        fun labelFor(size: String): String {
            OPTIONS.firstOrNull { it.second == size }?.let { return it.first }
            val parts = size.split("x")
            return if (parts.size == 2 && parts.all { it.toIntOrNull() != null }) {
                "自定义 ${parts[0]}×${parts[1]}"
            } else {
                size
            }
        }
    }
}
