package com.aster.companion.ui

import com.aster.companion.data.PendingActionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingActionPresentationTest {

    private fun action(capabilityId: String, argumentsJson: String = "{}", transport: String = "LOCAL") =
        PendingActionEntity(
            id = "a",
            eventId = null,
            threadId = "t",
            capabilityId = capabilityId,
            transport = transport,
            argumentsJson = argumentsJson,
            state = "PENDING",
            createdAt = 0,
            resolvedAt = null,
            resultSummary = null
        )

    @Test
    fun `闹钟把时间读成人话`() {
        assertEquals(
            "设置 07:30 的闹钟",
            PendingActionPresentation.title(action("device.set_alarm", "{\"hour\":7,\"minute\":\"30\"}"), emptyMap())
        )
    }

    @Test
    fun `系统动作不再把能力 id 打给用户看`() {
        assertEquals("打开一个应用", PendingActionPresentation.title(action("app.open_known"), emptyMap()))
        assertEquals("打开拨号", PendingActionPresentation.title(action("phone.dial"), emptyMap()))
        assertEquals("打开短信", PendingActionPresentation.title(action("sms.compose"), emptyMap()))
        assertEquals("执行一个手机操作", PendingActionPresentation.title(action("device.unknown_thing"), emptyMap()))
    }

    @Test
    fun `出图动作交给出图那份展示，能显示她的原话`() {
        assertEquals(
            "别催，我去换件睡衣拍给你。",
            PendingActionPresentation.title(
                action("companion.generate_image", "{\"character_id\":\"xia\",\"consent_note\":\"别催，我去换件睡衣拍给你。\"}"),
                mapOf("xia" to "林见夏")
            )
        )
    }

    @Test
    fun `执行通道说人话`() {
        assertEquals("在本机执行", PendingActionPresentation.transportLabel("LOCAL"))
        assertEquals("通过 Shizuku 执行", PendingActionPresentation.transportLabel("shizuku"))
        assertEquals("通过无障碍服务执行", PendingActionPresentation.transportLabel("ACCESSIBILITY"))
    }
}
