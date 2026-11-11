package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设定提案：四重门、证据累积、结算与 overlay。
 *
 * 要钉死的：**同一天说十遍不算数**（必须跨天）；重大变更门槛更高；
 * 冷却会挡住"一次对话就把她改成另一个人"。
 */
class StatePatchPolicyTest {

    private val now = 1_700_000_000_000L
    private val hour = 3600_000L

    private fun proposal(
        kind: StatePatchPolicy.Kind = StatePatchPolicy.Kind.HABIT,
        text: String = "习惯早起",
        confidence: Double = 0.9,
        turns: Int = 3,
        days: Set<String> = setOf("2026-10-10", "2026-10-11"),
        firstSeenAt: Long = now - hour,
        status: StatePatchPolicy.Status = StatePatchPolicy.Status.PENDING,
        appliedAt: Long = 0L
    ) = StatePatchPolicy.Proposal(
        id = "p",
        kind = kind,
        text = text,
        confidence = confidence,
        evidenceCount = turns,
        turnCount = turns,
        dayKeys = days,
        firstSeenAt = firstSeenAt,
        lastSeenAt = now,
        status = status,
        appliedAt = appliedAt
    )

    private fun candidate(
        text: String = "习惯早起",
        kind: StatePatchPolicy.Kind = StatePatchPolicy.Kind.HABIT,
        confidence: Double = 0.9
    ) = StatePatchPolicy.Candidate(kind, text, confidence)

    // ---- 解析 ----

    @Test
    fun `解析出带分级与不带分级的提案行`() {
        val parsed = StatePatchPolicy.parseCandidates(
            "PATCH[HABIT]: 习惯早起\nPATCH: 最近在学做菜\n这是一句普通的话\nPATCH[relationship]：把他当成男朋友"
        )

        assertEquals(3, parsed.size)
        assertEquals(StatePatchPolicy.Kind.HABIT, parsed[0].kind)
        assertEquals(StatePatchPolicy.Kind.CIRCUMSTANCE, parsed[1].kind)
        assertEquals(StatePatchPolicy.Kind.RELATIONSHIP, parsed[2].kind)
        assertEquals("把他当成男朋友", parsed[2].text)
    }

    @Test
    fun `没有提案行时给空列表`() {
        assertTrue(StatePatchPolicy.parseCandidates("").isEmpty())
        assertTrue(StatePatchPolicy.parseCandidates(null).isEmpty())
        assertTrue(StatePatchPolicy.parseCandidates("今天聊了天气").isEmpty())
        assertTrue(StatePatchPolicy.parseCandidates("PATCH:   ").isEmpty())
    }

    @Test
    fun `提案文字按上限截断`() {
        val parsed = StatePatchPolicy.parseCandidates("PATCH: " + "字".repeat(200))

        assertEquals(StatePatchPolicy.MAX_TEXT_CHARS, parsed.single().text.length)
    }

    // ---- 证据累积 ----

    @Test
    fun `新候选建为待审`() {
        val book = StatePatchPolicy.observe(StatePatchPolicy.Book(), listOf(candidate()), now, "2026-10-10")

        val one = book.proposals.single()
        assertEquals(1, one.turnCount)
        assertEquals(1, one.evidenceCount)
        assertEquals(setOf("2026-10-10"), one.dayKeys)
        assertEquals(StatePatchPolicy.Status.PENDING, one.status)
    }

    @Test
    fun `同一条反复出现会累积次数，但同一天不加天数`() {
        var book = StatePatchPolicy.observe(StatePatchPolicy.Book(), listOf(candidate()), now, "2026-10-10")
        book = StatePatchPolicy.observe(book, listOf(candidate()), now + hour, "2026-10-10")
        book = StatePatchPolicy.observe(book, listOf(candidate()), now + 2 * hour, "2026-10-10")

        val one = book.proposals.single()
        assertEquals(3, one.turnCount)
        assertEquals("同一天说十遍也只算一天", 1, one.dayKeys.size)
    }

    @Test
    fun `跨天会累加天数`() {
        var book = StatePatchPolicy.observe(StatePatchPolicy.Book(), listOf(candidate()), now, "2026-10-10")
        book = StatePatchPolicy.observe(book, listOf(candidate()), now + 24 * hour, "2026-10-11")

        assertEquals(2, book.proposals.single().dayKeys.size)
    }

