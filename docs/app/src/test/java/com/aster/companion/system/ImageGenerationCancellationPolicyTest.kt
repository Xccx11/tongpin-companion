package com.aster.companion.system

import com.aster.companion.data.PendingActionEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationCancellationPolicyTest {
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
    fun `only running image work may be cancelled`() {
        assertTrue(ImageGenerationCancellationPolicy.canCancel(action("RUNNING")))
        assertTrue(ImageGenerationCancellationPolicy.canCancel(action("RETRYING")))
        assertTrue(ImageGenerationCancellationPolicy.canCancel(action("RUNNING", "companion.generate_moment_image")))
        assertFalse(ImageGenerationCancellationPolicy.canCancel(action("PENDING")))
        assertFalse(ImageGenerationCancellationPolicy.canCancel(action("FAILED")))
        assertFalse(ImageGenerationCancellationPolicy.canCancel(action("RUNNING", "device.set_alarm")))
    }

    @Test
    fun `cancelled image work is not resumable`() {
        assertFalse(ImageGenerationCancellationPolicy.shouldResume("CANCELLED"))
        assertTrue(ImageGenerationCancellationPolicy.shouldResume("RUNNING"))
        assertTrue(ImageGenerationCancellationPolicy.shouldResume("RETRYING"))
    }

    @Test
    fun `only a moment image cancellation needs the feed post state updated`() {
        assertTrue(ImageGenerationCancellationPolicy.shouldMarkMomentPostCancelled("companion.generate_moment_image"))
        assertFalse(ImageGenerationCancellationPolicy.shouldMarkMomentPostCancelled("companion.generate_image"))
    }
}
