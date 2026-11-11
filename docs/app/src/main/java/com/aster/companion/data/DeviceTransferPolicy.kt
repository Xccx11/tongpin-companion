package com.aster.companion.data

import com.aster.companion.domain.EventState

/** Prevents a new device from repeating real-world work that belonged to the old device. */
object DeviceTransferPolicy {
    const val REVIEW_REQUIRED = "MIGRATED_REVIEW_REQUIRED"

    fun importEventState(@Suppress("UNUSED_PARAMETER") state: EventState): EventState = EventState.CONSUMED

    fun importActionState(state: String): String = when (state) {
        "COMPLETED", "FAILED", "REJECTED", "CANCELLED" -> state
        else -> REVIEW_REQUIRED
    }
}
