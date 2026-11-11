package com.aster.companion.agent

import com.aster.companion.domain.GroupAtmosphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupBatchReplyPlanParserTest {
    @Test
    fun `keeps one model planned exchange in its declared order`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """
                {"turns":[
                  {"character_id":"younger","message":"姐姐刚才说得对。"},
                  {"character_id":"older","message":"嗯，这次我站你这边。"}
                ]}
            """.trimIndent(),
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(listOf("younger", "older"), plan.turns.map { it.characterId })
        assertEquals("姐姐刚才说得对。", plan.turns.first().message)
    }

    @Test
    fun `rejects invalid speakers and caps a lively group to one planned batch`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """
                {"turns":[
                  {"character_id":"older","message":"第一句"},
                  {"character_id":"older","message":"重复的人不应连说"},
                  {"character_id":"missing","message":"不在群里"},
                  {"character_id":"younger","message":"第二句"},
                  {"character_id":"older","message":"第三句"},
                  {"character_id":"younger","message":"超过上限"}
                ]}
            """.trimIndent(),
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.LIVELY
        )

        assertEquals(listOf("older", "younger", "older", "younger"), plan.turns.map { it.characterId })
    }

    @Test
    fun `keeps a private image action attached to its requesting character`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """
                {"turns":[{
                  "character_id":"xia",
                  "message":"我去私聊发给你。",
                  "actions":[{"capability_id":"companion.propose_private_image","arguments":{"character_id":"xia","scene":"I am Xia and I send the requested private photo."}}]
                }]}
            """.trimIndent(),
            memberIds = setOf("xia", "lan"),
            atmosphere = GroupAtmosphere.DAILY
        )

        val action = plan.turns.single().actions.single()
        assertEquals("companion.propose_private_image", action.capabilityId)
        assertEquals("xia", action.arguments["character_id"])
        assertTrue(action.arguments["scene"].orEmpty().isNotBlank())
    }

    @Test
    fun `accepts common reply array names and speaker names from a fenced response`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """
                ```json
                {"messages":[
                  {"speaker_name":"older","content":"我听见了。"},
                  {"characterName":"younger","text":"那你也说两句嘛。"}
                ]}
                ```
            """.trimIndent(),
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY,
            memberNameToId = mapOf("older" to "older", "younger" to "younger")
        )

        assertEquals(listOf("older", "younger"), plan.turns.map { it.characterId })
        assertEquals("那你也说两句嘛。", plan.turns.last().message)
    }

    @Test
    fun `accepts a single reply object and maps a display name to its member id`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"speaker":"姐姐","reply":"我在听，不是没有回应。"}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY,
            memberNameToId = mapOf("姐姐" to "older")
        )

        assertEquals(listOf("older"), plan.turns.map { it.characterId })
        assertEquals("我在听，不是没有回应。", plan.turns.single().message)
    }

    @Test
    fun `keeps an explicitly private planned turn out of the group destination`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"turns":[{"character_id":"older","delivery":"PRIVATE","message":"这句我想单独和你说。"}]}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(GroupReplyDelivery.PRIVATE, plan.turns.single().delivery)
    }

    @Test
    fun `defaults an omitted or invalid delivery to the group`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"turns":[
              {"character_id":"older","message":"默认在群里。"},
              {"character_id":"younger","delivery":"SOMEWHERE","message":"无效目标也留在群里。"}
            ]}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(listOf(GroupReplyDelivery.GROUP, GroupReplyDelivery.GROUP), plan.turns.map { it.delivery })
    }

    @Test
    fun `accepts a top level reply array and common speaker aliases`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """[{"speaker":"older","content":"我听见了。"},{"speaker":"younger","content":"我也在听。"}]""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY,
            memberNameToId = mapOf("older" to "older")
        )

        assertEquals(listOf("older", "younger"), plan.turns.map { it.characterId })
        assertEquals("我听见了。", plan.turns.first().message)
    }

    @Test
    fun `uses the valid reply json after model reasoning contains an earlier json example`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """
                我会按这个格式规划：{"turns":[{"character_id":"missing","message":"示例"}]}
                最终计划：
                {"turns":[{"character_id":"older","message":"我接住你刚才的话了。"}]}
            """.trimIndent(),
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(listOf("older"), plan.turns.map { it.characterId })
        assertEquals("我接住你刚才的话了。", plan.turns.single().message)
    }

    @Test
    fun `unwraps a provider response whose final plan is double encoded as a json string`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = "\"{\\\"turns\\\":[{\\\"character_id\\\":\\\"older\\\",\\\"message\\\":\\\"我在群里接着说。\\\"}]}\"",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(listOf("older"), plan.turns.map { it.characterId })
        assertEquals("我在群里接着说。", plan.turns.single().message)
    }

    @Test
    fun `unwraps an encoded turns array and a nested reply plan`() {
        val encodedTurns = GroupBatchReplyPlanParser.parse(
            raw = """{"turns":"[{\"character_id\":\"older\",\"message\":\"数组字符串也能解析。\"}]"}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )
        val nestedPlan = GroupBatchReplyPlanParser.parse(
            raw = """{"reply_plan":{"turns":[{"character_id":"younger","message":"嵌套计划也能解析。"}]}}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals("数组字符串也能解析。", encodedTurns.turns.single().message)
        assertEquals("younger", nestedPlan.turns.single().characterId)
    }

    @Test
    fun `uses the selected fallback member for a single array item without a speaker id`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """[{"content":"我来接着说。"}]""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY,
            fallbackCharacterId = "younger"
        )

        assertEquals(listOf("younger"), plan.turns.map { it.characterId })
    }

    @Test
    fun `a private delivery terminates the visible group exchange`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"turns":[
              {"character_id":"older","delivery":"GROUP","message":"我先在群里说一句。"},
              {"character_id":"younger","delivery":"PRIVATE","message":"这句单独发给你。"},
              {"character_id":"older","delivery":"GROUP","message":"不能看见私聊后再接话。"}
            ]}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.LIVELY
        )

        assertEquals(listOf("older", "younger"), plan.turns.map { it.characterId })
    }

    @Test
    fun `private message action makes the turn private instead of duplicating it in the group`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"turns":[{"character_id":"older","delivery":"GROUP","message":"我去私聊你。","actions":[{"capability_id":"companion.send_private_message","arguments":{"character_id":"older","message":"这句我单独和你说。"}}]}]}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(GroupReplyDelivery.PRIVATE, plan.turns.single().delivery)
        assertEquals("这句我单独和你说。", plan.turns.single().message)
    }

    @Test
    fun `unwraps a group wrapper and reads nested speaker and reply objects`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"group":{"turns":[{"speaker":{"id":"older","name":"姐姐"},"reply":{"text":"我接住了妹妹的话。"}}]}}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY,
            memberNameToId = mapOf("姐姐" to "older")
        )

        assertEquals(listOf("older"), plan.turns.map { it.characterId })
        assertEquals("我接住了妹妹的话。", plan.turns.single().message)
    }

    @Test
    fun `prefers nested turns when a provider also adds a top level summary`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"message":"group summary","turns":[{"character_id":"older","message":"姐姐先说。"},{"character_id":"younger","message":"妹妹接上。"}]}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY
        )

        assertEquals(listOf("older", "younger"), plan.turns.map { it.characterId })
    }

    @Test
    fun `assigns omitted or group placeholder speakers from the ordered fallback members`() {
        val plan = GroupBatchReplyPlanParser.parse(
            raw = """{"turns":[{"speaker":"group","message":"姐姐先说。"},{"message":"妹妹接上。"}]}""",
            memberIds = setOf("older", "younger"),
            atmosphere = GroupAtmosphere.DAILY,
            fallbackCharacterId = "older",
            fallbackCharacterIds = listOf("older", "younger")
        )

        assertEquals(listOf("older", "younger"), plan.turns.map { it.characterId })
    }
}
