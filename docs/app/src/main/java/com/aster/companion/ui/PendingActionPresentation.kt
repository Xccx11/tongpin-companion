package com.aster.companion.ui

import com.aster.companion.data.PendingActionEntity

/**
 * 待确认动作的**人话标题**：设置页「待确认动作」和聊天里的同意卡共用这一份。
 *
 * 以前两处各写一套：设置页直接把 `capabilityId` 打给用户看（"device.set_alarm"、
 * "通过 LOCAL 执行"），聊天里的同意卡自己 when 了一遍能力名 —— 规则不同、改的时候还容易漏一处。
 */
object PendingActionPresentation {

    /** 出图类动作：标题交给 [ImageGenerationActionPresentation]（它认她的原话与合影模式）。 */
    private val IMAGE_CAPABILITIES = setOf(
        "companion.generate_image",
        "companion.propose_image",
        "companion.propose_private_image",
        "companion.generate_moment_image",
        "companion.peek_image",
        "companion.share_life_moment"
    )

    fun title(action: PendingActionEntity, characterNames: Map<String, String>): String =
        if (action.capabilityId in IMAGE_CAPABILITIES) {
            ImageGenerationActionPresentation.title(action, characterNames)
        } else {
            systemTitle(action)
        }

    /** 手机操作：能力 id 换成一句人话；闹钟顺带把时间读出来。 */
    fun systemTitle(action: PendingActionEntity): String = when (action.capabilityId) {
        "device.set_alarm" -> {
            val hour = argument(action.argumentsJson, "hour")?.padStart(2, '0') ?: "--"
            val minute = argument(action.argumentsJson, "minute")?.padStart(2, '0') ?: "--"
            "设置 $hour:$minute 的闹钟"
        }
        "app.open_known" -> "打开一个应用"
        "phone.dial" -> "打开拨号"
        "sms.compose" -> "打开短信"
        else -> "执行一个手机操作"
    }

    /** 执行通道的人话说法。用户看不懂 LOCAL / SHIZUKU / ACCESSIBILITY。 */
    fun transportLabel(transport: String): String = when (transport.trim().uppercase()) {
        "LOCAL" -> "在本机执行"
        "SHIZUKU" -> "通过 Shizuku 执行"
        "ACCESSIBILITY" -> "通过无障碍服务执行"
        else -> transport
    }

    /**
     * 从 `argumentsJson` 里取一个数字参数。
     *
     * 工具参数可能是数字（`"hour": 7`）也可能是字符串（`"hour": "07"`），所以引号可有可无。
     * 不解析整个 JSON：这条路径在 Compose 组合里每帧都可能跑，正则更省。
     */
    private fun argument(argumentsJson: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"?(\\d+)").find(argumentsJson)?.groupValues?.getOrNull(1)
}
