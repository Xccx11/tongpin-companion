package com.aster.companion.agent

import com.aster.companion.domain.GroupTurnPlan

/** The initial plan starts a turn; only persisted messages may justify a follow-up. */
object GroupDynamicTurnPolicy {
    fun initialSpeakers(plan: GroupTurnPlan): List<String> = listOfNotNull(plan.mainSpeakerId)
}
