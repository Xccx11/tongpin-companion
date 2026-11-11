package com.aster.companion.agent

object RoleplayResponseSanitizer {
    fun clean(raw: String): String {
        val text = raw.trim()
            .replace(Regex("^[.\\u2026]{2,}\\s*"), "")
            .trim()
        if (text.isEmpty() || text.equals("null", ignoreCase = true)) return ""
        // Keep real newlines: the chat UI splits multi-line replies into bubbles.
        // Only drop the leaked literal token "null" as a whole word.
        val cleanedLines = text.lines().map { line ->
            line.replace(Regex("(?<!\\S)null(?!\\S)", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
        }.filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return cleanedLines.joinToString("\n").trim()
    }
}
