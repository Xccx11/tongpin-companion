package com.aster.companion.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentTimeContextTest {
    @Test
    fun `formats the local calendar date weekday and time for a model turn`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val instant = ZonedDateTime.of(2026, 7, 21, 14, 30, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("2026年7月21日 星期二 14:30（Asia/Shanghai）", CurrentTimeContext.describe(instant, zone))
    }
}
