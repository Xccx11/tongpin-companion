package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveDeliveryStabilityPolicyTest {
    @Test
    fun `delivered event is not delivered again after process restart`() {
        val delivered = ProactiveDeliveryStabilityPolicy.recordDelivery(
            state = ProactiveDeliveryState(),
            now = 1_000_000L,
            dayKey = "2026-07-22",
            eventKey = "courier-123"
        )

        assertFalse(
            ProactiveDeliveryStabilityPolicy.mayAttempt(
                state = delivered,
                now = 1_001_000L,
                dayKey = "2026-07-22",
                eventKey = "courier-123",
                urgent = true
            )
        )
    }

    @Test
    fun `ordinary delivery respects persisted three minute cooldown`() {
        val delivered = ProactiveDeliveryStabilityPolicy.recordDelivery(
            state = ProactiveDeliveryState(),
            now = 1_000_000L,
            dayKey = "2026-07-22",
            eventKey = "battery-1"
        )

        assertFalse(ProactiveDeliveryStabilityPolicy.mayAttempt(delivered, 1_179_999L, "2026-07-22", "weather-1", urgent = false))
        assertTrue(ProactiveDeliveryStabilityPolicy.mayAttempt(delivered, 1_180_000L, "2026-07-22", "weather-1", urgent = false))
    }

    @Test
    fun `a silent model decision is not retried for the same event bucket`() {
        val decided = ProactiveDeliveryStabilityPolicy.recordDecision(
            state = ProactiveDeliveryState(),
            dayKey = "2026-07-22",
            eventKey = "group-presence:bucket-1"
        )

        assertFalse(
            ProactiveDeliveryStabilityPolicy.mayAttempt(
                decided,
                now = 1_000_001L,
                dayKey = "2026-07-22",
                eventKey = "group-presence:bucket-1",
                urgent = false
            )
        )
        assertTrue(
            ProactiveDeliveryStabilityPolicy.mayAttempt(
                decided,
                now = 1_000_001L,
                dayKey = "2026-07-22",
                eventKey = "group-presence:bucket-2",
                urgent = false
            )
        )
    }

    @Test
    fun `model failure backs off instead of retrying every service poll`() {
        val failed = ProactiveDeliveryStabilityPolicy.recordFailure(
            state = ProactiveDeliveryState(),
            now = 1_000_000L,
            dayKey = "2026-07-22"
        )

        assertEquals(1_120_000L, failed.retryAfter)
        assertFalse(ProactiveDeliveryStabilityPolicy.mayAttempt(failed, 1_030_000L, "2026-07-22", "weather-1", urgent = false))
        assertTrue(ProactiveDeliveryStabilityPolicy.mayAttempt(failed, 1_120_000L, "2026-07-22", "weather-1", urgent = false))
    }

    @Test
    fun `paused interval discards event rather than releasing it on resume`() {
        assertFalse(
            ProactiveDeliveryStabilityPolicy.mayResumeDeferredEvent(
                occurredAt = 1_000_000L,
                resumedAt = 1_180_001L,
                pauseStartedAt = 1_010_000L
            )
        )
        assertTrue(
            ProactiveDeliveryStabilityPolicy.mayResumeDeferredEvent(
                occurredAt = 1_200_000L,
                resumedAt = 1_210_000L,
                pauseStartedAt = 1_010_000L
            )
        )
    }
}