    @Test
    fun `可信度取历史最高`() {
        var book = StatePatchPolicy.observe(StatePatchPolicy.Book(), listOf(candidate(confidence = 0.9)), now, "d1")
        book = StatePatchPolicy.observe(book, listOf(candidate(confidence = 0.4)), now + hour, "d2")

        assertEquals(0.9, book.proposals.single().confidence, 1e-9)
    }

    @Test
    fun `说的是两件事就是两条提案`() {
        val book = StatePatchPolicy.observe(
            StatePatchPolicy.Book(),
            listOf(candidate(text = "习惯早起"), candidate(text = "最近在学做菜")),
            now,
            "d1"
        )

        assertEquals(2, book.proposals.size)
    }

    @Test
    fun `待审队列封顶，丢最旧的`() {
        // 候选之间必须**真的不像**：写成"第 N 条完全不同的事"、甚至"事1 与 事12"都会被去重合并
        // （后者共享二元组 `事1`，Jaccard 正好 0.5 就达阈值）。这里让每条的第二字互不相同。
        val distinct = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰"
        val many = distinct.map { candidate(text = "事$it") }

        val book = StatePatchPolicy.observe(StatePatchPolicy.Book(), many, now, "d1")

        assertEquals(StatePatchPolicy.MAX_PENDING, book.proposals.count { it.status == StatePatchPolicy.Status.PENDING })
    }

    // ---- 四重门 ----

    @Test
    fun `四重门全过才通过`() {
        assertNull(StatePatchPolicy.gate(proposal(), now, lastAppliedAt = 0L))
    }

    @Test
    fun `证据不够天数就不过`() {
        val reason = StatePatchPolicy.gate(proposal(days = setOf("2026-10-10")), now, 0L)

        assertNotNull(reason)
        assertTrue(reason!!.contains("天"))
    }

    @Test
    fun `提起次数不够就不过`() {
        val reason = StatePatchPolicy.gate(proposal(turns = 2), now, 0L)

        assertNotNull(reason)
        assertTrue(reason!!.contains("次"))
    }

    @Test
    fun `可信度不够就不过`() {
        val reason = StatePatchPolicy.gate(proposal(confidence = 0.5), now, 0L)

        assertNotNull(reason)
        assertTrue(reason!!.contains("可信度"))
    }

    /** 关系定位是重大变更：普通阈值够了也不行。 */
    @Test
    fun `关系定位要用更高的可信度门槛`() {
        val normal = StatePatchPolicy.gate(
            proposal(kind = StatePatchPolicy.Kind.HABIT, confidence = 0.85), now, 0L
        )
        val major = StatePatchPolicy.gate(
            proposal(kind = StatePatchPolicy.Kind.RELATIONSHIP, confidence = 0.85), now, 0L
        )

        assertNull("普通变更 0.85 够了", normal)
        assertNotNull("关系定位 0.85 不够", major)
        assertTrue(major!!.contains(StatePatchPolicy.GATE_CONFIDENCE_MAJOR.toString()))
    }

    @Test
    fun `冷却期内别的提案也过不了`() {
        val reason = StatePatchPolicy.gate(proposal(), now, lastAppliedAt = now - hour)

        assertNotNull(reason)
        assertTrue(reason!!.contains("小时"))
    }

    // ---- 结算 ----

    @Test
    fun `过门的转生效并记下时刻`() {
        val book = StatePatchPolicy.Book(proposals = listOf(proposal()))

        val settled = StatePatchPolicy.settle(book, now)

        assertEquals(StatePatchPolicy.Status.APPLIED, settled.proposals.single().status)
        assertEquals(now, settled.proposals.single().appliedAt)
        assertEquals(now, settled.lastAppliedAt)
    }

    @Test
    fun `攒太久没凑够的作废`() {
        val stale = proposal(firstSeenAt = now - StatePatchPolicy.EXPIRE_MS - 1, turns = 1, days = setOf("d"))

        val settled = StatePatchPolicy.settle(StatePatchPolicy.Book(proposals = listOf(stale)), now)

        assertEquals(StatePatchPolicy.Status.EXPIRED, settled.proposals.single().status)
        assertTrue(settled.proposals.single().reason.contains("一个月"))
    }

