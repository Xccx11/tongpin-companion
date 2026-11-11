package com.aster.companion.proactive

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent idempotency markers for notification replies. Entries contain hashes only. */
@Singleton
class NotificationReplyMarkerStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun contains(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val retained = retainedEntries(now)
        persist(retained)
        return retained.any { it.substringBefore(SEPARATOR) == key }
    }

    @Synchronized
    fun markReplied(key: String, now: Long = System.currentTimeMillis()) {
        val retained = retainedEntries(now).filterNot { it.substringBefore(SEPARATOR) == key }.toMutableSet()
        retained += "$key$SEPARATOR$now"
        persist(retained)
    }

    private fun retainedEntries(now: Long): Set<String> = preferences.getStringSet(KEY_ENTRIES, emptySet()).orEmpty()
        .filter { entry -> entry.substringAfter(SEPARATOR, "").toLongOrNull()?.let { now - it < RETENTION_MS } == true }
        .toSet()

    private fun persist(entries: Set<String>) {
        preferences.edit().putStringSet(KEY_ENTRIES, entries).apply()
    }

    private companion object {
        const val PREFERENCES = "tongpin_notification_reply_markers"
        const val KEY_ENTRIES = "entries"
        const val SEPARATOR = "|"
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
