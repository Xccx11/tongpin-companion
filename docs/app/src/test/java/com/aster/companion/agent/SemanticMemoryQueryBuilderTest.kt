package com.aster.companion.agent

import com.aster.companion.proactive.LifeSnapshotFact
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticMemoryQueryBuilderTest {
    @Test
    fun `semantic query includes complete conversation trigger and current life facts`() {
        val query = SemanticMemoryQueryBuilder.build(
            recentConversation = listOf("User: 我刚下班", "岚: 那我陪你走一会"),
            triggerSummary = "The user has not replied after the character's last message.",
            lifeContext = listOf(LifeSnapshotFact("battery", "Battery is 18%", 1L))
        )

        assertTrue(query.contains("我刚下班"))
        assertTrue(query.contains("陪你走一会"))
        assertTrue(query.contains("Battery is 18%"))
        assertTrue(query.contains("has not replied"))
    }
}
