package com.aster.companion.ui

import com.aster.companion.data.PendingActionEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shared labels for consent cards and the persistent image-task screen. */
// 刻意保留 "consent_note" 这个 JSON key 的读取：它是她自己的原话，见 PhotoConsentCopy。
object ImageGenerationActionPresentation {
    fun title(action: PendingActionEntity, characterNames: Map<String, String>): String {
        // **她当时说的那句话优先**：出图请求里一直带着 `consent_note`（她的原话，比如
        // "别催，我去换件睡衣拍给你。"）。同意卡后来改成只显示系统拼的"她想发一张照片"，
        // 把语气全丢了 —— payload 里的原话没人读，[PhotoConsentCopy] 这个提取器就成了死代码。
        com.aster.companion.agent.PhotoConsentCopy.fromActionArguments(action.argumentsJson)?.let { return it }
        if (action.capabilityId == "companion.peek_image") return "偷看照片"
        val args = arguments(action)
        if (action.capabilityId == "companion.share_life_moment") {
            val what = args["what"]?.take(20).orEmpty()
            return "她想给你看" + if (what.isBlank()) "这一刻" else "$what"
        }
        val participantIds = args["participant_character_ids"]
            ?.split(',', '|', '\n')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
            .ifEmpty { listOfNotNull(args["character_id"]) }
        val names = participantIds.map { characterNames[it] ?: it }
        val people = names.joinToString("和").ifBlank { "她" }
        if (action.capabilityId == "companion.generate_moment_image") {
            return "${names.firstOrNull() ?: action.capabilityId} 想发一张动态照片"
        }
        return when (args["group_image_mode"]?.trim()?.uppercase()) {
            "TOGETHER" -> "${people}想发一张合照"
            "SEPARATE" -> "${people}想分别发照片"
            else -> "${names.firstOrNull() ?: "她"}想发一张照片"
        }
    }

    fun detail(action: PendingActionEntity): String = when (action.state) {
        "PENDING" -> "同意后才会开始生成"
        "RUNNING" -> "正在出图，先不着急"
        "APPROVED" -> "已发送到原对话"
        "FAILED" -> action.resultSummary?.take(160) ?: "生成失败"
        "REJECTED" -> "已拒绝"
        "CANCELLED" -> "已取消生成"
        else -> action.resultSummary?.take(160) ?: action.state
    }

    private fun arguments(action: PendingActionEntity): Map<String, String> = runCatching {
        Json.parseToJsonElement(action.argumentsJson).jsonObject.mapValues { (_, value) -> value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())
}
