package com.aster.companion.agent.life

import com.aster.companion.data.CharacterLifeStateEntity
import com.aster.companion.domain.CharacterSchedule
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 需求推进与「要不要换」的验收。
 *
 * 重心在两件事：**数值要按时间自然走**，以及**模型抽风时她不会乱换事**——
 * 认出不来就按「继续」处理，这是整套「不像排班」的兜底。
 */
class LifeSwitchPolicyTest {

    @Test
    fun `continue decision gets a visible initial activity when no state exists`() {
        assertEquals("安静待着", LifeSwitchPolicy.activityForContinue(null))
        assertEquals("看书", LifeSwitchPolicy.activityForContinue("看书"))
        assertEquals("安静待着", LifeSwitchPolicy.activityForContinue("  "))
    }

    private val zone: ZoneId = ZoneId.systemDefault()
    private val now = LocalDate.of(2026, 9, 15).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()

    private fun moment(activity: String = "在看剧", startedAt: Long = now - 40 * 60_000L) = LivingMoment(
        activity = activity,
        startedAt = startedAt,
        mood = "放松",
        locationId = "home.living_room",
        intent = "看完这集再说",
        reviewAt = now - 1
    )



    // ---- prompt 要把「惯性」讲清楚 ----

    @Test
    fun `the prompt tells her to keep doing what she is doing by default`() {
        val system = LifeSwitchPolicy.system(input())
        assertTrue(system.contains("在看剧"))
        assertTrue("要说清默认是接着做", system.contains("接着做"))
        assertTrue("换要有理由", system.contains("饿了"))
    }

    @Test
    fun `数值已删除，改成按上下文判断并问她还要做多久`() {
        val system = LifeSwitchPolicy.system(input())
        // 能量/压力/孤独那套数字不再存在，也不再以"你现在的感觉"进提示词。
        assertTrue(!system.contains("energy="))
        assertTrue(!system.contains("你现在的感觉"))
        assertTrue(LifeSwitchPolicy.user(input()).contains("还要做多久"))
    }

    // ---- 解析：认出不来就当她继续做 ----

    @Test
    fun `continue is parsed`() {
        assertEquals(
            LifeSwitchPolicy.Decision.Continue("看完这集再说"),
            LifeSwitchPolicy.parse("继续|看完这集再说")
        )
    }

    @Test
    fun `switching is parsed with a valid place`() {
        val decision = LifeSwitchPolicy.parse("换|去楼下便利店买点吃的|outside.convenience|有点馋|有点饿了|顺便走走")
        assertTrue(decision is LifeSwitchPolicy.Decision.Switch)
        decision as LifeSwitchPolicy.Decision.Switch
        assertEquals("去楼下便利店买点吃的", decision.activity)
        assertEquals("outside.convenience", decision.locationId)
        assertEquals("有点饿了", decision.reason)
    }

    @Test
    fun `an unknown place is dropped instead of trusted`() {
        val decision = LifeSwitchPolicy.parse("换|去了个不存在的地方|not.a.real.place|还行|想去|")
        assertTrue(decision is LifeSwitchPolicy.Decision.Switch)
        assertNull("不在目录里的地点必须丢掉", (decision as LifeSwitchPolicy.Decision.Switch).locationId)
    }

    @Test
    fun `sleeping is parsed`() {
        assertEquals(LifeSwitchPolicy.Decision.Sleep("眼睛睁不开了"), LifeSwitchPolicy.parse("睡了|眼睛睁不开了"))
    }

    @Test
    fun `garbage means she just keeps doing what she was doing`() {
        listOf(
            "",
            "   ",
            "好的，我觉得你可以继续看剧。",
            "我建议你换个事情做",
            "换|",
            "换|x"
        ).forEach { garbage ->
            assertNull("认不出来就该返回 null（= 继续）：$garbage", LifeSwitchPolicy.parse(garbage))
        }
    }

    @Test
    fun `a markdown fence wrapped around a valid answer is tolerated`() {
        // 模型爱把结果包在 ``` 里。那只是包装，内容本身是有效的「继续」，不该因此作废。
        assertEquals(
            LifeSwitchPolicy.Decision.Continue(),
            LifeSwitchPolicy.parse("```\n继续\n```")
        )
    }

    @Test
    fun `an overlong activity sentence is rejected`() {
        val essay = "换|我本来在看剧但是突然觉得有点饿所以就想着要不要下楼去便利店买点吃的顺便走走看看|home|还行|饿了|"
        assertNull("写成一整句作文就不认", LifeSwitchPolicy.parse(essay))
    }

    @Test
    fun `the first meaningful line wins even if the model chats first`() {
        val raw = "好的，我明白了。\n继续|把这集看完"
        val decision = LifeSwitchPolicy.parse(raw)
        assertEquals(LifeSwitchPolicy.Decision.Continue("把这集看完"), decision)
    }

    // ---- 下次什么时候问 ----

    @Test
    fun `continuing asks again soon, sleeping does not`() {
        val rnd = Random(7)
        val cont = LifeSwitchPolicy.nextReviewAt(now, LifeSwitchPolicy.Decision.Continue(), rnd)
        assertTrue("继续的话 20~60 分钟后再问一次", cont - now in 20 * 60_000L..60 * 60_000L)

        val sleep = LifeSwitchPolicy.nextReviewAt(now, LifeSwitchPolicy.Decision.Sleep(), rnd)
        assertTrue("睡了就别老问，至少 6 小时", sleep - now >= 6 * 3_600_000L)
    }

    @Test
    fun `the review interval is not a fixed rhythm`() {
        val samples = (1..20).map {
            LifeSwitchPolicy.nextReviewAt(now, LifeSwitchPolicy.Decision.Continue(), Random(it))
        }
        assertTrue("每次问的间隔应该不一样", samples.toSet().size > 5)
    }

    // ---- 这一刻本身能存能读 ----

    @Test
    fun `a moment round trips through json`() {
        val m = moment()
        assertEquals(m, LivingMoment.fromJson(m.toJson()))
        assertNull(LivingMoment.fromJson(""))
        assertNull(LivingMoment.fromJson("not json"))
    }

    @Test
    fun `a moment knows its own age and whether it is due`() {
        val m = moment(startedAt = now - 90 * 60_000L)
        assertEquals(90 * 60_000L, m.durationMs(now))
        assertTrue(m.isDueForReview(now))
    }

    // ---- 作息仍然是「她的」，只是不再拿来排班 ----


    private fun input() = LifeSwitchPolicy.Input(
        characterName = "林小满",
        personalityPrompt = "24 岁，在书店上班，慢热。",
        relationshipPrompt = "刚认识不久的朋友",
        moment = moment(),
        now = now,
        zone = zone
    )
}
