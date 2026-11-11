package com.aster.companion.proactive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

object SensingDiagnosticPolicy {
    fun shouldWrite(previous: String?, current: String, lastWrittenAt: Long, now: Long): Boolean =
        previous != current || now - lastWrittenAt >= HEARTBEAT_INTERVAL_MS

    const val HEARTBEAT_INTERVAL_MS = 10 * 60 * 1000L
}

data class SensingSourceStatus(
    val source: String,
    val state: String,
    val observedAt: Long
)

/** Private diagnostic state for device verification. Never stores source payloads or identifiers. */
@Singleton
class SensingDiagnosticStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun record(source: String, state: String, now: Long) {
        val stateKey = "$source.state"
        val timeKey = "$source.time"
        val previous = preferences.getString(stateKey, null)
        val lastWrittenAt = preferences.getLong(timeKey, 0L)
        if (!SensingDiagnosticPolicy.shouldWrite(previous, state, lastWrittenAt, now)) return
        preferences.edit().putString(stateKey, state).putLong(timeKey, now).apply()
        _revision.value += 1
    }

    fun statuses(): List<SensingSourceStatus> = KNOWN_SOURCES.map { source ->
        SensingSourceStatus(
            source = source,
            state = preferences.getString("$source.state", "not_observed").orEmpty(),
            observedAt = preferences.getLong("$source.time", 0L)
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "tongpin_sensing_status"
        val KNOWN_SOURCES = listOf("place", "weather", "battery", "network", "calendar", "foreground", "screen", "media")
    }
}
