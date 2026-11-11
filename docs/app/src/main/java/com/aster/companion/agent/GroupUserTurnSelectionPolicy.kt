package com.aster.companion.agent

import com.aster.companion.domain.GroupProactiveIntent
import com.aster.companion.domain.GroupTurnPlan

object GroupUserTurnSelectionPolicy {
    fun ensureDirectUserReply(
        plan: GroupTurnPlan,
        memberIdsByLeastRecentSpeech: List<String>,
        allowGroupSilence: Boolean
    ): GroupTurnPlan {
        if (allowGroupSilence || plan.mainSpeakerId != null) return plan
        val fallback = memberIdsByLeastRecentSpeech.firstOrNull() ?: return plan
        return plan.copy(mainSpeakerId = fallback, followUpSpeakerIds = emptyList(), intent = GroupProactiveIntent.CONTINUE)
    }
}
