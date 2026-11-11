package com.aster.companion.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingImagePreviewPolicyTest {
    @Test
    fun `keeps local preview visible until the stored chat message contains its uri`() {
        val uri = "content://media/picked-image"

        assertTrue(PendingImagePreviewPolicy.shouldShow(uri, emptySet()))
        assertFalse(PendingImagePreviewPolicy.shouldShow(uri, setOf(uri)))
    }
}
