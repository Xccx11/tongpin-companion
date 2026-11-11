package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class ComfyGatewayTaskStatus { Queued, Running, Succeeded, Failed, Cancelled }

data class ComfyGatewayTask(
    val id: String,
    val status: ComfyGatewayTaskStatus,
    val imageUrl: String? = null,
    val error: String? = null
)

/** The app sends a compiled visual brief; the desktop gateway only formats it for ComfyUI. */
data class ComfyGatewayPromptContext(
    val characterAppearance: String = "",
    val visualBrief: String = "",
    val requiredCharacterCount: Int = 1
)

/** Small protocol boundary for Tongpin's local ComfyUI gateway; generic image APIs stay untouched. */
object ComfyGatewayTaskProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun supports(profile: ModelProfileEntity): Boolean =
        ImageProviderProtocolResolver.resolve(profile) == ImageProviderProtocol.COMFY_GATEWAY_TASK

    fun parseStatus(raw: String): ComfyGatewayTask {
        val root = json.parseToJsonElement(raw).jsonObject
        val id = root["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Image gateway did not return a task id")
        val status = when (root["status"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "queued" -> ComfyGatewayTaskStatus.Queued
            "running" -> ComfyGatewayTaskStatus.Running
            "succeeded" -> ComfyGatewayTaskStatus.Succeeded
            "failed" -> ComfyGatewayTaskStatus.Failed
            "cancelled", "canceled" -> ComfyGatewayTaskStatus.Cancelled
            else -> throw IllegalStateException("Image gateway returned an unknown task status")
        }
        val imageUrl = root["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
        val error = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        if (status == ComfyGatewayTaskStatus.Succeeded && imageUrl.isNullOrBlank()) {
            throw IllegalStateException("Image gateway completed without an image URL")
        }
        return ComfyGatewayTask(id, status, imageUrl, error)
    }
}