    @Test
    fun `一次结算里只放行一条（冷却立刻生效）`() {
        val book = StatePatchPolicy.Book(
            proposals = listOf(proposal(text = "甲"), proposal(text = "乙"), proposal(text = "丙"))
        )

        val settled = StatePatchPolicy.settle(book, now)

        assertEquals(1, settled.proposals.count { it.status == StatePatchPolicy.Status.APPLIED })
    }

    @Test
    fun `已生效与已作废的记录不再改动`() {
        val applied = proposal(status = StatePatchPolicy.Status.APPLIED, appliedAt = now - 1000)
        val rejected = proposal(text = "另一条", status = StatePatchPolicy.Status.REJECTED)

        val settled = StatePatchPolicy.settle(StatePatchPolicy.Book(proposals = listOf(applied, rejected)), now)

        assertEquals(applied, settled.proposals[0])
        assertEquals(rejected, settled.proposals[1])
    }

    // ---- overlay 与注入 ----

    @Test
    fun `overlay 按生效时间新在前并封顶`() {
        val many = (0 until StatePatchPolicy.MAX_OVERLAYS + 3).map { index ->
            proposal(
                text = "第 $index 条",
                status = StatePatchPolicy.Status.APPLIED,
                appliedAt = now + index
            )
        }

        val overlays = StatePatchPolicy.overlays(StatePatchPolicy.Book(proposals = many))

        assertEquals(StatePatchPolicy.MAX_OVERLAYS, overlays.size)
        assertEquals("第 ${StatePatchPolicy.MAX_OVERLAYS + 2} 条", overlays.first())
    }

    @Test
    fun `没有 overlay 时不注入`() {
        assertEquals("", StatePatchPolicy.render(StatePatchPolicy.Book()))
        assertEquals("", StatePatchPolicy.render(StatePatchPolicy.Book(proposals = listOf(proposal()))))
    }

    @Test
    fun `注入写明这是慢慢长出来的、不必特意提`() {
        val rendered = StatePatchPolicy.render(
            StatePatchPolicy.Book(proposals = listOf(proposal(text = "习惯早起，六点多就醒了", status = StatePatchPolicy.Status.APPLIED)))
        )

        assertTrue(rendered.contains("慢慢长出来的"))
        assertTrue(rendered.contains("不必特意解释或提起"))
        assertTrue(rendered.contains("习惯早起，六点多就醒了"))
    }

    // ---- 相似度与序列化 ----

    @Test
    fun `相似度是字符二元组 Jaccard`() {
        assertEquals(1.0, StatePatchPolicy.similarity("习惯早起", "习惯早起"), 1e-9)
        assertEquals(0.0, StatePatchPolicy.similarity("abc", "xyz"), 1e-9)
    }

    @Test
    fun `书可以 json 往返，坏数据退回空本`() {
        val full = StatePatchPolicy.Book(
            proposals = listOf(proposal(status = StatePatchPolicy.Status.APPLIED, appliedAt = now)),
            lastAppliedAt = now
        )

        assertEquals(full, StatePatchPolicy.Book.fromJson(full.toJson()))
        assertEquals(StatePatchPolicy.Book(), StatePatchPolicy.Book.fromJson("不是 json"))
        assertEquals(StatePatchPolicy.Book(), StatePatchPolicy.Book.fromJson(null))
    }

    @Test
    fun `scope 与主键名是落库约定，必须稳定`() {
        assertEquals("STATE_PATCH", StatePatchPolicy.SCOPE)
        assertEquals("state-patch:c1", StatePatchPolicy.rowId("c1"))
    }

    @Test
    fun `只有关系定位算重大变更`() {
        assertTrue(StatePatchPolicy.isMajor(StatePatchPolicy.Kind.RELATIONSHIP))
        assertFalse(StatePatchPolicy.isMajor(StatePatchPolicy.Kind.HABIT))
        assertFalse(StatePatchPolicy.isMajor(StatePatchPolicy.Kind.PREFERENCE))
        assertFalse(StatePatchPolicy.isMajor(StatePatchPolicy.Kind.CIRCUMSTANCE))
    }
}
