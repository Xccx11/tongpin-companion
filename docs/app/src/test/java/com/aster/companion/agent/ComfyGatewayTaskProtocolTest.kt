package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyGatewayTaskProtocolTest {
    @Test
    fun `background task protocol is no longer selected after unifying to OpenAI Images`() {
        val gateway = ModelProfileEntity("image", "image_generation", "https://images.example/v1", "wai-illustrious-v1", true, 120, "key", null)
        val regular = gateway.copy(modelName = "gpt-image-2")

        assertFalse(ComfyGatewayTaskProtocol.supports(gateway))
        assertFalse(ComfyGatewayTaskProtocol.supports(regular))
    }

    @Test
    fun `parses queued running success and failure task responses`() {
        assertEquals(ComfyGatewayTaskStatus.Queued, ComfyGatewayTaskProtocol.parseStatus("""{"id":"imgjob-1","status":"queued"}""").status)
        assertEquals(ComfyGatewayTaskStatus.Running, ComfyGatewayTaskProtocol.parseStatus("""{"id":"imgjob-1","status":"running"}""").status)

        val done = ComfyGatewayTaskProtocol.parseStatus("""{"id":"imgjob-1","status":"succeeded","data":[{"url":"https://gateway/v1/assets/a"}]}""")
        assertEquals(ComfyGatewayTaskStatus.Succeeded, done.status)
        assertEquals("https://gateway/v1/assets/a", done.imageUrl)

        val failed = ComfyGatewayTaskProtocol.parseStatus("""{"id":"imgjob-1","status":"failed","error":{"message":"ComfyUI image task failed"}}""")
        assertEquals(ComfyGatewayTaskStatus.Failed, failed.status)
        assertEquals("ComfyUI image task failed", failed.error)

        val cancelled = ComfyGatewayTaskProtocol.parseStatus("""{"id":"imgjob-1","status":"cancelled"}""")
        assertEquals(ComfyGatewayTaskStatus.Cancelled, cancelled.status)
    }
}
