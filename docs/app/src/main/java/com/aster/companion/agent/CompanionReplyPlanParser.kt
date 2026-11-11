package com.aster.companion.agent

import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.AgentDecisionKind

data class CompanionReplyPlan(val message: String?, val actions: List<AgentActionRequest>)

/** Keeps normal chat as text while allowing the model to attach a bounded action plan when needed. */
object CompanionReplyPlanParser {
    fun parse(raw: String): CompanionReplyPlan? = embeddedPlan(raw)
        ?.let { plan -> runCatching { AgentDecisionParser.parse(plan) }.getOrNull() }
        ?.takeIf { it.kind == AgentDecisionKind.TOOL_CALLS }
        ?.let { CompanionReplyPlan(it.message, it.actions) }

    /** A model protocol is never a visible chat message, even if its JSON is malformed. */
    fun visibleReply(raw: String): String {
        val protocol = embeddedPlan(raw) ?: return raw
        return parse(protocol)?.message ?: raw.substringBefore(protocol).trim()
    }

    private fun embeddedPlan(raw: String): String? {
        if (!raw.contains("\"decision\"") || !raw.contains("\"tool_calls\"")) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
    }
}
