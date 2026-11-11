package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeviceActionRequestParserTest {
    @Test
    fun `turns an explicit afternoon alarm request into a confirmed Shizuku action`() {
        val action = requireNotNull(DeviceActionRequestParser.parse("给我定个下午四点的闹钟"))

        assertEquals("device.set_alarm", action.capabilityId)
        assertEquals(ActionTransport.SHIZUKU, action.transport)
        assertEquals(ConfirmationRequirement.REQUIRED, action.confirmation)
        assertEquals("16", action.arguments["hour"])
        assertEquals("0", action.arguments["minute"])
    }

    @Test
    fun `understands a morning half-past alarm request`() {
        val action = requireNotNull(DeviceActionRequestParser.parse("明早七点半提醒我起床"))

        assertEquals("7", action.arguments["hour"])
        assertEquals("30", action.arguments["minute"])
    }

    @Test
    fun `晚上12点指的是午夜而不是中午`() {
        // 差 12 小时的那种错：用户要 00:00，闹钟不能定在 12:00。
        // （注意：这条兜底解析只认「闹钟 / 提醒我 / 叫我起床」这几个词，所以测试句里必须有它们。）
        assertEquals("0", requireNotNull(DeviceActionRequestParser.parse("晚上12点提醒我睡觉")).arguments["hour"])
        assertEquals("0", requireNotNull(DeviceActionRequestParser.parse("凌晨十二点叫我起床")).arguments["hour"])
        // 中午十二点仍然是 12。
        assertEquals("12", requireNotNull(DeviceActionRequestParser.parse("中午十二点提醒我")).arguments["hour"])
        // 晚上 11 点照旧 +12。
        assertEquals("23", requireNotNull(DeviceActionRequestParser.parse("晚上十一点提醒我")).arguments["hour"])
    }
}
