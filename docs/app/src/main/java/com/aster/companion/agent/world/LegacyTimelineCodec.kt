package com.aster.companion.agent.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Compatibility format for pre-v27 timeline snapshots. */
internal object LegacyTimelineCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(events: List<PlannedEvent>): String = buildJsonArray {
        events.forEach { event ->
            add(buildJsonObject {
                put("id", event.id)
                put("time", event.time)
                put("duration", event.durationMinutes)
                put("type", event.type)
                put("participants", buildJsonArray { event.participants.forEach { add(JsonPrimitive(it)) } })
                put("description", event.description)
                put("status", event.status)
            })
        }
    }.toString()

    fun decode(value: String): List<PlannedEvent> = runCatching {
        json.parseToJsonElement(value).jsonArray.mapNotNull { item ->
            val event = item as? JsonObject ?: return@mapNotNull null
            val id = event["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            PlannedEvent(
                id = id,
                time = event["time"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                durationMinutes = event["duration"]?.jsonPrimitive?.intOrNull ?: 30,
                type = event["type"]?.jsonPrimitive?.contentOrNull ?: "activity",
                participants = event["participants"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty(),
                description = event["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                status = event["status"]?.jsonPrimitive?.contentOrNull ?: "planned"
            )
        }
    }.getOrDefault(emptyList())
}
