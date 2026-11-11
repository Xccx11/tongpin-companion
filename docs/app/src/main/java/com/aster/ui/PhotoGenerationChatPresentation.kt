package com.aster.ui

import com.aster.companion.data.PendingActionEntity

/** Keeps a photo approval visible until the role's final image message replaces it. */
object PhotoGenerationChatPresentation {
    fun shouldShowInConversation(action: PendingActionEntity): Boolean {
        // 偷看任务（peek）只出现在"设置→生成任务"列表，不在对话里弹确认卡。
        val isPeek = action.argumentsJson.contains("\"peek\":\"true\"")
        return !isPeek && action.capabilityId in IMAGE_CAPABILITIES && action.state in VISIBLE_STATES
    }

    private val IMAGE_CAPABILITIES = setOf("companion.generate_image", "companion.propose_image")
    private val VISIBLE_STATES = setOf("PENDING", "RUNNING")
}
