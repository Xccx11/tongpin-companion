package com.aster.companion.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats the device clock once per model turn so relative dates remain grounded. */
object CurrentTimeContext {
    private val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINA)

    fun describe(now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): String =
        formatter.format(Instant.ofEpochMilli(now).atZone(zoneId)) + "（${zoneId.id}）"
}
