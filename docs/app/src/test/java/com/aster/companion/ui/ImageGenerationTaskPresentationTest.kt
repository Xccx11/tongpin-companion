package com.aster.companion.ui

import com.aster.companion.data.PendingActionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ImageGenerationTaskPresentationTest {
    @Test
    fun persistedMomentTaskWinsOverCompatibilityFallbackWithTheSameId() {
        val persisted = action("moment-image:post-1", "RUNNING", "真实任务")
        val fallback = action("moment-image:post-1", "APPROVED", "兼容展示")

        val merged = ImageGenerationTaskPresentation.merge(listOf(persisted), listOf(fallback))

        assertEquals(1, merged.size)
        assertSame(persisted, merged.single())
    }

    @Test
    fun compatibilityFallbackIsKeptWhenNoPersistedTaskExists() {
        val fallback = action("moment-image:post-2", "FAILED", "兼容展示")

        val merged = ImageGenerationTaskPresentation.merge(emptyList(), listOf(fallback))

        assertEquals(listOf(fallback), merged)
    }

    private fun action(id: String, state: String, summary: String) = PendingActionEntity(
        id = id,
        eventId = null,
        threadId = null,
        capabilityId = "companion.generate_moment_image",
        transport = "LOCAL",
        argumentsJson = "{}",
        state = state,
        createdAt = 1L,
        resolvedAt = null,
        resultSummary = summary
    )
}
