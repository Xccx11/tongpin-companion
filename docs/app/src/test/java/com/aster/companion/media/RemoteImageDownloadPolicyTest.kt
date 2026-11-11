package com.aster.companion.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteImageDownloadPolicyTest {
    @Test
    fun `download failures retry a bounded number of times before returning to durable recovery`() {
        assertTrue(RemoteImageDownloadPolicy.shouldRetry(1))
        assertTrue(RemoteImageDownloadPolicy.shouldRetry(RemoteImageDownloadPolicy.MAX_ATTEMPTS - 1))
        assertFalse(RemoteImageDownloadPolicy.shouldRetry(RemoteImageDownloadPolicy.MAX_ATTEMPTS))
        assertEquals(500L, RemoteImageDownloadPolicy.delayMillis(1))
    }
}
