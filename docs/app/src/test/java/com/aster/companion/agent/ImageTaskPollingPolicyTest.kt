package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTaskPollingPolicyTest {
    @Test
    fun `queued and running provider tasks continue polling without a local deadline`() {
        assertTrue(ImageTaskPollingPolicy.shouldContinue(ComfyGatewayTaskStatus.Queued))
        assertTrue(ImageTaskPollingPolicy.shouldContinue(ComfyGatewayTaskStatus.Running))
    }

    @Test
    fun `only a terminal provider state stops polling`() {
        assertFalse(ImageTaskPollingPolicy.shouldContinue(ComfyGatewayTaskStatus.Succeeded))
        assertFalse(ImageTaskPollingPolicy.shouldContinue(ComfyGatewayTaskStatus.Failed))
    }
}
