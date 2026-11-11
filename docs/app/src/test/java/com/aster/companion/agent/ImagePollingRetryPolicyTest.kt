package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePollingRetryPolicyTest {
    @Test
    fun `transient gateway responses keep the durable task alive`() {
        assertTrue(ImagePollingRetryPolicy.isTransientHttpStatus(408))
        assertTrue(ImagePollingRetryPolicy.isTransientHttpStatus(429))
        assertTrue(ImagePollingRetryPolicy.isTransientHttpStatus(500))
        assertTrue(ImagePollingRetryPolicy.isTransientHttpStatus(504))
        assertFalse(ImagePollingRetryPolicy.isTransientHttpStatus(400))
        assertFalse(ImagePollingRetryPolicy.isTransientHttpStatus(404))
    }

    @Test
    fun `poll retry backoff stays bounded and never becomes a stop condition`() {
        assertEquals(500L, ImagePollingRetryPolicy.retryDelayMillis(0))
        assertEquals(5_000L, ImagePollingRetryPolicy.retryDelayMillis(99))
        assertTrue(ImagePollingRetryPolicy.shouldKeepTracking(ComfyGatewayTaskStatus.Queued))
        assertTrue(ImagePollingRetryPolicy.shouldKeepTracking(ComfyGatewayTaskStatus.Running))
    }
}
