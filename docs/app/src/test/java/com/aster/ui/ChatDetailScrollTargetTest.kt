package com.aster.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDetailScrollTargetTest {
    @Test
    fun `targets the newest message after the date header`() {
        assertEquals(
            3,
            ChatDetailScrollTarget.lastItemIndex(
                messageCount = 3,
                hasPendingImage = false,
                pendingPhotoCount = 0,
                pendingDeviceCount = 0,
                isReplying = false
            )
        )
    }

    @Test
    fun `targets the actual final row when typing or consent cards follow messages`() {
        assertEquals(
            6,
            ChatDetailScrollTarget.lastItemIndex(
                messageCount = 2,
                hasPendingImage = true,
                pendingPhotoCount = 1,
                pendingDeviceCount = 1,
                isReplying = true
            )
        )
    }

    @Test
    fun `returns no target for an empty conversation without transient rows`() {
        assertEquals(
            null,
            ChatDetailScrollTarget.lastItemIndex(
                messageCount = 0,
                hasPendingImage = false,
                pendingPhotoCount = 0,
                pendingDeviceCount = 0,
                isReplying = false
            )
        )
    }
}
