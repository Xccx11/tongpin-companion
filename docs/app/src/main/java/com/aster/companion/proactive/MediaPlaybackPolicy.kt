package com.aster.companion.proactive

data class MediaTrack(val title: String, val artist: String?)

/** Pure normalization so player-specific metadata never leaks into relationship logic. */
object MediaPlaybackPolicy {
    fun track(title: String?, artist: String?): MediaTrack? {
        val normalizedTitle = title?.trim().orEmpty()
        if (normalizedTitle.isBlank()) return null
        return MediaTrack(normalizedTitle, artist?.trim()?.takeIf(String::isNotBlank))
    }

    fun signature(track: MediaTrack): String = "${track.title}\u001f${track.artist.orEmpty()}"

    fun summary(track: MediaTrack): String = buildString {
        append("The user is currently listening to \"${track.title}\"")
        track.artist?.let { append(" by $it") }
        append(". This is the user's music, not the character's.")
    }
}
