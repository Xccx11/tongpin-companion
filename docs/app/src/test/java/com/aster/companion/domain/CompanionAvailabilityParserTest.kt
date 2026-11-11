package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionAvailabilityParserTest {
    @Test
    fun `sleep wording pauses proactive contact and requests a diary`() {
        assertEquals(CompanionAvailabilitySignal.PAUSE_AND_WRITE_DIARY, CompanionAvailabilityParser.parse("我要睡觉了，晚安"))
    }

    @Test
    fun `busy wording pauses proactive contact`() {
        assertEquals(CompanionAvailabilitySignal.PAUSE, CompanionAvailabilityParser.parse("我去学习一会儿"))
        assertEquals(CompanionAvailabilitySignal.PAUSE, CompanionAvailabilityParser.parse("我要打游戏了"))
        assertEquals(CompanionAvailabilitySignal.PAUSE_AND_WRITE_DIARY, CompanionAvailabilityParser.parse("我先睡会儿，晚点见"))
        assertEquals(CompanionAvailabilitySignal.PAUSE, CompanionAvailabilityParser.parse("我现在去写作业啦"))
        assertEquals(CompanionAvailabilitySignal.PAUSE, CompanionAvailabilityParser.parse("我要开黑了"))
    }

    @Test
    fun `return wording restores proactive contact before the next event`() {
        assertEquals(CompanionAvailabilitySignal.RESUME, CompanionAvailabilityParser.parse("我醒了，回来啦"))
        assertEquals(CompanionAvailabilitySignal.RESUME, CompanionAvailabilityParser.parse("我打完了"))
        assertEquals(CompanionAvailabilitySignal.RESUME, CompanionAvailabilityParser.parse("我没睡！"))
        assertEquals(CompanionAvailabilitySignal.RESUME, CompanionAvailabilityParser.parse("到家了，继续聊"))
        assertEquals(CompanionAvailabilitySignal.RESUME, CompanionAvailabilityParser.parse("学习忙完了"))
        assertEquals(CompanionAvailabilitySignal.RESUME, CompanionAvailabilityParser.parse("我忙完啦，回来咯"))
        assertEquals(CompanionAvailabilitySignal.RESUME, CompanionAvailabilityParser.parse("我醒来啦，可以聊天了"))
    }
}
