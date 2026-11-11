package com.aster.companion.domain

object UpgradeSnapshotPolicy {
    fun isDue(lastCreatedAt: Long?, now: Long): Boolean =
        lastCreatedAt == null || now - lastCreatedAt >= REFRESH_INTERVAL_MS

    private const val REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L
}
