package com.aster.companion.agent

import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.domain.GroupProactiveIntent
import com.aster.companion.domain.GroupTurnPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Validates the model's group-turn choice before any participant is allowed to speak. */
object GroupTurnPlanParser {
    fun parse(
        raw: String,
        memberIds: Set<String>,
        atmosphere: GroupAtmosphere,
        memberNameToId: Map<String, String> = emptyMap()
    ): GroupTurnPlan = runCatching {
        val payload = Json.parseToJsonElement(raw.jsonObjectCandidate()).jsonObject
        fun field(vararg names: String): String? = names.asSequence()
            .mapNotNull { name -> payload[name]?.jsonPrimitive?.contentOrNull?.trim() }
            .firstOrNull(String::isNotBlank)
        fun resolve(value: String?): String? {
            if (value.isNullOrBlank()) return null
            if (value in memberIds) return value
            return memberNameToId[value]
                ?: memberNameToId[value.removePrefix("@").trim()]
                ?: memberNameToId.entries.firstOrNull { (key, _) -> key.equals(value, ignoreCase = true) }?.value
        }
        val main = resolve(field("main_speaker_id", "mainSpeakerId", "main_speaker", "mainSpeaker", "speaker_id", "speakerId"))
        val parsedFollowUps = listOf(
            "follow_up_speaker_ids", "followUpSpeakerIds", "follow_ups", "followUps",
            "next_speaker_ids", "nextSpeakerIds"
        ).asSequence()
            .mapNotNull { key -> payload[key]?.jsonArray }
            .firstOrNull()
            ?.mapNotNull { element -> resolve(element.jsonPrimitive.contentOrNull?.trim()) }
            .orEmpty()
            .asSequence()
            .fold(mutableListOf<String>()) { accepted, candidate ->
                val previous = accepted.lastOrNull() ?: main
                if (candidate != previous && accepted.size < atmosphere.maxFollowUps) accepted += candidate
                accepted
            }
        val requestedInvitation = resolve(field("invited_speaker_id", "invitedSpeakerId", "invited_speaker", "invitedSpeaker"))
            ?.takeIf { it != main }
        val followUps = buildList {
            requestedInvitation?.let(::add)
            parsedFollowUps.forEach(::add)
        }.fold(mutableListOf<String>()) { accepted, candidate ->
            val previous = accepted.lastOrNull() ?: main
            if (candidate != previous && accepted.size < atmosphere.maxFollowUps) accepted += candidate
            accepted
        }
        val parsedIntent = payload["intent"]
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.uppercase()
            ?.let { value -> runCatching { GroupProactiveIntent.valueOf(value) }.getOrNull() }
        val intent = when {
            parsedIntent == GroupProactiveIntent.SILENT -> GroupProactiveIntent.SILENT
            main == null -> GroupProactiveIntent.SILENT
            parsedIntent == GroupProactiveIntent.START_TOPIC &&
                !payload["topic"]?.jsonPrimitive?.content.orEmpty().isBlank() -> GroupProactiveIntent.START_TOPIC
            else -> GroupProactiveIntent.CONTINUE
        }
        val topic = payload["topic"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank)
        GroupTurnPlan(
            mainSpeakerId = main.takeIf { intent != GroupProactiveIntent.SILENT },
            followUpSpeakerIds = followUps.takeIf { intent != GroupProactiveIntent.SILENT }.orEmpty(),
            intent = intent,
            topic = topic,
            invitedSpeakerId = requestedInvitation
        )
    }.getOrDefault(GroupTurnPlan(null, emptyList(), GroupProactiveIntent.SILENT))

    private fun String.jsonObjectCandidate(): String {
        val start = indexOf('{')
        val end = lastIndexOf('}')
        return if (start >= 0 && end > start) substring(start, end + 1) else this
    }
}
