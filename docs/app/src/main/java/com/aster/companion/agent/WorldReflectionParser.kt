package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WorldReflection(val privateWorld: String, val sharedWorld: String)

object WorldReflectionParser {
    fun parse(raw: String): WorldReflection {
        val root = Json.parseToJsonElement(raw.trim()).jsonObject
        val privateWorld = root["private_world"]?.jsonPrimitive?.content?.trim().orEmpty()
        val sharedWorld = root["shared_world"]?.jsonPrimitive?.content?.trim().orEmpty()
        require(privateWorld.isNotBlank() || sharedWorld.isNotBlank()) { "World reflection was empty" }
        return WorldReflection(privateWorld.take(800), sharedWorld.take(800))
    }
}
