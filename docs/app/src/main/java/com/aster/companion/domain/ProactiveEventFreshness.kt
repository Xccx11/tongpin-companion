package com.aster.companion.domain

object ProactiveEventFreshness {
    fun mayEnterProactivePlanning(source: String, occurredAt: Long, now: Long): Boolean =
        !source.startsWith("notification:") ||
            occurredAt <= now && now - occurredAt < NOTIFICATION_MAX_AGE_MS

    private const val NOTIFICATION_MAX_AGE_MS = 3 * 60 * 1000L
}
