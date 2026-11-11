package com.aster.companion.agent.world

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses only the constrained opportunity contract returned by WorldDirector. */
object WorldOpportunityCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseDirectorResponse(
        raw: String,
        timelineId: String,
        windowStartAt: Long,
        knownCharacterIds: Set<String>,
        knownLocationIds: Set<String>
    ): List<WorldOpportunity> = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val events = root["events"]?.jsonArray ?: root["timeline"]?.jsonArray ?: JsonArray(emptyList())
        events.mapNotNull { element ->
            element as? JsonObject
        }.mapNotNull { event ->
            event.toOpportunity(
                timelineId = timelineId,
                windowStartAt = windowStartAt,
                knownCharacterIds = knownCharacterIds,
                knownLocationIds = knownLocationIds
            )
        }
    }.getOrDefault(emptyList())

    private fun JsonObject.toOpportunity(
        timelineId: String,
        windowStartAt: Long,
        knownCharacterIds: Set<String>,
        knownLocationIds: Set<String>
    ): WorldOpportunity? {
        val offsetMinutes = int("time_offset_minutes") ?: return null
        val durationMinutes = int("duration_minutes") ?: int("duration") ?: return null
        val required = stringArray("required_character_ids")
            .ifEmpty { stringArray("participants") }
        val optional = stringArray("optional_character_ids").filterNot { it in required }
        val initiator = string("initiator_character_id")
        val locationId = string("location_id") ?: return null
        val description = string("description")?.trim().orEmpty()
        if (offsetMinutes !in 0..60 || durationMinutes !in 1..240 || description.isBlank()) return null
        if (required.isEmpty() || "user" in required || "user" in optional) return null
        if (!knownCharacterIds.containsAll(required) || !knownCharacterIds.containsAll(optional)) return null
        if (initiator != null && initiator !in knownCharacterIds) return null
        if (initiator != null && initiator !in required) return null
        if (locationId !in knownLocationIds) return null
        val latestOffset = int("latest_start_offset_minutes") ?: offsetMinutes
        if (latestOffset !in offsetMinutes..120) return null
        if (!isObjectOrAbsent("prerequisites") || !isObjectOrAbsent("expected_effects")) return null
        return WorldOpportunity(
            id = string("id")?.takeIf { it.isNotBlank() } ?: "world-opportunity:${UUID.randomUUID()}",
            timelineId = timelineId,
            scheduledAt = windowStartAt + offsetMinutes * MINUTE_MS,
            latestStartAt = windowStartAt + latestOffset * MINUTE_MS,
            durationMinutes = durationMinutes,
            type = string("type")?.takeIf { it.isNotBlank() } ?: "activity",
            initiatorCharacterId = initiator,
            requiredCharacterIds = required,
            optionalCharacterIds = optional,
            userParticipation = userParticipation(string("user_participation")),
            locationId = locationId,
            description = description.take(MAX_DESCRIPTION_LENGTH),
            prerequisiteEventIds = stringArray("prerequisite_event_ids"),
            prerequisiteJson = objectJson("prerequisites"),
            expectedEffectsJson = objectJson("expected_effects"),
            flexibility = enumValue(string("flexibility"), WorldEventFlexibility.FLEXIBLE),
            interruptStrategy = enumValue(string("interrupt_strategy"), WorldEventInterruptStrategy.REEVALUATE)
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.stringArray(name: String): List<String> =
        (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            ?.distinct()
            ?: emptyList()

    private fun JsonObject.objectJson(name: String): String =
        (this[name] as? JsonObject)?.toString() ?: "{}"

    private fun JsonObject.isObjectOrAbsent(name: String): Boolean =
        this[name] == null || this[name] is JsonObject

    private fun userParticipation(raw: String?): UserParticipation = when (raw?.uppercase()) {
        UserParticipation.OPTIONAL.name -> UserParticipation.OPTIONAL
        UserParticipation.REQUIRED.name -> UserParticipation.REQUIRED
        else -> UserParticipation.NONE
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, default: T): T =
        raw?.uppercase()?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private const val MINUTE_MS = 60_000L
    private const val MAX_DESCRIPTION_LENGTH = 240
}
