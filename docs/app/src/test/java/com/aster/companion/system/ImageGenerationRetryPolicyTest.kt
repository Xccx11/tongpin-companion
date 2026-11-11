package com.aster.companion.system

import com.aster.companion.data.PendingActionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationRetryPolicyTest {
    @Test
    fun `allows a failed private photo proposal to retry`() {
        val action = PendingActionEntity(
            id = "private-photo",
            eventId = null,
            threadId = "private-thread",
            capabilityId = "companion.propose_private_image",
            transport = "LOCAL",
            argumentsJson = "{}",
            state = "FAILED",
            createdAt = 1L,
            resolvedAt = 2L,
            resultSummary = "temporary network failure"
        )

        assertTrue(ImageGenerationRetryPolicy.canRetry(action))
    }
    @Test
    fun `failed image task can retry without stale provider task identifiers`() {
        val action = PendingActionEntity(
            id = "image-1",
            eventId = null,
            threadId = "thread-1",
            capabilityId = "companion.generate_image",
            transport = "APP",
            argumentsJson = "{}",
            state = "FAILED",
            createdAt = 1L,
            resolvedAt = 2L,
            resultSummary = "timeout"
        )

        assertTrue(ImageGenerationRetryPolicy.canRetry(action))
        assertEquals(
            mapOf(
                "character_id" to "lan",
                "scene" to "rainy window",
                ImageGenerationRetryPolicy.RENDERED_PROMPT_KEY to "the same final prompt"
            ),
            ImageGenerationRetryPolicy.cleanArgumentsForRetry(
                mapOf(
                    "character_id" to "lan",
                    "scene" to "rainy window",
                    ImageGenerationRetryPolicy.RENDERED_PROMPT_KEY to "the same final prompt",
                    "provider_task_id" to "old-task",
                    "provider_task_id:lan" to "old-slot-task"
                )
            )
        )
        assertFalse(ImageGenerationRetryPolicy.canRetry(action.copy(state = "RUNNING")))
    }
}
