package com.aster.companion.proactive

data class LifeSnapshotInput(
    val source: String,
    val summary: String,
    val observedAt: Long
)

data class LifeSnapshotFact(
    val source: String,
    val summary: String,
    val observedAt: Long
)

/** Projects raw event history into current, trustworthy life facts for one model turn. */
object LifeSnapshotPolicy {
    fun currentFacts(inputs: List<LifeSnapshotInput>, now: Long): List<LifeSnapshotFact> =
        inputs
            .asSequence()
            .filter { input ->
                input.summary.isNotBlank() &&
                    input.summary != REDACTED_EVENT_CONTENT &&
                    input.observedAt <= now &&
                    now - input.observedAt <= maxAgeFor(input.source)
            }
            .sortedByDescending(LifeSnapshotInput::observedAt)
            .distinctBy(::categoryFor)
            .map { input -> LifeSnapshotFact(input.source, input.summary, input.observedAt) }
            .toList()

    fun promptLines(facts: List<LifeSnapshotFact>): List<String> =
        facts.map { fact -> "source=${fact.source}; observed_at=${fact.observedAt}; fact=${fact.summary}" }

    private fun categoryFor(input: LifeSnapshotInput): String = when {
        input.source.startsWith("notification:") -> "notification"
        input.source == "sms" -> "sms"
        else -> input.source
    }

    private fun maxAgeFor(source: String): Long = when {
        source.startsWith("notification:") -> NOTIFICATION_MAX_AGE_MS
        source == "sms" -> SMS_MAX_AGE_MS
        source == "device:foreground_app" -> FOREGROUND_APP_MAX_AGE_MS
        source == "device:place" -> PLACE_MAX_AGE_MS
        source == "device:screen" -> SCREEN_MAX_AGE_MS
        source == "device:media" -> MEDIA_MAX_AGE_MS
        source == "device:battery" -> BATTERY_MAX_AGE_MS
        source == "device:network" -> NETWORK_MAX_AGE_MS
        source == "weather" -> WEATHER_MAX_AGE_MS
        source == "calendar" -> CALENDAR_MAX_AGE_MS
        else -> -1L
    }

    private const val REDACTED_EVENT_CONTENT = "Event content discarded after context window"
    // A notification that is no longer fresh must never color a later relationship pulse.
    private const val NOTIFICATION_MAX_AGE_MS = 3 * 60 * 1000L
    private const val SMS_MAX_AGE_MS = 10 * 60 * 1000L
    private const val FOREGROUND_APP_MAX_AGE_MS = 15 * 60 * 1000L
    private const val PLACE_MAX_AGE_MS = 45 * 60 * 1000L
    private const val SCREEN_MAX_AGE_MS = 15 * 60 * 1000L
    private const val MEDIA_MAX_AGE_MS = 15 * 60 * 1000L
    private const val BATTERY_MAX_AGE_MS = 30 * 60 * 1000L
    private const val NETWORK_MAX_AGE_MS = 20 * 60 * 1000L
    private const val WEATHER_MAX_AGE_MS = 90 * 60 * 1000L
    private const val CALENDAR_MAX_AGE_MS = 2 * 60 * 60 * 1000L
}
