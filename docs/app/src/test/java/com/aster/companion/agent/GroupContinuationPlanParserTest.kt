package com.aster.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupContinuationPlanParserTest {
    @Test
    fun `accepts a model selected member name through the supplied member map`() {
        val result = GroupContinuationPlanParser.parse(
            raw = """{"next_speaker_name":"妹妹"}""",
            memberIds = setOf("older", "younger"),
            previousSpeakerId = "older",
            memberNameToId = mapOf("姐姐" to "older", "妹妹" to "younger")
        )

        assertEquals("younger", result)
    }

    @Test
    fun `accepts the model selected next member and rejects the current speaker`() {
        val type = Class.forName("com.aster.companion.agent.GroupContinuationPlanParser")
        val instance = type.getField("INSTANCE").get(null)
        val parse = type.getMethod("parse", String::class.java, Set::class.java, String::class.java)

        val next = parse.invoke(instance, "{\"next_speaker_id\":\"sister\"}", setOf("sister", "younger"), "younger") as String?
        val same = parse.invoke(instance, "{\"next_speaker_id\":\"younger\"}", setOf("sister", "younger"), "younger") as String?

        assertEquals("sister", next)
        assertNull(same)
    }

    @Test
    fun `accepts fenced json and common next speaker aliases`() {
        val next = GroupContinuationPlanParser.parse(
            raw = """```json
                {"next_speaker":"姐姐"}
            ```""".trimIndent(),
            memberIds = setOf("older", "younger"),
            previousSpeakerId = "younger",
            memberNameToId = mapOf("姐姐" to "older")
        )

        assertEquals("older", next)
    }
}
