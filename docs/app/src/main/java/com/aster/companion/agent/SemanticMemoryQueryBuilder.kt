package com.aster.companion.agent

import com.aster.companion.proactive.LifeSnapshotFact

/** Builds an embedding query from the full live turn, never only the user's final sentence. */
object SemanticMemoryQueryBuilder {
    fun build(
        recentConversation: List<String>,
        triggerSummary: String,
        lifeContext: List<LifeSnapshotFact>
    ): String = buildString {
        appendLine("Current companion turn")
        appendLine("Trigger: $triggerSummary")
        if (lifeContext.isNotEmpty()) {
            appendLine("Current verified life context:")
            lifeContext.forEach { appendLine("${it.source}: ${it.summary}") }
        }
        if (recentConversation.isNotEmpty()) {
            appendLine("Recent complete conversation:")
            recentConversation.forEach(::appendLine)
        }
    }.take(12_000)
}
