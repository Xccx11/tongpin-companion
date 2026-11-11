package com.aster.companion.agent

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 给模型看的**可读时间**（`MM-dd HH:mm`）。
 *
 * 以前好几处把 epoch 毫秒直接拼进 prompt（`时间=1758000000000`）：模型会把它当事实念出来，
 * 而且完全算不出"这是多久之前"。所有"给模型看的时间"都应该走这里。
 */
object TimeLabel {
    private val FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    fun of(millis: Long): String =
        if (millis <= 0L) "未知" else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(FORMAT)
}
