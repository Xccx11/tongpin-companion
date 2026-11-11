package com.aster.companion.agent

import com.aster.companion.domain.ActionTransport
import com.aster.companion.domain.AgentActionRequest
import com.aster.companion.domain.AgentDecision
import com.aster.companion.domain.AgentDecisionKind
import com.aster.companion.domain.ConfirmationRequirement
import com.aster.companion.domain.ConversationType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject

/** Parses the single, bounded decision format used for proactive agent turns. */
object AgentDecisionParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): AgentDecision {
        val root = json.parseToJsonElement(extractJsonObject(raw)).jsonObject
        val kind = when (root.requiredString("decision").lowercase()) {
            "send_now" -> AgentDecisionKind.SEND_NOW
            "defer" -> AgentDecisionKind.DEFER
            "ignore" -> AgentDecisionKind.IGNORE
            "tool_calls" -> AgentDecisionKind.TOOL_CALLS
            else -> throw IllegalArgumentException("Unknown agent decision")
        }
        val message = root.optionalString("message")?.trim()?.takeIf(String::isNotEmpty)
        val deferUntil = root["defer_until"]?.jsonPrimitive?.longOrNull
        val actions = root["tool_calls"]?.jsonArray?.map(::parseAction).orEmpty()

        when (kind) {
            AgentDecisionKind.SEND_NOW -> require(!message.isNullOrBlank()) { "send_now requires message" }
            AgentDecisionKind.DEFER -> require(deferUntil != null) { "defer requires defer_until" }
            AgentDecisionKind.TOOL_CALLS -> {
                require(actions.isNotEmpty()) { "tool_calls requires tool_calls" }
            }
            AgentDecisionKind.IGNORE -> Unit
        }
        return AgentDecision(kind, ConversationType.PRIVATE, message, deferUntil, actions)
    }

    private fun parseAction(element: kotlinx.serialization.json.JsonElement): AgentActionRequest {
        val action = element.jsonObject
        val transport = when (action.requiredString("transport").lowercase()) {
            "local" -> ActionTransport.LOCAL
            "shizuku" -> ActionTransport.SHIZUKU
            "accessibility" -> ActionTransport.ACCESSIBILITY
            else -> throw IllegalArgumentException("Unknown action transport")
        }
        val confirmation = when (action.requiredString("confirmation").lowercase()) {
            "none" -> ConfirmationRequirement.NONE
            "required" -> ConfirmationRequirement.REQUIRED
            else -> ConfirmationRequirement.REQUIRED
        }
        val arguments = action["arguments"]?.jsonObject?.mapValues { (_, value) ->
            value.jsonPrimitive.content
        }.orEmpty()
        return AgentActionRequest(action.requiredString("capability_id"), transport, confirmation, arguments)
    }

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name) ?: throw IllegalArgumentException("Missing $name")

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    /** Models often wrap the required object in a Markdown fence or a short preamble. */
    private fun extractJsonObject(raw: String): String {
        val firstBrace = raw.indexOf('{')
        val lastBrace = raw.lastIndexOf('}')
        require(firstBrace >= 0 && lastBrace > firstBrace) { "Agent response contains no JSON object" }
        return raw.substring(firstBrace, lastBrace + 1)
    }
}
