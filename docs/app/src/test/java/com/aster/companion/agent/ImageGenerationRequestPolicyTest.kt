package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationRequestPolicyTest {
    @Test
    fun `uses documented 768 square default and accepts a size override`() {
        assertEquals("768x768", ImageGenerationRequestPolicy.normalizeSize(null))
        assertEquals("512x768", ImageGenerationRequestPolicy.normalizeSize("512x768"))
        assertEquals("1536x1024", ImageGenerationRequestPolicy.normalizeSize("1536x1024"))
    }

    @Test
    fun `invalid or unsafe image sizes fall back to the documented default`() {
        assertEquals("768x768", ImageGenerationRequestPolicy.normalizeSize("1024*1024"))
        assertEquals("768x768", ImageGenerationRequestPolicy.normalizeSize("2048x2048"))
        assertEquals("768x768", ImageGenerationRequestPolicy.normalizeSize("9999x9999"))
        assertEquals("768x768", ImageGenerationRequestPolicy.normalizeSize(""))
    }

    @Test
    fun `standard gateway payload requests base64 png with the normalized size`() {
        val payload = Json.parseToJsonElement(
            ImageGenerationRequestPolicy.payload(
                modelName = "wai-nsfw-illustrious",
                prompt = "one woman reading by a warm window",
                size = "512x768"
            )
        ).jsonObject

        assertEquals("wai-nsfw-illustrious", payload["model"]?.jsonPrimitive?.content)
        assertEquals("one woman reading by a warm window", payload["prompt"]?.jsonPrimitive?.content)
        assertEquals("512x768", payload["size"]?.jsonPrimitive?.content)
        assertEquals("b64_json", payload["response_format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `custom size snaps to the 64 grid and reports the effective value`() {
        assertEquals("1024x1024", ImageGenerationRequestPolicy.customSizeOrNull("1024", "1024"))
        // 1000 -> 1024、760 -> 768：告诉用户的是真正会用的值，不是他填的那个。
        assertEquals("1024x768", ImageGenerationRequestPolicy.customSizeOrNull("1000", "760"))
        assertEquals("768x1344", ImageGenerationRequestPolicy.customSizeOrNull("768", "1344"))
        assertEquals("256x1536", ImageGenerationRequestPolicy.customSizeOrNull("256", "1536"))
        assertEquals("512x768", ImageGenerationRequestPolicy.customSizeOrNull(" 512 ", " 768 "))
    }

    @Test
    fun `custom size rejects out of range and non numeric input instead of silently defaulting`() {
        assertNull(ImageGenerationRequestPolicy.customSizeOrNull("", "1024"))
        assertNull(ImageGenerationRequestPolicy.customSizeOrNull(null, "1024"))
        assertNull(ImageGenerationRequestPolicy.customSizeOrNull("1024", null))
        assertNull(ImageGenerationRequestPolicy.customSizeOrNull("abc", "1024"))
        assertNull(ImageGenerationRequestPolicy.customSizeOrNull("128", "1024"))
        assertNull(ImageGenerationRequestPolicy.customSizeOrNull("1024", "2048"))
    }

    @Test
    fun `isValidSize accepts built in presets and custom values but not junk`() {
        assertTrue(ImageGenerationRequestPolicy.isValidSize("768x1344"))
        assertTrue(ImageGenerationRequestPolicy.isValidSize("1024x1024"))
        assertFalse(ImageGenerationRequestPolicy.isValidSize(null))
        assertFalse(ImageGenerationRequestPolicy.isValidSize(""))
        assertFalse(ImageGenerationRequestPolicy.isValidSize("2048x2048"))
        assertFalse(ImageGenerationRequestPolicy.isValidSize("1024*1024"))
    }

    @Test
    fun `工具没给尺寸时用设置页选的那一档`() {
        // 模型不写 size 是常态：以前这里会退回 768x768，设置页选什么都不生效。
        assertEquals("832x1216", ImageGenerationRequestPolicy.resolveSize(null, "832x1216"))
        assertEquals("832x1216", ImageGenerationRequestPolicy.resolveSize("", "832x1216"))
        assertEquals("832x1216", ImageGenerationRequestPolicy.resolveSize("   ", "832x1216"))
        // 工具自己指定了就听它的。
        assertEquals("512x768", ImageGenerationRequestPolicy.resolveSize("512x768", "832x1216"))
        // 两边都没有时仍由 normalizeSize 兜底成文档默认值。
        assertEquals("", ImageGenerationRequestPolicy.resolveSize(null, null))
        assertEquals("768x768", ImageGenerationRequestPolicy.normalizeSize(ImageGenerationRequestPolicy.resolveSize(null, null)))
        assertEquals(
            "832x1216",
            ImageGenerationRequestPolicy.normalizeSize(ImageGenerationRequestPolicy.resolveSize(null, "832x1216"))
        )
    }
}
