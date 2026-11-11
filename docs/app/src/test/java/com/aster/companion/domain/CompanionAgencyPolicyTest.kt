package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可打扰性（[CompanionAgencyPolicy]）的纯逻辑测试。
 *
 * 契约分两层：
 * - **环境层**：睡着 / 被按下 / 他在回复 / 安静时段 / 设备不可用 → 一律不该打扰；
 * - **候选层**：私人内容不能公开说、配额到顶就放弃、普通联系要等间隔、**承诺可以绕过间隔**、
 *   手头有事时只有承诺与实际进展能突破。
 */
class CompanionAgencyPolicyTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun input(
        sleeping: Boolean = false,
        sleepUntil: Long = 0L,
        busyUntil: Long = 0L,
        locationId: String? = "home.living_room",
        conversationType: ConversationType? = ConversationType.PRIVATE,
        quietHours: Boolean = false,
        paused: Boolean = false,
        userReplying: Boolean = false,
        deviceAccess: CompanionAgencyPolicy.DeviceAccess = CompanionAgencyPolicy.DeviceAccess.AVAILABLE,
        sourceEntryIds: List<String> = emptyList(),
        maxWindowMinutes: Long = CompanionAgencyPolicy.DEFAULT_MAX_WINDOW_MINUTES
    ) = CompanionAgencyPolicy.Input(
        now = now,
        sleeping = sleeping,
        sleepUntil = sleepUntil,
        busyUntil = busyUntil,
        locationId = locationId,
        conversationType = conversationType,
        quietHours = quietHours,
        paused = paused,
        userReplying = userReplying,
        deviceAccess = deviceAccess,
        sourceEntryIds = sourceEntryIds,
        maxWindowMinutes = maxWindowMinutes
    )

    private fun window(
        load: CompanionAgencyPolicy.ActivityLoad = CompanionAgencyPolicy.ActivityLoad.FREE,
        privacy: CompanionAgencyPolicy.PrivacySetting = CompanionAgencyPolicy.PrivacySetting.PRIVATE,
        device: CompanionAgencyPolicy.DeviceAccess = CompanionAgencyPolicy.DeviceAccess.AVAILABLE,
        nextOpportunityAt: Long? = null,
        log: List<CompanionAgencyPolicy.ContactLogEntry> = emptyList()
    ) = CompanionAgencyPolicy.Window(
        activityLoad = load,
        privacy = privacy,
        deviceAccess = device,
        nextOpportunityAt = nextOpportunityAt,
        validUntil = now + hour,
        basis = "测试",
        sourceEntryIds = emptyList(),
        contactLog = log,
        updatedAt = now
    )

    private fun draft(
        origin: CompanionAgencyPolicy.ContactOrigin = CompanionAgencyPolicy.ContactOrigin.LIFE_EVENT,
        disclosure: CompanionAgencyPolicy.Disclosure = CompanionAgencyPolicy.Disclosure.ORDINARY,
        willingness: Double? = null,
        notBefore: Long? = null,
        expiresAt: Long? = null,
        participantId: String = "c1"
    ) = CompanionAgencyPolicy.ContactDraft(
        participantId = participantId,
        origin = origin,
        motive = "理由",
        disclosure = disclosure,
        sourceEntryIds = emptyList(),
        willingness = willingness,
        notBefore = notBefore,
        expiresAt = expiresAt
    )

    private fun logEntry(at: Long, participantId: String = "c1", origin: String = "LIFE_EVENT") =
        CompanionAgencyPolicy.ContactLogEntry(participantId, at, origin, "测试")

    // ---- 环境层 ----

    @Test
    fun `场合从已有事实推断，推不出来时保守当作自己家`() {
        assertEquals(
            CompanionAgencyPolicy.PrivacySetting.SHARED,
            CompanionAgencyPolicy.privacyOf("home.living_room", ConversationType.GROUP)
        )
        assertEquals(
            CompanionAgencyPolicy.PrivacySetting.PRIVATE,
            CompanionAgencyPolicy.privacyOf("home.living_room", ConversationType.PRIVATE)
        )
        assertEquals(
            CompanionAgencyPolicy.PrivacySetting.PUBLIC,
            CompanionAgencyPolicy.privacyOf("outside.office", ConversationType.PRIVATE)
        )
        assertEquals(
            CompanionAgencyPolicy.PrivacySetting.PRIVATE,
            CompanionAgencyPolicy.privacyOf(null, ConversationType.PRIVATE)
        )
        assertEquals(
            CompanionAgencyPolicy.PrivacySetting.PRIVATE,
            CompanionAgencyPolicy.privacyOf("home.unknown", ConversationType.PRIVATE)
        )
    }

    @Test
    fun `四种情形都算完全不该打扰`() {
        assertEquals(CompanionAgencyPolicy.ActivityLoad.OVERLOADED, CompanionAgencyPolicy.loadOf(input(sleeping = true)))
        assertEquals(CompanionAgencyPolicy.ActivityLoad.OVERLOADED, CompanionAgencyPolicy.loadOf(input(paused = true)))
        assertEquals(CompanionAgencyPolicy.ActivityLoad.OVERLOADED, CompanionAgencyPolicy.loadOf(input(userReplying = true)))
        assertEquals(CompanionAgencyPolicy.ActivityLoad.OVERLOADED, CompanionAgencyPolicy.loadOf(input(quietHours = true)))
    }

    @Test
    fun `手头有事算占用，没事算空闲`() {
        assertEquals(
            CompanionAgencyPolicy.ActivityLoad.OCCUPIED,
            CompanionAgencyPolicy.loadOf(input(busyUntil = now + 30 * minute))
        )
        assertEquals(
            CompanionAgencyPolicy.ActivityLoad.FREE,
            CompanionAgencyPolicy.loadOf(input(busyUntil = now - 1))
        )
    }

    /**
     * 环境硬门的语义。
     *
     * 原来直接断言 `windowAllows`——那个函数**已删除**：它没有调用方，而 `decide` 内部
     * 就覆盖了同样的判定（见下面"环境不该打扰时收起来等""设备不可用或受限时收起来等"）。
     * 这里改成从 `decide` 断言，**覆盖不变**。
     */
    @Test
    fun `环境硬门只看占用度与设备可用性`() {
        for (blocked in listOf(
            window(load = CompanionAgencyPolicy.ActivityLoad.OVERLOADED),
            window(device = CompanionAgencyPolicy.DeviceAccess.LIMITED),
            window(device = CompanionAgencyPolicy.DeviceAccess.UNAVAILABLE)
        )) {
            assertEquals(
                CompanionAgencyPolicy.Outcome.RECHECK_LATER,
                CompanionAgencyPolicy.decide(blocked, draft(), now = now).outcome
            )
        }
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(window(), draft(), now = now).outcome
        )
    }

    @Test
    fun `空闲在自己家时给出窗口有效期与可回溯来源`() {
        val result = CompanionAgencyPolicy.evaluate(input(sourceEntryIds = listOf("m1", "m2")))
        assertEquals(CompanionAgencyPolicy.ActivityLoad.FREE, result.activityLoad)
        assertEquals(CompanionAgencyPolicy.PrivacySetting.PRIVATE, result.privacy)
        assertNull(result.nextOpportunityAt)
        assertEquals(now + CompanionAgencyPolicy.DEFAULT_MAX_WINDOW_MINUTES * minute, result.validUntil)
        assertEquals(listOf("m1", "m2"), result.sourceEntryIds)
        assertTrue(result.basis.contains("她手上没事"))
        assertTrue(result.basis.contains("自己家"))
    }

    @Test
    fun `睡着时把下次机会指到睡醒`() {
        val sleepUntil = now + 90 * minute
        val result = CompanionAgencyPolicy.evaluate(input(sleeping = true, sleepUntil = sleepUntil))
        assertEquals(CompanionAgencyPolicy.ActivityLoad.OVERLOADED, result.activityLoad)
        assertEquals(sleepUntil, result.nextOpportunityAt)
        assertTrue(result.basis.contains("她在睡"))
    }

    @Test
    fun `设备受限时给出最短重查间隔并写进依据`() {
        val result = CompanionAgencyPolicy.evaluate(
            input(deviceAccess = CompanionAgencyPolicy.DeviceAccess.LIMITED)
        )
        assertEquals(now + CompanionAgencyPolicy.MIN_RECHECK_MS, result.nextOpportunityAt)
        assertTrue(result.basis.contains("设备=受限"))
        // 同一个窗亲密接触给裁决：受限一律先收起来等。
        assertEquals(
            CompanionAgencyPolicy.Outcome.RECHECK_LATER,
            CompanionAgencyPolicy.decide(result, draft(), now = now).outcome
        )
    }

    @Test
    fun `他在回复时依据说清楚是他在回复`() {
        val result = CompanionAgencyPolicy.evaluate(input(userReplying = true))
        assertTrue(result.basis.contains("正在这个会话里回复"))
    }

    @Test
    fun `窗口会把联系日志一起带下去`() {
        val previous = CompanionAgencyPolicy.Window(contactLog = listOf(logEntry(now - hour)))
        val result = CompanionAgencyPolicy.evaluate(input(), previous)

        assertEquals(1, result.contactLog.size)
    }

    @Test
    fun `联系日志是配额与间隔的唯一来源`() {
        val log = listOf(logEntry(now - 2 * hour), logEntry(now - hour), logEntry(now - 30 * minute, participantId = "c2"))
        assertEquals(2, CompanionAgencyPolicy.countContactsInWindow(log, "c1", now))
        assertEquals(1, CompanionAgencyPolicy.countContactsInWindow(log, "c2", now))
        assertEquals(0, CompanionAgencyPolicy.countContactsInWindow(log, "c3", now))
        // 超出 24 小时的旧记录不计数。
        assertEquals(0, CompanionAgencyPolicy.countContactsInWindow(listOf(logEntry(now - 25 * hour)), "c1", now))
        assertEquals(now - hour, CompanionAgencyPolicy.lastContactAt(log, "c1"))
        assertEquals(0L, CompanionAgencyPolicy.lastContactAt(log, "c9"))
    }

    // ---- 候选层：硬门 ----

    @Test
    fun `过期候选直接放弃`() {
        val decision = CompanionAgencyPolicy.decide(window(), draft(expiresAt = now - 1), now = now)
        assertEquals(CompanionAgencyPolicy.Outcome.LET_GO, decision.outcome)
        assertTrue(decision.reason.contains("过期"))
    }

    @Test
    fun `还没到点就收起来等`() {
        val at = now + 20 * minute
        val decision = CompanionAgencyPolicy.decide(window(), draft(notBefore = at), now = now)
        assertEquals(CompanionAgencyPolicy.Outcome.RECHECK_LATER, decision.outcome)
        assertEquals(at, decision.notBefore)
    }

    @Test
    fun `环境不该打扰时收起来等，任何来源都不放宽`() {
        val overloaded = window(
            load = CompanionAgencyPolicy.ActivityLoad.OVERLOADED,
            nextOpportunityAt = now + 30 * minute
        )
        for (origin in CompanionAgencyPolicy.ContactOrigin.entries) {
            val decision = CompanionAgencyPolicy.decide(overloaded, draft(origin = origin), now = now)
            assertEquals(origin.name, CompanionAgencyPolicy.Outcome.RECHECK_LATER, decision.outcome)
        }
    }

    @Test
    fun `设备不可用或受限时收起来等`() {
        val unavailable = window(device = CompanionAgencyPolicy.DeviceAccess.UNAVAILABLE)
        assertEquals(
            CompanionAgencyPolicy.Outcome.RECHECK_LATER,
            CompanionAgencyPolicy.decide(unavailable, draft(), now = now).outcome
        )
        val limited = window(device = CompanionAgencyPolicy.DeviceAccess.LIMITED)
        val decision = CompanionAgencyPolicy.decide(limited, draft(), now = now)
        assertEquals(CompanionAgencyPolicy.Outcome.RECHECK_LATER, decision.outcome)
        assertTrue(decision.reason.contains("显存"))
    }

    @Test
    fun `私人内容不能在非私密场合发`() {
        val shared = window(privacy = CompanionAgencyPolicy.PrivacySetting.SHARED)
        val blocked = CompanionAgencyPolicy.decide(
            shared,
            draft(disclosure = CompanionAgencyPolicy.Disclosure.PERSONAL),
            now = now
        )
        assertEquals(CompanionAgencyPolicy.Outcome.LET_GO, blocked.outcome)
        assertTrue(blocked.reason.contains("共同场合"))
        // 同一个场合，普通内容不受这条限制。
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(shared, draft(), now = now).outcome
        )
        // 私人内容在自己家可以发。
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(
                window(),
                draft(disclosure = CompanionAgencyPolicy.Disclosure.PERSONAL),
                now = now
            ).outcome
        )
    }

    @Test
    fun `到 24 小时配额上限就放弃`() {
        val config = CompanionAgencyPolicy.ContactPolicyConfig(dailyCap = 2)
        val log = listOf(logEntry(now - 3 * hour), logEntry(now - hour))
        val decision = CompanionAgencyPolicy.decide(window(log = log), draft(), config, now)
        assertEquals(CompanionAgencyPolicy.Outcome.LET_GO, decision.outcome)
        assertTrue(decision.reason.contains("到上限"))
    }

    @Test
    fun `配额为零表示不限`() {
        val config = CompanionAgencyPolicy.ContactPolicyConfig(dailyCap = 0)
        val log = List(5) { logEntry(now - (it + 1) * hour) }
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(window(log = log), draft(), config, now).outcome
        )
    }

    // ---- 候选层：间隔与来源 ----

    @Test
    fun `普通联系要等够最小间隔`() {
        val log = listOf(logEntry(now - 10 * minute))
        val decision = CompanionAgencyPolicy.decide(window(log = log), draft(), now = now)
        assertEquals(CompanionAgencyPolicy.Outcome.RECHECK_LATER, decision.outcome)
        assertEquals(now - 10 * minute + hour, decision.notBefore)
    }

    @Test
    fun `承诺可以绕过最小间隔`() {
        val log = listOf(logEntry(now - 10 * minute))
        val decision = CompanionAgencyPolicy.decide(
            window(log = log),
            draft(origin = CompanionAgencyPolicy.ContactOrigin.PROMISE),
            now = now
        )
        assertEquals(CompanionAgencyPolicy.Outcome.SEND_NOW, decision.outcome)
    }

    @Test
    fun `手头有事时只有承诺与实际进展能突破`() {
        val occupied = window(load = CompanionAgencyPolicy.ActivityLoad.OCCUPIED)
        assertEquals(
            CompanionAgencyPolicy.Outcome.RECHECK_LATER,
            CompanionAgencyPolicy.decide(occupied, draft(origin = CompanionAgencyPolicy.ContactOrigin.LIFE_EVENT), now = now).outcome
        )
        assertEquals(
            CompanionAgencyPolicy.Outcome.RECHECK_LATER,
            CompanionAgencyPolicy.decide(
                occupied,
                draft(origin = CompanionAgencyPolicy.ContactOrigin.RELATIONSHIP_FOLLOW_UP),
                now = now
            ).outcome
        )
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(occupied, draft(origin = CompanionAgencyPolicy.ContactOrigin.PROMISE), now = now).outcome
        )
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(
                occupied,
                draft(origin = CompanionAgencyPolicy.ContactOrigin.PRACTICAL_UPDATE),
                now = now
            ).outcome
        )
    }

    // ---- 候选层：意愿闸 ----

    @Test
    fun `带了意愿值就必须达阈值，没带则跳过`() {
        val strict = CompanionAgencyPolicy.ContactPolicyConfig()
        val low = CompanionAgencyPolicy.decide(window(), draft(willingness = 0.3), strict, now)
        assertEquals(CompanionAgencyPolicy.Outcome.LET_GO, low.outcome)
        assertTrue(low.reason.contains("意愿"))
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(window(), draft(willingness = 0.9), strict, now).outcome
        )
        // 承诺类候选不带意愿，直接跳过这一闸。
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(window(), draft(willingness = null), strict, now).outcome
        )
    }

    // ---- 联系温度三档 ----

    @Test
    fun `严格档用配置的间隔与阈值`() {
        val strict = CompanionAgencyPolicy.ContactPolicyConfig()
        assertEquals(CompanionAgencyPolicy.ContactMode.STRICT, strict.mode)
        assertEquals(hour, strict.effectiveIntervalMs)
        assertEquals(0.65, strict.effectiveWillingnessThreshold, 0.0001)
    }

    @Test
    fun `宽松档取更小的间隔与阈值，且只在更小时才生效`() {
        val natural = CompanionAgencyPolicy.ContactPolicyConfig(mode = CompanionAgencyPolicy.ContactMode.NATURAL)
        assertEquals(30 * minute, natural.effectiveIntervalMs)
        assertEquals(0.25, natural.effectiveWillingnessThreshold, 0.0001)
        // natural 给的间隔比严格档更大时不该放宽。
        val larger = CompanionAgencyPolicy.ContactPolicyConfig(
            mode = CompanionAgencyPolicy.ContactMode.NATURAL,
            minimumIntervalMs = 20 * minute,
            naturalMinimumIntervalMs = 45 * minute
        )
        assertEquals(20 * minute, larger.effectiveIntervalMs)
    }

    @Test
    fun `宽松档让同一候选通过而严格档不通过`() {
        val log = listOf(logEntry(now - 40 * minute))
        val candidate = draft(willingness = 0.3)
        assertEquals(
            CompanionAgencyPolicy.Outcome.RECHECK_LATER,
            CompanionAgencyPolicy.decide(window(log = log), candidate, CompanionAgencyPolicy.ContactPolicyConfig(), now).outcome
        )
        val natural = CompanionAgencyPolicy.ContactPolicyConfig(mode = CompanionAgencyPolicy.ContactMode.NATURAL)
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(window(log = log), candidate, natural, now).outcome
        )
        // balanced 与 natural 同教义。
        val balanced = CompanionAgencyPolicy.ContactPolicyConfig(mode = CompanionAgencyPolicy.ContactMode.BALANCED)
        assertEquals(
            CompanionAgencyPolicy.Outcome.SEND_NOW,
            CompanionAgencyPolicy.decide(window(log = log), candidate, balanced, now).outcome
        )
    }

    // ---- 记账与序列化 ----

    @Test
    fun `记账追加日志并只保留最近若干条`() {
        var w = window()
        repeat(CompanionAgencyPolicy.CONTACT_LOG_LIMIT + 3) { index ->
            w = CompanionAgencyPolicy.recordSpoke(w, logEntry(now + index))
        }
        assertEquals(CompanionAgencyPolicy.CONTACT_LOG_LIMIT, w.contactLog.size)
        assertEquals(now + CompanionAgencyPolicy.CONTACT_LOG_LIMIT + 2, w.contactLog.last().at)
        // 这些记录都在 24 小时窗口内，所以计数等于日志长度（超出窗口的排除另有用例覆盖）。
        assertEquals(
            CompanionAgencyPolicy.CONTACT_LOG_LIMIT,
            CompanionAgencyPolicy.countContactsInWindow(
                w.contactLog,
                "c1",
                now + CompanionAgencyPolicy.CONTACT_LOG_LIMIT + 2
            )
        )
    }

    @Test
    fun `窗口可以 json 往返，坏数据退回默认值`() {
        val full = CompanionAgencyPolicy.Window(
            activityLoad = CompanionAgencyPolicy.ActivityLoad.OCCUPIED,
            privacy = CompanionAgencyPolicy.PrivacySetting.PUBLIC,
            deviceAccess = CompanionAgencyPolicy.DeviceAccess.LIMITED,
            nextOpportunityAt = now,
            validUntil = now + hour,
            basis = "测试",
            sourceEntryIds = listOf("m1"),
            contactLog = listOf(logEntry(now - hour)),
            updatedAt = now
        )
        assertEquals(full, CompanionAgencyPolicy.Window.fromJson(full.toJson()))
        assertEquals(CompanionAgencyPolicy.Window(), CompanionAgencyPolicy.Window.fromJson("不是 json"))
        assertEquals(CompanionAgencyPolicy.Window(), CompanionAgencyPolicy.Window.fromJson(null))
    }

    @Test
    fun `scope 与主键名保持稳定，因为它是要落库的约定`() {
        assertEquals("AGENCY_WINDOW", CompanionAgencyPolicy.SCOPE)
        assertEquals("agency-window:thread-1", CompanionAgencyPolicy.windowId("thread-1"))
    }
}
