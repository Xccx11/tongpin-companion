package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Validates a model-selected follow-up speaker after a visible group message. */
object GroupContinuationPlanParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, memberIds: Set<String>, previousSpeakerId: String): String? =
        parse(raw, memberIds, previousSpeakerId, emptyMap())

    fun parse(
        raw: String,
        memberIds: Set<String>,
        previousSpeakerId: String,
        memberNameToId: Map<String, String>
    ): String? = runCatching {
        val payload = json.parseToJsonElement(raw.jsonObjectCandidate()).jsonObject
        fun field(vararg names: String): String? = names.asSequence()
            .mapNotNull { name -> payload[name]?.jsonPrimitive?.contentOrNull }
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)

        val idOrNameCandidate = field(
            "next_speaker_id", "nextSpeakerId", "next_speaker", "nextSpeaker",
            "responder_id", "responderId", "speaker_id", "speakerId"
        )
        val candidate = idOrNameCandidate
            ?.let { value ->
                value.takeIf { it in memberIds }
                    ?: memberNameToId[value]
                    ?: memberNameToId[value.removePrefix("@").trim()]
                    ?: memberNameToId.entries.firstOrNull { (key, _) -> key.equals(value, ignoreCase = true) }?.value
            }
            ?.takeIf { it in memberIds }
            ?: field(
                "next_speaker_name", "nextSpeakerName", "speaker_name", "speakerName",
                "responder_name", "responderName", "member_name", "memberName"
            )
                ?.let { value ->
                    memberNameToId[value]
                        ?: memberNameToId[value.removePrefix("@").trim()]
                        ?: memberNameToId.entries.firstOrNull { (key, _) -> key.equals(value, ignoreCase = true) }?.value
                }
                ?.takeIf { it in memberIds }
        candidate?.takeIf { it != previousSpeakerId }
    }.getOrNull()

    private fun String.jsonObjectCandidate(): String {
        val start = indexOf('{')
        val end = lastIndexOf('}')
        return if (start >= 0 && end > start) substring(start, end + 1) else this
    }
}
