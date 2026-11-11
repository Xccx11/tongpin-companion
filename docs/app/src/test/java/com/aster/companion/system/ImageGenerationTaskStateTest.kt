package com.aster.companion.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGenerationTaskStateTest {
    @Test
    fun `approved image request enters durable running state before execution`() {
        assertEquals(ImageGenerationTaskState.RUNNING, ImageGenerationTaskState.afterApproval())
    }

    @Test
    fun `temporary provider failure keeps the action retryable`() {
        assertEquals(ImageGenerationTaskState.RETRYING, ImageGenerationTaskState.afterTransientFailure())
    }
}
