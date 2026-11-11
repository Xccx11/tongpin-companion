package com.aster.companion.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Decodes the standard Images response without treating a JSON null URL as a real URL. */
object ImageGenerationResponsePolicy {
    private val json = Json { ignoreUnknownKeys = true }

    fun imageReference(body: String): String {
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("图像供应商没有返回图片数据")
        item["url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        item["b64_json"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let { encoded ->
            // 网关为了省流量会把 PNG 转成 JPEG 回传，所以 mime 跟着响应走；
            // 老版本网关不带这个字段时按 png 兜底，行为与改动前一致。
            val mime = item["mime_type"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.startsWith("image/") } ?: "image/png"
            return "data:$mime;base64,$encoded"
        }
        error("图像供应商没有返回可显示的图片")
    }
}
