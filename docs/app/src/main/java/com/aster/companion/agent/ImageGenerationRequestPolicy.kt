package com.aster.companion.agent

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** OpenAI Images request defaults used by the documented ComfyUI-backed gateway. */
object ImageGenerationRequestPolicy {
    const val DEFAULT_SIZE = "768x768"
    const val RESPONSE_FORMAT = "b64_json"

    /** 宽高各自的上下限。上限比网关侧的 2048 保守：12GB 卡上再大就明显更慢、也更容易爆显存。 */
    const val MIN_SIZE = 256
    const val MAX_SIZE = 1536

    /**
     * 尺寸对齐粒度。
     *
     * latent 是 1/8 下采样，尺寸不对齐会被 ComfyUI 静默截断（填 1001 实际得到 1000 宽的图，
     * 用户以为生效了其实没有）。内置档位本来就都是 64 的倍数，所以自定义尺寸按 64 对齐最稳。
     */
    const val SIZE_ALIGN = 64

    fun normalizeSize(value: String?): String {
        val raw = value?.trim().orEmpty()
        val match = SIZE_PATTERN.matchEntire(raw) ?: return DEFAULT_SIZE
        val width = match.groupValues[1].toIntOrNull() ?: return DEFAULT_SIZE
        val height = match.groupValues[2].toIntOrNull() ?: return DEFAULT_SIZE
        return if (width in MIN_SIZE..MAX_SIZE && height in MIN_SIZE..MAX_SIZE) raw else DEFAULT_SIZE
    }

    /**
     * 工具参数没给尺寸时，用设置页选的那一档（[fallback]）。
     *
     * 以前这里是直接 `normalizeSize(arguments["size"] ?: null)`：模型不写 size 是常态，
     * 于是**聊天里生成的每一张照片都是 768x768**，设置页的「图片尺寸」（默认 832x1216、
     * 也可以是自定义竖屏）只对「场景照片」那条确认路径生效 —— 一个改了没反应的开关。
     */
    fun resolveSize(requested: String?, fallback: String?): String =
        requested?.takeIf { it.isNotBlank() } ?: fallback.orEmpty()

    /** 是否是 App 认得的合法尺寸字符串（内置档位与自定义都算）。 */
    fun isValidSize(value: String?): Boolean {
        val raw = value?.trim().orEmpty()
        val match = SIZE_PATTERN.matchEntire(raw) ?: return false
        val width = match.groupValues[1].toIntOrNull() ?: return false
        val height = match.groupValues[2].toIntOrNull() ?: return false
        return width in MIN_SIZE..MAX_SIZE && height in MIN_SIZE..MAX_SIZE
    }

    /**
     * 设置页「自定义尺寸」用：把两格输入规范化成 "WxH"，非法就返回 null。
     *
     * 非法时**返回 null 而不是回退默认值**：静默回退会让用户以为自己填的生效了。
     * 返回值是按 [SIZE_ALIGN] 对齐后的结果，设置页要把它显示出来（填 1000 会看到 1024）。
     */
    fun customSizeOrNull(widthText: String?, heightText: String?): String? {
        val width = widthText?.trim()?.toIntOrNull() ?: return null
        val height = heightText?.trim()?.toIntOrNull() ?: return null
        if (width !in MIN_SIZE..MAX_SIZE || height !in MIN_SIZE..MAX_SIZE) return null
        return "${align(width)}x${align(height)}"
    }

    private fun align(value: Int): Int =
        (((value + SIZE_ALIGN / 2) / SIZE_ALIGN) * SIZE_ALIGN).coerceIn(MIN_SIZE, MAX_SIZE)

    fun payload(modelName: String, prompt: String, size: String? = null): String = buildJsonObject {
        // 只发请求本身需要的：模型、提示词、尺寸、返回格式。
        // 采样参数（steps / cfg / sampler 等）由后端与 ComfyUI 工作流决定，App 不覆盖。
        put("model", modelName)
        put("prompt", prompt)
        put("size", normalizeSize(size))
        put("response_format", RESPONSE_FORMAT)
    }.toString()

    private val SIZE_PATTERN = Regex("(\\d{3,4})x(\\d{3,4})")
}
