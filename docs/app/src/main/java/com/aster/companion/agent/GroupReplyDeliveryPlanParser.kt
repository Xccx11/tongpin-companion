package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The only two destinations available to a character responding from a group. */
enum class GroupReplyDelivery {
    GROUP,
    PRIVATE
}

data class GroupReplyDeliveryPlan(
    val delivery: GroupReplyDelivery,
    val message: String
)

/** Parses the explicit destination selected by a group reply model. */
object GroupReplyDeliveryPlanParser {
    private val json = Json { ignoreUnknownKeys = true }

    const val OUTPUT_CONTRACT = "Return JSON only: {\"delivery\":\"GROUP or PRIVATE\",\"message\":\"natural in-character Chinese message\"}."

    fun parse(raw: String): GroupReplyDeliveryPlan? = runCatching {
        val payload = json.parseToJsonElement(raw.jsonObjectCandidate()).jsonObject
        val message = sequenceOf("message", "content", "text", "reply", "response", "body")
            .mapNotNull { key -> payload[key]?.jsonPrimitive?.contentOrNull?.trim() }
            .firstOrNull(String::isNotBlank)
            ?.take(MAX_MESSAGE_LENGTH)
            ?: return null
        val delivery = sequenceOf("delivery", "destination", "target")
            .mapNotNull { key -> payload[key]?.jsonPrimitive?.contentOrNull }
            .map(String::trim)
            .map(String::uppercase)
            .mapNotNull { value -> GroupReplyDelivery.entries.firstOrNull { it.name == value } }
            .firstOrNull()
            ?: return null
        GroupReplyDeliveryPlan(delivery, message)
    }.getOrNull()

    fun parseOrGroup(raw: String): GroupReplyDeliveryPlan? {
        val structured = parse(raw)
        if (structured != null) return structured
        val message = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .takeIf { it.isNotBlank() && !it.contains('{') && !it.contains('}') }
            ?.take(MAX_MESSAGE_LENGTH)
            ?: return null
        return GroupReplyDeliveryPlan(GroupReplyDelivery.GROUP, message)
    }

    private fun String.jsonObjectCandidate(): String {
        val start = indexOf('{')
        val end = lastIndexOf('}')
        return if (start >= 0 && end > start) substring(start, end + 1) else this
    }

    private const val MAX_MESSAGE_LENGTH = 1_200
}
