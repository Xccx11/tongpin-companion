package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class GroupPrivateFollowUpDecision(
    val shouldSend: Boolean,
    val message: String?,
    val sourceMessageIds: List<String> = emptyList()
)

/** Parses the bounded decision used when a visible group exchange inspires a private follow-up. */
object GroupPrivateFollowUpParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): GroupPrivateFollowUpDecision = runCatching {
        val firstBrace = raw.indexOf('{')
        val lastBrace = raw.lastIndexOf('}')
        require(firstBrace >= 0 && lastBrace > firstBrace)
        val root = json.parseToJsonElement(raw.substring(firstBrace, lastBrace + 1)).jsonObject
        val decision = root["decision"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
        val message = root["message"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        val sourceMessageIds = when (val sources = root["source_message_ids"]) {
            is JsonArray -> sources.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            null -> emptyList()
            else -> listOfNotNull(sources.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty))
        }.distinct()
        when (decision) {
            "SEND", "SEND_NOW" -> message?.let { GroupPrivateFollowUpDecision(true, it, sourceMessageIds) }
                ?: GroupPrivateFollowUpDecision(false, null)
            else -> GroupPrivateFollowUpDecision(false, null)
        }
    }.getOrDefault(GroupPrivateFollowUpDecision(false, null))
}
