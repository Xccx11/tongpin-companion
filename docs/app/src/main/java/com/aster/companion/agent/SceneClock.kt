package com.aster.companion.agent

import java.time.LocalTime

internal fun timeOfDay(): String {
        val now = java.time.LocalTime.now()
        val period = when (now.hour) {
            in 5..8 -> "清晨"
            in 9..11 -> "上午"
            in 12..13 -> "中午"
            in 14..17 -> "下午"
            in 18..22 -> "晚上"
            else -> "深夜"
        }
        return "%s %02d:%02d".format(period, now.hour, now.minute)
    }

