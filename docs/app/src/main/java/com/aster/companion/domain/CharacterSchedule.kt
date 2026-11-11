package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 角色作息表：默认 23:00 睡—7:30 醒，可按角色调整。
 *
 * 正常睡眠按作息推进；异常睡眠（如推演中断错过起床点）超过 [maxSleepMinutes]
 * 必须兜底唤醒，保证「睡眠 ≤90 分钟必醒」的世界规则。
 */
@Serializable
data class CharacterSchedule(
    val sleepHour: Int = 23,
    val sleepMinute: Int = 0,
    val wakeHour: Int = 7,
    val wakeMinute: Int = 30,
    /** 异常睡眠最长时长（分钟），超过必醒。 */
    val maxSleepMinutes: Long = 90
) {

    /** 该日期的入睡时间点（毫秒，本地时区）。 */
    fun sleepStartMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(LocalTime.of(sleepHour, sleepMinute)).atZone(zone).toInstant().toEpochMilli()

    /** 该日期的起床时间点（毫秒，本地时区）。 */
    fun wakeUpMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(LocalTime.of(wakeHour, wakeMinute)).atZone(zone).toInstant().toEpochMilli()

    /**
     * 判断某个时刻是否处于作息睡眠区间（支持跨天：23:00→次日 7:30）。
     * 睡眠开始时刻与结束时刻重合时（极端配置）视为未睡眠。
     */
    fun isSleeping(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val now = LocalTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val start = LocalTime.of(sleepHour, sleepMinute)
        val wake = LocalTime.of(wakeHour, wakeMinute)
        if (start == wake) return false
        return if (start.isBefore(wake)) {
            !now.isBefore(start) && now.isBefore(wake)
        } else {
            !now.isBefore(start) || now.isBefore(wake)
        }
    }

    /** 下一次起床时间点（毫秒）：若当前时刻未过本日 wake 点则取本日，否则取次日。 */
    fun nextWakeUp(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val now = LocalTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val wake = LocalTime.of(wakeHour, wakeMinute)
        // 若此刻仍处于睡眠中且已过 wake（异常跨日），或现在还没到 wake，都取本日 wake；
        // 否则已清醒且过 wake，取次日 wake。
        val todayWake = wakeUpMillis(date, zone)
        return if (now.isBefore(wake) || isSleeping(nowMillis, zone)) {
            if (now.isBefore(wake)) todayWake else todayWake
        } else {
            wakeUpMillis(date.plusDays(1), zone)
        }
    }

    /** 异常睡眠兜底唤醒时间点：入睡时刻 + maxSleepMinutes。 */
    fun forcedWakeUp(fellAsleepAt: Long): Long = fellAsleepAt + maxSleepMinutes * 60_000L

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val DEFAULT = CharacterSchedule()
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(raw: String): CharacterSchedule =
            try {
                json.decodeFromString(serializer(), raw)
            } catch (_: Exception) {
                DEFAULT
            }
    }
}
