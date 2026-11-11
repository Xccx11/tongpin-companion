package com.aster.companion.domain

/**
 * Pure guardrails for proactive delivery. They deliberately do not decide whether
 * a message is emotionally worthwhile; that remains the companion model's job.
 */
data class ProactiveDeliveryState(
    val dayKey: String? = null,
    val dailyDeliveryCount: Int = 0,
    val lastDeliveredAt: Long? = null,
    val lastEventKey: String? = null,
    val retryAfter: Long? = null,
    val consecutiveFailures: Int = 0
)

object ProactiveDeliveryStabilityPolicy {
    const val DELIVERY_COOLDOWN_MS = 3 * 60 * 1000L
    const val FIRST_FAILURE_BACKOFF_MS = 2 * 60 * 1000L
    const val MAX_FAILURE_BACKOFF_MS = 30 * 60 * 1000L
    const val MAX_ORDINARY_DELIVERIES_PER_DAY = 36

    fun mayAttempt(
        state: ProactiveDeliveryState,
        now: Long,
        dayKey: String,
        eventKey: String,
        urgent: Boolean
    ): Boolean {
        val today = normalizeForDay(state, dayKey)
        if (today.lastEventKey == eventKey) return false
        if (today.retryAfter?.let { now < it } == true) return false
        if (!urgent && today.dailyDeliveryCount >= MAX_ORDINARY_DELIVERIES_PER_DAY) return false
        return urgent || today.lastDeliveredAt == null || now - today.lastDeliveredAt >= DELIVERY_COOLDOWN_MS
    }

    fun recordDelivery(state: ProactiveDeliveryState, now: Long, dayKey: String, eventKey: String): ProactiveDeliveryState {
        val today = normalizeForDay(state, dayKey)
        return today.copy(
            dailyDeliveryCount = today.dailyDeliveryCount + 1,
            lastDeliveredAt = now,
            lastEventKey = eventKey,
            retryAfter = null,
            consecutiveFailures = 0
        )
    }

    /** Records a valid model decision that produced no message without consuming delivery quota. */
    fun recordDecision(state: ProactiveDeliveryState, dayKey: String, eventKey: String): ProactiveDeliveryState =
        normalizeForDay(state, dayKey).copy(
            lastEventKey = eventKey,
            retryAfter = null,
            consecutiveFailures = 0
        )

    fun recordFailure(state: ProactiveDeliveryState, now: Long, dayKey: String): ProactiveDeliveryState {
        val today = normalizeForDay(state, dayKey)
        val failureCount = today.consecutiveFailures + 1
        val multiplier = 1L shl (failureCount - 1).coerceAtMost(4)
        return today.copy(
            retryAfter = now + (FIRST_FAILURE_BACKOFF_MS * multiplier).coerceAtMost(MAX_FAILURE_BACKOFF_MS),
            consecutiveFailures = failureCount
        )
    }

    fun clearAttempt(state: ProactiveDeliveryState, dayKey: String): ProactiveDeliveryState =
        normalizeForDay(state, dayKey).copy(retryAfter = null)

    /** Events sensed while paused are intentionally discarded rather than replayed on resume. */
    fun mayResumeDeferredEvent(occurredAt: Long, resumedAt: Long, pauseStartedAt: Long): Boolean =
        occurredAt >= pauseStartedAt && occurredAt <= resumedAt

    fun normalizeForDay(state: ProactiveDeliveryState, dayKey: String): ProactiveDeliveryState =
        if (state.dayKey == dayKey) state else ProactiveDeliveryState(dayKey = dayKey)
}
