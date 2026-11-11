package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Presentation-only copy from the already persisted action payload. */
object PhotoConsentCopy {
    fun fromActionArguments(argumentsJson: String): String? = runCatching {
        Json.parseToJsonElement(argumentsJson).jsonObject["consent_note"]
            ?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()
}
