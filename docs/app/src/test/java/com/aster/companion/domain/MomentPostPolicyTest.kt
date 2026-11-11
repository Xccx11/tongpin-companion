package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.aster.companion.data.MomentPostEntity

class MomentPostPolicyTest {
    @Test
    fun `user post needs text or at least one image and limits media`() {
        assertEquals("empty_post", MomentPostPolicy.errorFor("", emptyList()))
        assertNull(MomentPostPolicy.errorFor("", listOf("content://one")))
        assertEquals("too_many_images", MomentPostPolicy.errorFor("hello", List(10) { "content://$it" }))
    }

    @Test
    fun `role post keeps caption bounded and requires a character`() {
        assertEquals("missing_character", MomentPostPolicy.errorForRole(null, "hello", emptyList()))
        assertEquals("empty_post", MomentPostPolicy.errorForRole("character", "", emptyList()))
        assertNull(MomentPostPolicy.errorForRole("character", "hello", listOf("content://image")))
    }

    @Test
    fun `failed generated post does not consume the proactive cooldown`() {
        val failed = MomentPostEntity("post", "CHARACTER", "c", "caption", "ALL", "PROACTIVE", "FAILED", 1_000L, 1_000L)
        val published = failed.copy(status = "PUBLISHED")

        assertFalse(MomentPostPolicy.isProactiveCooldownActive(failed, 1_001L, 10_000L))
        assertTrue(MomentPostPolicy.isProactiveCooldownActive(published, 1_001L, 10_000L))
        assertFalse(MomentPostPolicy.isProactiveCooldownActive(published, 20_000L, 10_000L))
    }

    @Test
    fun `proactive photo waits for a quiet window and a free image lane`() {
        val now = 1_000_000L

        assertFalse(MomentPostPolicy.canStartProactivePhoto(now - 30_000L, imageLaneBusy = false, now = now))
        assertFalse(MomentPostPolicy.canStartProactivePhoto(now - 5 * 60_000L, imageLaneBusy = true, now = now))
        assertTrue(MomentPostPolicy.canStartProactivePhoto(now - 5 * 60_000L, imageLaneBusy = false, now = now))
    }

    @Test
    fun `proactive photo can be queued while another image is running`() {
        val now = 1_000_000L

        assertTrue(MomentPostPolicy.canQueueProactivePhoto(now - 5 * 60_000L, now))
        assertFalse(MomentPostPolicy.canQueueProactivePhoto(now - 30_000L, now))
    }

    @Test
    fun `proactive post requires a traceable grounding source`() {
        assertFalse(MomentPostPolicy.hasTraceableGrounding(null, setOf("message-1", "event-1")))
        assertFalse(MomentPostPolicy.hasTraceableGrounding("made-up", setOf("message-1", "event-1")))
        assertTrue(MomentPostPolicy.hasTraceableGrounding("event-1", setOf("message-1", "event-1")))
    }
}
