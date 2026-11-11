package com.aster.companion.agent

import com.aster.companion.domain.GroupProactiveIntent
import com.aster.companion.domain.GroupTurnPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupUserTurnSelectionPolicyTest {
    @Test
    fun `silent group planner falls back to the least recent member`() {
        val plan = GroupUserTurnSelectionPolicy.ensureDirectUserReply(
            GroupTurnPlan(null, emptyList(), GroupProactiveIntent.SILENT),
            listOf("lin", "lan"),
            allowGroupSilence = false
        )

        assertEquals("lin", plan.mainSpeakerId)
    }
}
