package com.aster.service

import kotlinx.serialization.json.JsonElement

/**
 * Result of a command execution.
 */
data class CommandResult(
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null
) {
    companion object {
        fun success(data: JsonElement? = null) = CommandResult(success = true, data = data)
        fun failure(error: String) = CommandResult(success = false, error = error)
    }
}
