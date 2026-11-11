package com.aster.companion.system

import com.aster.companion.data.PendingActionEntity

/** A cancelled image action is terminal and must never be revived by recovery. */
object ImageGenerationCancellationPolicy {
    private val imageCapabilities = setOf(
        "companion.generate_image",
        "companion.propose_image",
        "companion.share_life_moment",
        "companion.propose_private_image",
        "companion.generate_moment_image",
        "companion.peek_image"
    )

    fun canCancel(action: PendingActionEntity): Boolean =
        action.state in setOf(ImageGenerationTaskState.RUNNING, ImageGenerationTaskState.RETRYING) &&
            action.capabilityId in imageCapabilities

    fun shouldResume(state: String): Boolean =
        state == ImageGenerationTaskState.RUNNING || state == ImageGenerationTaskState.RETRYING

    fun shouldMarkMomentPostCancelled(capabilityId: String): Boolean =
        capabilityId == "companion.generate_moment_image"
}
