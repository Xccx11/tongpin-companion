package com.aster.companion.agent

import com.aster.companion.proactive.LifeSnapshotFact

/** Converts live sensing into a small group-safe background context. */
object GroupProactiveLifeContextPolicy {
    fun lines(facts: List<LifeSnapshotFact>): List<String> = facts.mapNotNull { fact ->
        val summary = fact.summary.trim()
        if (summary.isBlank()) return@mapNotNull null
        when {
            fact.source == "weather" -> "[live weather] ${summary.take(MAX_SUMMARY)}"
            fact.source == "device:media" -> "[currently playing media] ${summary.take(MAX_SUMMARY)}"
            fact.source == "device:place" -> "[approximate current area] ${summary.substringBefore('(').trim().take(MAX_SUMMARY)}"
            fact.source == "device:foreground_app" -> "[foreground app category] ${foregroundCategory(summary).take(MAX_SUMMARY)}"
            fact.source == "calendar" -> "[current schedule signal] ${summary.take(MAX_SUMMARY)}"
            else -> null
        }
    }

    private fun foregroundCategory(summary: String): String =
        summary.substringBefore('(').substringAfter(':').trim().ifBlank { "an app is in the foreground" }

    private const val MAX_SUMMARY = 240
}
