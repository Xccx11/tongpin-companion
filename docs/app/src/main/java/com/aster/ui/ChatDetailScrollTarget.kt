package com.aster.ui

/** Mirrors the rows in ChatDetailPage so IME changes always reveal the newest conversation state. */
object ChatDetailScrollTarget {
    fun lastItemIndex(
        messageCount: Int,
        hasPendingImage: Boolean,
        pendingPhotoCount: Int,
        pendingDeviceCount: Int,
        isReplying: Boolean
    ): Int? {
        val rowCount =
            (if (messageCount > 0) 1 else 0) + // Date header.
            messageCount +
            (if (hasPendingImage) 1 else 0) +
            pendingPhotoCount +
            pendingDeviceCount +
            (if (isReplying) 1 else 0)
        return (rowCount - 1).takeIf { it >= 0 }
    }
}
