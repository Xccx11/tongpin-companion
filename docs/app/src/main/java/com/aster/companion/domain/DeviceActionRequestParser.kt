package com.aster.companion.domain

/** Safety fallback for an explicit alarm request when a model returns prose instead of a tool call. */
object DeviceActionRequestParser {
    private val alarmWords = Regex("闹钟|提醒我|叫我起床")
    private val time = Regex("(凌晨|早上|早晨|上午|中午|下午|晚上)?\\s*([0-9一二三四五六七八九十两]+)\\s*(?:点|时)(?:(半)|([0-9一二三四五六七八九十两]+)\\s*分?)?")

    fun parse(message: String): AgentActionRequest? {
        if (!alarmWords.containsMatchIn(message)) return null
        val match = time.find(message) ?: return null
        var hour = number(match.groupValues[2]) ?: return null
        val qualifier = match.groupValues[1]
        if (qualifier in setOf("下午", "晚上") && hour in 1..11) hour += 12
        if (qualifier == "中午" && hour in 1..10) hour += 12
        // 「晚上12点」「凌晨12点」说的都是**午夜**，不是中午十二点：不加这一条会差 12 小时
        //（用户要的是 00:00，闹钟却定在 12:00）。
        if (qualifier in setOf("晚上", "凌晨") && hour == 12) hour = 0
        val minute = when {
            match.groupValues[3].isNotBlank() -> 30
            match.groupValues[4].isNotBlank() -> number(match.groupValues[4]) ?: return null
            else -> 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return AgentActionRequest(
            capabilityId = "device.set_alarm",
            transport = ActionTransport.SHIZUKU,
            confirmation = ConfirmationRequirement.REQUIRED,
            arguments = mapOf("hour" to hour.toString(), "minute" to minute.toString())
        )
    }

    private fun number(raw: String): Int? = raw.toIntOrNull() ?: when (raw) {
        "十" -> 10
        else -> {
            val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
            if ('十' !in raw) raw.map { digits[it] ?: return null }.fold(0) { total, digit -> total * 10 + digit }
            else {
                val parts = raw.split('十')
                val tens = parts[0].takeIf { it.isNotBlank() }?.let { digits[it.singleOrNull()] } ?: 1
                val ones = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { digits[it.singleOrNull()] } ?: 0
                tens * 10 + ones
            }
        }
    }
}
