package com.aster.companion.media

/** Bounded per-download retry; the durable image action retries again if the CDN stays down. */
internal object RemoteImageDownloadPolicy {
    const val MAX_ATTEMPTS = 3

    fun shouldRetry(attempt: Int): Boolean = attempt < MAX_ATTEMPTS

    fun delayMillis(attempt: Int): Long = when (attempt) {
        1 -> 500L
        2 -> 1_500L
        else -> 2_500L
    }
}
