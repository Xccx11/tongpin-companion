package com.aster.companion.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlaybackPolicyTest {
    @Test
    fun `normalizes a playing track into a user owned life fact`() {
        val track = MediaPlaybackPolicy.track("  Blue  ", "  Artist  ")

        requireNotNull(track)
        assertEquals("Blue", track.title)
        assertEquals("Artist", track.artist)
        assertEquals(
            "The user is currently listening to \"Blue\" by Artist. This is the user's music, not the character's.",
            MediaPlaybackPolicy.summary(track)
        )
    }

    @Test
    fun `does not create a song fact without a title`() {
        assertNull(MediaPlaybackPolicy.track("   ", "Artist"))
    }

    @Test
    fun `song signature changes when either title or artist changes`() {
        val first = requireNotNull(MediaPlaybackPolicy.track("Blue", "Artist A"))
        val second = requireNotNull(MediaPlaybackPolicy.track("Blue", "Artist B"))

        assertFalse(MediaPlaybackPolicy.signature(first) == MediaPlaybackPolicy.signature(second))
    }
}
