package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ActivityParticipants(
    val characterIds: List<String>,
    val userParticipated: Boolean
)

/** Reads the legacy sender/receiver payload and the V3 participant array. */
object ActivityParticipantCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeWorld(characterIds: List<String>, userParticipated: Boolean): String = JsonObject(
        mapOf(
            "character_ids" to JsonArray(characterIds.distinct().map(::JsonPrimitive)),
            "user_participated" to JsonPrimitive(userParticipated)
        )
    ).toString()

    fun decode(value: String): ActivityParticipants = runCatching {
        val root = json.parseToJsonElement(value).jsonObject
        val ids = root["character_ids"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            ?.distinct()
            ?: legacyIds(root)
        ActivityParticipants(
            characterIds = ids,
            userParticipated = root["user_participated"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }.getOrDefault(ActivityParticipants(emptyList(), false))

    private fun legacyIds(root: JsonObject): List<String> = listOfNotNull(
        root["sender"]?.jsonPrimitive?.contentOrNull,
        root["receiver"]?.jsonPrimitive?.contentOrNull
    ).map(String::trim).filter(String::isNotBlank).distinct()
}
