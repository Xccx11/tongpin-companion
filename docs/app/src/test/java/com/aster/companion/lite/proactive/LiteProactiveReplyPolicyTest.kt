package com.aster.companion.lite.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteProactiveReplyPolicyTest {

    @Test
    fun `silent means the character chose not to speak`() {
        assertEquals(LiteProactiveReplyPolicy.Verdict.Silent, LiteProactiveReplyPolicy.parse("SILENT"))
        assertEquals(LiteProactiveReplyPolicy.Verdict.Silent, LiteProactiveReplyPolicy.parse("  silent \n"))
    }

    @Test
    fun `a natural short message is accepted`() {
        val verdict = LiteProactiveReplyPolicy.parse("刚洗完澡，头发还湿着。你在干嘛？")

        assertTrue(verdict is LiteProactiveReplyPolicy.Verdict.Speak)
        assertEquals("刚洗完澡，头发还湿着。你在干嘛？", (verdict as LiteProactiveReplyPolicy.Verdict.Speak).text)
    }

    @Test
    fun `system tone and list formatting are rejected`() {
        assertTrue(LiteProactiveReplyPolicy.parse("作为AI，我可以陪你聊天") is LiteProactiveReplyPolicy.Verdict.Invalid)
        assertTrue(LiteProactiveReplyPolicy.parse("1. 想你了\n2. 在干嘛") is LiteProactiveReplyPolicy.Verdict.Invalid)
        assertTrue(LiteProactiveReplyPolicy.parse("```\n想你了\n```") is LiteProactiveReplyPolicy.Verdict.Invalid)
        assertTrue(LiteProactiveReplyPolicy.parse("# 想你了") is LiteProactiveReplyPolicy.Verdict.Invalid)
    }

    @Test
    fun `empty and runaway replies are rejected`() {
        assertTrue(LiteProactiveReplyPolicy.parse("   ") is LiteProactiveReplyPolicy.Verdict.Invalid)
        val runaway = "想你了" + "再补一句".repeat(40)
        assertTrue(LiteProactiveReplyPolicy.parse(runaway) is LiteProactiveReplyPolicy.Verdict.Invalid)
    }

    @Test
    fun `preferred length flags an overlong but still valid reply`() {
        val settings = LiteProactiveSettings(maxMessageChars = 10)
        val text = "这是一条稍微长一点的句子但还没到硬上限"

        assertTrue(LiteProactiveReplyPolicy.parse(text, settings) is LiteProactiveReplyPolicy.Verdict.Speak)
        assertTrue(LiteProactiveReplyPolicy.exceedsPreferredLength(text, settings))
        assertFalse(LiteProactiveReplyPolicy.exceedsPreferredLength("在呢", settings))
    }

    @Test
    fun `prompts stay lite and free of comfy or tooling language`() {
        val system = LiteProactivePromptBuilder.system(
            LiteProactivePromptBuilder.Input(
                characterName = "夜鸦",
                personalityPrompt = "冷静、话少。",
                relationshipPrompt = "同居女友",
                recent = listOf("User: 今天好累", "夜鸦: 早点睡"),
                timeOfDay = "深夜",
                minutesSinceLastChat = 12
            )
        )

        assertTrue(system.contains("你是「夜鸦」。"))
        assertTrue(system.contains("距上次聊天约 12 分钟"))
        assertTrue(system.contains("只输出 SILENT"))
        assertFalse(system.contains("ComfyUI"))
        assertFalse(system.contains("tool_calls"))
        assertFalse(system.contains("Shizuku"))
        assertFalse(system.contains("电量"))
    }

    @Test
    fun `two stage inner prompt asks for a single short line`() {
        val inner = LiteProactivePromptBuilder.innerSystem(
            LiteProactivePromptBuilder.Input(characterName = "夜鸦", personalityPrompt = "冷静", timeOfDay = "深夜")
        )

        assertTrue(inner.contains("≤15 字"))
        assertFalse(inner.contains("ComfyUI"))
    }

    @Test
    fun `moment prompt stays a short chinese post without comfy or tooling`() {
        val system = LiteProactivePromptBuilder.momentSystem(
            LiteProactivePromptBuilder.Input(
                characterName = "夜鸦",
                personalityPrompt = "冷静、话少。",
                recent = listOf("User: 今天好累", "夜鸦: 早点睡"),
                timeOfDay = "下午"
            )
        )

        assertTrue(system.contains("日常动态"))
        assertTrue(system.contains("只输出 SILENT"))
        assertTrue(system.contains("≤60 字"))
        assertFalse(system.contains("ComfyUI"))
        assertFalse(system.contains("tool_calls"))
        assertFalse(system.contains("Shizuku"))
    }

    @Test
    fun `photo intent is recognised and kept separate from chat text`() {
        val verdict = LiteProactiveReplyPolicy.parse("PHOTO: 厨房里刚烤好的面包")

        assertTrue(verdict is LiteProactiveReplyPolicy.Verdict.Photo)
        assertEquals("厨房里刚烤好的面包", (verdict as LiteProactiveReplyPolicy.Verdict.Photo).intent)
        assertEquals(LiteProactiveReplyPolicy.parse("photo： 窗外的雨"), LiteProactiveReplyPolicy.Verdict.Photo("窗外的雨"))
        assertTrue(LiteProactiveReplyPolicy.parse("PHOTO:") is LiteProactiveReplyPolicy.Verdict.Invalid)
    }

    @Test
    fun `photo sending respects switch and cooldown`() {
        val now = 1_700_000_000_000L

        assertTrue(LiteProactiveReplyPolicy.canSendPhoto(0L, now, LiteProactiveSettings()))
        assertFalse(LiteProactiveReplyPolicy.canSendPhoto(now - 60_000L, now, LiteProactiveSettings()))
        assertTrue(LiteProactiveReplyPolicy.canSendPhoto(now - 4 * 60 * 60_000L, now, LiteProactiveSettings()))
        assertFalse(
            LiteProactiveReplyPolicy.canSendPhoto(0L, now, LiteProactiveSettings(photoEnabled = false))
        )
    }

    @Test
    fun `probe prompt only mentions photo when it is allowed`() {
        val base = LiteProactivePromptBuilder.Input(characterName = "夜鸦", personalityPrompt = "冷静")

        assertFalse(LiteProactivePromptBuilder.system(base).contains("PHOTO:"))
        assertTrue(LiteProactivePromptBuilder.system(base.copy(photoAllowed = true)).contains("PHOTO:"))
    }
}
