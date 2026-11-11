package com.aster.companion.ui

import com.aster.companion.data.PendingActionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGenerationActionPresentationTest {
    @Test
    fun `describes a separate group request by character names`() {
        val action = PendingActionEntity(
            id = "a", eventId = null, threadId = "group", capabilityId = "companion.propose_image",
            transport = "LOCAL", argumentsJson = "{\"participant_character_ids\":\"xia,lan\",\"group_image_mode\":\"SEPARATE\"}",
            state = "PENDING", createdAt = 0, resolvedAt = null, resultSummary = null
        )

        assertEquals(
            "林见夏和岚想分别发照片",
            ImageGenerationActionPresentation.title(action, mapOf("xia" to "林见夏", "lan" to "岚"))
        )
    }

    @Test
    fun `shows the character's own consent line when the payload carries one`() {
        // 出图请求里一直带着 consent_note（她的原话）。同意卡曾经只显示系统拼的"她想发一张照片"，
        // 把语气丢了 —— 这条测试盯住"她的原话优先"。
        val action = PendingActionEntity(
            id = "b", eventId = null, threadId = "private", capabilityId = "companion.generate_image",
            transport = "LOCAL",
            argumentsJson = "{\"scene\":\"portrait\",\"character_id\":\"xia\",\"consent_note\":\"别催，我去换件睡衣拍给你。\"}",
            state = "PENDING", createdAt = 0, resolvedAt = null, resultSummary = null
        )

        assertEquals(
            "别催，我去换件睡衣拍给你。",
            ImageGenerationActionPresentation.title(action, mapOf("xia" to "林见夏"))
        )
    }

    @Test
    fun `falls back to the generated label when there is no consent line`() {
        val action = PendingActionEntity(
            id = "c", eventId = null, threadId = "private", capabilityId = "companion.generate_image",
            transport = "LOCAL",
            argumentsJson = "{\"scene\":\"portrait\",\"character_id\":\"xia\",\"consent_note\":\"   \"}",
            state = "PENDING", createdAt = 0, resolvedAt = null, resultSummary = null
        )

        assertEquals(
            "林见夏想发一张照片",
            ImageGenerationActionPresentation.title(action, mapOf("xia" to "林见夏"))
        )
    }
}
