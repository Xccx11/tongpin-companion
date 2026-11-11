package com.aster.companion.domain

/** Shared bounds for the user-configurable conversation sampling temperature. */
object ConversationTemperature {
    const val DEFAULT = 0.75
    const val MIN = 0.0
    const val MAX = 2.0

    fun normalize(value: Double): Double = value.coerceIn(MIN, MAX)
}

object ConversationReasoning {
    const val DEFAULT_EFFORT = "medium"
    private val allowedEfforts = setOf("low", "medium", "high")

    /** 设置页可选的档位（表里存的就是这几个值；DeepSeek / OpenAI / Grok 都认 low、medium、high）。 */
    val EFFORTS = listOf("low", "medium", "high")

    fun normalizeEffort(value: String): String = value.lowercase().takeIf(allowedEfforts::contains) ?: DEFAULT_EFFORT

    fun labelFor(effort: String): String = when (normalizeEffort(effort)) {
        "low" -> "低"
        "high" -> "高"
        else -> "中"
    }
}
