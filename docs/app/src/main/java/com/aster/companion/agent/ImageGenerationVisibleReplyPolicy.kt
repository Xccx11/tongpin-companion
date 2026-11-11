package com.aster.companion.agent

/** Keeps the role's conversational response visible while its private image command stays internal. */
object ImageGenerationVisibleReplyPolicy {
    fun visibleModelReply(rawCandidate: String, characterName: String): String? {
        val plannedMessage = CompanionReplyPlanParser.parse(rawCandidate)?.message?.trim().orEmpty()
        val visibleCandidate = ImageReplySanitizer.visibleReply(
            if (plannedMessage.isNotBlank()) plannedMessage else CompanionReplyPlanParser.visibleReply(rawCandidate),
            characterName
        )
        return visibleCandidate
            .takeUnless(::containsInternalWorkflow)
            ?.takeIf(String::isNotBlank)
    }

    fun fromModel(rawCandidate: String, characterName: String): String {
        return visibleModelReply(rawCandidate, characterName) ?: FALLBACK_REPLY
    }

    private fun containsInternalWorkflow(value: String): Boolean {
        val normalized = value.lowercase()
        return INTERNAL_MARKERS.any { marker -> normalized.contains(marker) }
    }

    private val INTERNAL_MARKERS = listOf(
        "提示词",
        "生图命令",
        "调用模型",
        "tool_calls",
        "companion.",
        "comfyui",
        "workflow",
        "json"
    )

    const val FALLBACK_REPLY = "好呀，我去准备一张给你。你点一下确认，我就开始生成。"
}
