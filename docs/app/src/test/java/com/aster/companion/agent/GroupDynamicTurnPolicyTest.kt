package com.aster.companion.agent

import com.aster.companion.domain.GroupProactiveIntent
import com.aster.companion.domain.GroupTurnPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupDynamicTurnPolicyTest {
    @Test
    fun `initial proactive queue contains only main speaker`() {
        val plan = GroupTurnPlan(
            mainSpeakerId = "lin",
            followUpSpeakerIds = listOf("yuan", "lin"),
            intent = GroupProactiveIntent.CONTINUE
        )

        assertEquals(listOf("lin"), GroupDynamicTurnPolicy.initialSpeakers(plan))
    }
}
