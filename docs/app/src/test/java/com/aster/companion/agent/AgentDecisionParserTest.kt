package com.aster.companion.agent

import com.aster.companion.domain.AgentDecisionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentDecisionParserTest {
    @Test
    fun `parses a proactive send decision`() {
        val decision = AgentDecisionParser.parse("""{"decision":"send_now","message":"外卖到了，趁热吃。"}""")
        assertEquals(AgentDecisionKind.SEND_NOW, decision.kind)
        assertEquals("外卖到了，趁热吃。", decision.message)
    }

    @Test fun `parses a decision wrapped in a Markdown JSON fence`() {
        val decision = AgentDecisionParser.parse(
            """
            Here is the decision:
            ```json
            {"decision":"send_now","message":"A short in-character note","extra":"ignored"}
            ```
            """.trimIndent()
        )

        assertEquals(AgentDecisionKind.SEND_NOW, decision.kind)
        assertEquals("A short in-character note", decision.message)
    }
}
