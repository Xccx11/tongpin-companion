package com.aster.companion.agent.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class WorldDecisionSnapshot(
    val characterDecisions: Map<String, WorldCharacterDecision> = emptyMap(),
    /** null means the user has not responded; false is an explicit rejection. */
    val userAccepted: Boolean? = null,
    val nextAttemptAt: Long = 0L
)

object WorldDecisionCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: WorldDecisionSnapshot): String = JsonObject(
        buildMap {
            put(
                "characters",
                JsonObject(snapshot.characterDecisions.mapValues { JsonPrimitive(it.value.name) })
            )
            snapshot.userAccepted?.let { put("user_accepted", JsonPrimitive(it)) }
            if (snapshot.nextAttemptAt > 0L) put("next_attempt_at", JsonPrimitive(snapshot.nextAttemptAt))
        }
    ).toString()

    fun decode(value: String): WorldDecisionSnapshot = runCatching {
        val root = json.parseToJsonElement(value).jsonObject
        val decisions = (root["characters"] as? JsonObject)
            ?.mapNotNull { (characterId, raw) ->
                raw.jsonPrimitive.contentOrNull
                    ?.uppercase()
                    ?.let { decision -> enumValues<WorldCharacterDecision>().firstOrNull { it.name == decision } }
                    ?.let { characterId to it }
            }
            ?.toMap()
            .orEmpty()
        WorldDecisionSnapshot(
            characterDecisions = decisions,
            userAccepted = root["user_accepted"]?.jsonPrimitive?.booleanOrNull,
            nextAttemptAt = root["next_attempt_at"]?.jsonPrimitive?.longOrNull ?: 0L
        )
    }.getOrDefault(WorldDecisionSnapshot())
}
