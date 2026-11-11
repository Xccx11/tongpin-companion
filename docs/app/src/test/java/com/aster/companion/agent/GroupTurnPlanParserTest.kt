package com.aster.companion.agent

import com.aster.companion.domain.GroupAtmosphere
import com.aster.companion.domain.GroupProactiveIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupTurnPlanParserTest {
    @Test
    fun `keeps the valid main speaker and caps follow ups for daily atmosphere`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"main_speaker_id":"a","follow_up_speaker_ids":["a","missing","b","c"]}""",
            memberIds = setOf("a", "b", "c"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals("a", plan.mainSpeakerId)
        assertEquals(listOf("b", "c"), plan.followUpSpeakerIds)
    }

    @Test
    fun `daily atmosphere keeps a natural re entry after another character`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"main_speaker_id":"sister","follow_up_speaker_ids":["younger","sister"]}""",
            memberIds = setOf("sister", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals("sister", plan.mainSpeakerId)
        assertEquals(listOf("younger", "sister"), plan.followUpSpeakerIds)
    }

    @Test
    fun `lively atmosphere keeps three ordered follow ups even when members re enter`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"main_speaker_id":"sister","follow_up_speaker_ids":["younger","sister","younger"]}""",
            memberIds = setOf("sister", "younger"),
            atmosphere = GroupAtmosphere.LIVELY
        )

        assertEquals(listOf("younger", "sister", "younger"), plan.followUpSpeakerIds)
    }

    @Test
    fun `quiet atmosphere removes every follow up`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"main_speaker_id":"a","follow_up_speaker_ids":["b"]}""",
            memberIds = setOf("a", "b"),
            atmosphere = GroupAtmosphere.QUIET
        )

        assertEquals("a", plan.mainSpeakerId)
        assertEquals(emptyList<String>(), plan.followUpSpeakerIds)
    }

    @Test
    fun `invalid planner output becomes a silent plan`() {
        val plan = GroupTurnPlanParser.parse("not-json", setOf("a"), GroupAtmosphere.LIVELY)

        assertEquals(null, plan.mainSpeakerId)
        assertEquals(emptyList<String>(), plan.followUpSpeakerIds)
    }

    @Test
    fun `proactive planner keeps a grounded topic and invited character`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"intent":"START_TOPIC","main_speaker_id":"a","follow_up_speaker_ids":["b"],"topic":"接着刚才的晚饭话题问问用户今天想吃什么","invited_speaker_id":"b"}""",
            memberIds = setOf("a", "b"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(GroupProactiveIntent.START_TOPIC, plan.intent)
        assertEquals("接着刚才的晚饭话题问问用户今天想吃什么", plan.topic)
        assertEquals("b", plan.invitedSpeakerId)
    }

    @Test
    fun `proactive planner drops an invitation outside the group`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"intent":"START_TOPIC","main_speaker_id":"a","follow_up_speaker_ids":[],"topic":"群里刚才提到的计划","invited_speaker_id":"missing"}""",
            memberIds = setOf("a", "b"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(null, plan.invitedSpeakerId)
    }

    @Test
    fun `invited character is retained ahead of other follow ups`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"intent":"START_TOPIC","main_speaker_id":"a","follow_up_speaker_ids":["c"],"topic":"聊聊刚才的晚饭","invited_speaker_id":"b"}""",
            memberIds = setOf("a", "b", "c"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(listOf("b", "c"), plan.followUpSpeakerIds)
    }

    @Test
    fun `explicit silent intent remains silent even when a speaker id is returned`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """{"intent":"SILENT","main_speaker_id":"a","follow_up_speaker_ids":[]}""",
            memberIds = setOf("a", "b"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(GroupProactiveIntent.SILENT, plan.intent)
        assertEquals(null, plan.mainSpeakerId)
    }

    @Test
    fun `maps proactive speaker names and aliases to current member ids`() {
        val plan = GroupTurnPlanParser.parse(
            raw = """```json
                {"main_speaker":"姐姐","follow_ups":["妹妹"],"intent":"CONTINUE"}
            ```""".trimIndent(),
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY,
            memberNameToId = mapOf("姐姐" to "older", "妹妹" to "younger")
        )

        assertEquals("older", plan.mainSpeakerId)
        assertEquals(listOf("younger"), plan.followUpSpeakerIds)
    }
}
