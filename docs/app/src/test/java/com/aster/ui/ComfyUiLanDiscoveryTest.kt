package com.aster.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ComfyUiLanDiscoveryTest {
    @Test
    fun `checks the default ComfyUI port before compatible fallback ports`() {
        assertEquals(listOf(8188, 8189, 8190, 8191, 8192), ComfyUiLanDiscovery.candidatePorts())
    }
}
