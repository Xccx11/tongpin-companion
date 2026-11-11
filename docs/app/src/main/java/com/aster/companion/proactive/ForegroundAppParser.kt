package com.aster.companion.proactive

/** Extracts only the package token from Android's top resumed activity record. */
object ForegroundAppParser {
    private val topResumedActivity = Regex(
        """topResumedActivity=ActivityRecord\{[^\n]*?\bu\d+\s+([A-Za-z][A-Za-z0-9_.]+)(?:/|\s)"""
    )

    fun parse(dumpsysOutput: String): String? =
        topResumedActivity.find(dumpsysOutput)?.groupValues?.getOrNull(1)
}
