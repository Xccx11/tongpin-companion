package com.aster.companion.proactive

import android.content.Context
import com.aster.companion.domain.ProactiveDeliveryStabilityPolicy
import com.aster.companion.domain.ProactiveDeliveryState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small, durable delivery ledger. It contains character ids and hashes only, never
 * notification text, conversation text, API credentials, or other raw life data.
 */
@Singleton
class ProactiveDeliveryStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun begin(characterId: String, eventKey: String, now: Long, urgent: Boolean): ProactiveAttemptResult {
        val dayKey = dayKey(now)
        val state = ProactiveDeliveryStabilityPolicy.normalizeForDay(stateFor(characterId), dayKey)
        if (handled(characterId, eventKey, now) || state.lastEventKey == eventKey) return ProactiveAttemptResult.AlreadyDelivered
        if (!ProactiveDeliveryStabilityPolicy.mayAttempt(state, now, dayKey, eventKey, urgent)) {
            val retryAt = state.retryAfter
            if (retryAt != null && retryAt > now) return ProactiveAttemptResult.WaitUntil(retryAt)
            if (!urgent && state.dailyDeliveryCount >= ProactiveDeliveryStabilityPolicy.MAX_ORDINARY_DELIVERIES_PER_DAY) {
                return ProactiveAttemptResult.Suppressed
            }
            val cooldownAt = state.lastDeliveredAt?.plus(ProactiveDeliveryStabilityPolicy.DELIVERY_COOLDOWN_MS)
            if (!urgent && cooldownAt != null && cooldownAt > now) return ProactiveAttemptResult.WaitUntil(cooldownAt)
            return ProactiveAttemptResult.Suppressed
        }
        writeState(characterId, state.copy(retryAfter = now + ATTEMPT_LEASE_MS))
        return ProactiveAttemptResult.Started
    }

    @Synchronized
    fun recordDelivery(characterId: String, eventKey: String, now: Long) {
        val state = ProactiveDeliveryStabilityPolicy.recordDelivery(stateFor(characterId), now, dayKey(now), eventKey)
        writeState(characterId, state)
        val entries = retainedMarkers(now).filterNot { it.startsWith("$characterId$SEPARATOR$eventKey$SEPARATOR") }.toMutableSet()
        entries += "$characterId$SEPARATOR$eventKey$SEPARATOR$now"
        preferences.edit().putStringSet(KEY_MARKERS, entries).commit()
    }

    @Synchronized
    fun recordDecision(characterId: String, eventKey: String, now: Long) {
        writeState(
            characterId,
            ProactiveDeliveryStabilityPolicy.recordDecision(stateFor(characterId), dayKey(now), eventKey)
        )
    }

    @Synchronized
    fun recordFailure(characterId: String, now: Long): Long {
        val state = ProactiveDeliveryStabilityPolicy.recordFailure(stateFor(characterId), now, dayKey(now))
        writeState(characterId, state)
        return requireNotNull(state.retryAfter)
    }

    @Synchronized
    fun clearAttempt(characterId: String, now: Long) {
        writeState(characterId, ProactiveDeliveryStabilityPolicy.clearAttempt(stateFor(characterId), dayKey(now)))
    }

    fun eventKey(source: String, summary: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$source\n$summary".toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun stateFor(characterId: String): ProactiveDeliveryState {
        val fields = preferences.getString("$KEY_STATE_PREFIX$characterId", null)?.split(SEPARATOR).orEmpty()
        return ProactiveDeliveryState(
            dayKey = fields.getOrNull(0)?.takeIf(String::isNotBlank),
            dailyDeliveryCount = fields.getOrNull(1)?.toIntOrNull() ?: 0,
            lastDeliveredAt = fields.getOrNull(2)?.toLongOrNull(),
            lastEventKey = fields.getOrNull(3)?.takeIf(String::isNotBlank),
            retryAfter = fields.getOrNull(4)?.toLongOrNull(),
            consecutiveFailures = fields.getOrNull(5)?.toIntOrNull() ?: 0
        )
    }

    private fun writeState(characterId: String, state: ProactiveDeliveryState) {
        val value = listOf(
            state.dayKey.orEmpty(), state.dailyDeliveryCount, state.lastDeliveredAt ?: "",
            state.lastEventKey.orEmpty(), state.retryAfter ?: "", state.consecutiveFailures
        ).joinToString(SEPARATOR)
        preferences.edit().putString("$KEY_STATE_PREFIX$characterId", value).commit()
    }

    private fun handled(characterId: String, eventKey: String, now: Long): Boolean {
        val retained = retainedMarkers(now)
        preferences.edit().putStringSet(KEY_MARKERS, retained).commit()
        return retained.any { it.startsWith("$characterId$SEPARATOR$eventKey$SEPARATOR") }
    }

    private fun retainedMarkers(now: Long): Set<String> = preferences.getStringSet(KEY_MARKERS, emptySet()).orEmpty()
        .filter { entry -> entry.substringAfterLast(SEPARATOR, "").toLongOrNull()?.let { now - it < MARKER_RETENTION_MS } == true }
        .toSet()

    private fun dayKey(now: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    private companion object {
        const val PREFERENCES = "tongpin_proactive_delivery_state"
        const val KEY_MARKERS = "delivered_markers"
        const val KEY_STATE_PREFIX = "character_state:"
        const val SEPARATOR = "\u001f"
        const val ATTEMPT_LEASE_MS = 5 * 60 * 1000L
        const val MARKER_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

sealed interface ProactiveAttemptResult {
    data object Started : ProactiveAttemptResult
    data object AlreadyDelivered : ProactiveAttemptResult
    data object Suppressed : ProactiveAttemptResult
    data class WaitUntil(val at: Long) : ProactiveAttemptResult
}
