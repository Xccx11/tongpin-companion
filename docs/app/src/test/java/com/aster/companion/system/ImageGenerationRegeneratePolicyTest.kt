package com.aster.companion.system

import com.aster.companion.data.PendingActionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationRegeneratePolicyTest {
    private fun action(state: String, capabilityId: String = "companion.generate_image") = PendingActionEntity(
        id = "image-1",
        eventId = null,
        threadId = "thread-1",
        capabilityId = capabilityId,
        transport = "LOCAL",
        argumentsJson = "{}",
        state = state,
        createdAt = 1L,
        resolvedAt = null,
        resultSummary = null
    )

    @Test
    fun `running image work may be regenerated`() {
        assertTrue(ImageGenerationRegeneratePolicy.canRegenerate(action("RUNNING")))
        assertTrue(ImageGenerationRegeneratePolicy.canRegenerate(action("RETRYING")))
        assertTrue(ImageGenerationRegeneratePolicy.canRegenerate(action("RUNNING", "companion.peek_image")))
    }

    @Test
    fun `terminal and non image work may not be regenerated`() {
        // FAILED 走 retryImage；CANCELLED 是终态、不许复活；非图片任务不碰。
        assertFalse(ImageGenerationRegeneratePolicy.canRegenerate(action("FAILED")))
        assertFalse(ImageGenerationRegeneratePolicy.canRegenerate(action("CANCELLED")))
        assertFalse(ImageGenerationRegeneratePolicy.canRegenerate(action("APPROVED")))
        assertFalse(ImageGenerationRegeneratePolicy.canRegenerate(action("PENDING")))
        assertFalse(ImageGenerationRegeneratePolicy.canRegenerate(action("RUNNING", "device.set_alarm")))
    }

    /** 判据是与 canCancel 共用同一个闸，这条测试防止两边以后悄悄漂移。 */
    @Test
    fun `regenerate shares the same gate as cancel`() {
        listOf("RUNNING", "RETRYING", "FAILED", "CANCELLED", "APPROVED", "PENDING").forEach { state ->
            val subject = action(state)
            assertEquals(
                ImageGenerationCancellationPolicy.canCancel(subject),
                ImageGenerationRegeneratePolicy.canRegenerate(subject)
            )
        }
    }
}
