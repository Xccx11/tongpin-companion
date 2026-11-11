package com.aster.companion.domain

import com.aster.companion.data.MomentPostEntity

/** Shared validation for user-created and character-created moments. */
object MomentPostPolicy {
    const val MAX_IMAGES = 9
    const val MAX_BODY_LENGTH = 2_000
    const val MAX_CHARACTER_BODY_LENGTH = 1_000

    fun errorFor(body: String, imageUris: List<String>): String? {
        if (body.isBlank() && imageUris.isEmpty()) return "empty_post"
        if (body.length > MAX_BODY_LENGTH) return "body_too_long"
        if (imageUris.size > MAX_IMAGES) return "too_many_images"
        if (imageUris.any { it.isBlank() }) return "invalid_image"
        return null
    }

    fun errorForRole(characterId: String?, body: String, imageUris: List<String>): String? {
        if (characterId.isNullOrBlank()) return "missing_character"
        if (body.length > MAX_CHARACTER_BODY_LENGTH) return "body_too_long"
        return errorFor(body, imageUris)
    }

    fun isProactiveCooldownActive(post: MomentPostEntity?, now: Long, cooldownMs: Long): Boolean {
        if (post == null || post.status == MomentActors.STATUS_FAILED) return false
        return now - post.createdAt < cooldownMs
    }

    /** A feed photo is background decoration, never a reason to occupy the conversation image lane. */
    fun canStartProactivePhoto(lastMessageAt: Long?, imageLaneBusy: Boolean, now: Long): Boolean {
        if (imageLaneBusy) return false
        return lastMessageAt == null || now - lastMessageAt >= PROACTIVE_PHOTO_QUIET_WINDOW_MS
    }

    /** The global image queue owns contention; a feed request may wait behind an older image. */
    fun canQueueProactivePhoto(lastMessageAt: Long?, now: Long): Boolean =
        lastMessageAt == null || now - lastMessageAt >= PROACTIVE_PHOTO_QUIET_WINDOW_MS

    /** The planner must point at one supplied fact; otherwise a proactive post is discarded. */
    fun hasTraceableGrounding(groundingId: String?, availableIds: Set<String>): Boolean =
        !groundingId.isNullOrBlank() && groundingId in availableIds

    private const val PROACTIVE_PHOTO_QUIET_WINDOW_MS = 3 * 60_000L
}
