package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ProactiveGroundingVerdict {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Providers frequently append a short rationale even when asked for a bare
     * verdict. Accept only an explicit leading ALLOW or a verdict JSON field;
     * arbitrary prose never becomes an approval.
     */
    fun allows(raw: String): Boolean {
        val normalized = raw.trim()
        if (normalized.startsWith("{")) {
            val verdict = runCatching {
                json.parseToJsonElement(normalized).jsonObject["verdict"]?.jsonPrimitive?.content
            }.getOrNull()
            return verdict.equals("allow", ignoreCase = true)
        }
        val firstLine = normalized.lineSequence().firstOrNull()?.trim()?.uppercase().orEmpty()
        return firstLine == "ALLOW" ||
            firstLine.startsWith("ALLOW ") ||
            firstLine.startsWith("ALLOW:") ||
            firstLine.startsWith("ALLOW -")
    }
}
