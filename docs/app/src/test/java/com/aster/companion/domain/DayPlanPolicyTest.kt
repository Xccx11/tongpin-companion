package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日程预排：块分级、日期例外、投影窗口、每日审查。
 *
 * 三条要钉死的纪律：**计划不是事实**；冲突让位给更硬的块；**非法提案整体拒绝**（旧计划继续有效）。
 */
class DayPlanPolicyTest {

    private val now = 1_700_000_000_000L
    private val date = "2026-10-10"

    private fun block(
        kind: DayPlanPolicy.BlockKind = DayPlanPolicy.BlockKind.ROUTINE,
        start: Int,
        end: Int,
        title: String = "事情",
        days: Set<Int> = emptySet(),
        on: String? = null
    ) = DayPlanPolicy.Block(kind, start, end, title, days, on)

    private fun plan(vararg blocks: DayPlanPolicy.Block) = DayPlanPolicy.Plan(blocks = blocks.toList())

    // ---- 解析 ----

    @Test
    fun `解析一行正常写法`() {
        val parsed = DayPlanPolicy.parseLine("FIXED 09:00-18:00 上班")

        assertNotNull(parsed)
        assertEquals(DayPlanPolicy.BlockKind.FIXED, parsed!!.kind)
        assertEquals(540, parsed.startMinute)
        assertEquals(1080, parsed.endMinute)
        assertEquals("上班", parsed.title)
    }

    @Test
    fun `解析对大小写、全角冒号、各种分隔符都宽容`() {
        val parsed = DayPlanPolicy.parseLine("- routine 9：00～18:00   通勤  ")

        assertNotNull(parsed)
        assertEquals(DayPlanPolicy.BlockKind.ROUTINE, parsed!!.kind)
        assertEquals(540, parsed.startMinute)
        assertEquals("通勤", parsed.title)
    }

    @Test
    fun `认不出来的行一律 null`() {
        assertNull("未知分级", DayPlanPolicy.parseLine("MEETING 09:00-10:00 开会"))
        assertNull("没有时间", DayPlanPolicy.parseLine("FIXED 上班"))
        assertNull("小时越界", DayPlanPolicy.parseLine("FIXED 25:00-26:00 事情"))
        assertNull("结束早于开始", DayPlanPolicy.parseLine("FIXED 09:00-09:00 事情"))
        assertNull("没有说明", DayPlanPolicy.parseLine("FIXED 09:00-10:00 "))
        assertNull("空行", DayPlanPolicy.parseLine("   "))
    }

    /** **任何一行不合法就整体拒绝**：不许"跳过坏行留下好行"。 */
    @Test
    fun `提案里有一行坏的就整体拒绝`() {
        assertNull(DayPlanPolicy.parseProposal("FIXED 09:00-10:00 上班\n这行是坏的"))
        assertNull(DayPlanPolicy.parseProposal("FIXED 09:00-10:00 上班\nFIXED 25:00-26:00 坏时间"))
    }

    @Test
    fun `提案是 NONE 表示什么都不改`() {
        assertEquals(emptyList<DayPlanPolicy.Block>(), DayPlanPolicy.parseProposal("NONE"))
        assertEquals(emptyList<DayPlanPolicy.Block>(), DayPlanPolicy.parseProposal("none"))
    }

    @Test
    fun `带 markdown 围栏的输出整体拒绝`() {
        assertNull(DayPlanPolicy.parseProposal("```\nFIXED 09:00-10:00 上班\n```"))
    }

    @Test
    fun `多行提案全部解析出来`() {
        val blocks = DayPlanPolicy.parseProposal(
            "FIXED 09:00-12:00 上班\nROUTINE 12:00-13:00 午休\nOPEN 13:00-18:00 空闲"
        )

        assertEquals(3, blocks?.size)
        assertEquals(DayPlanPolicy.BlockKind.OPEN, blocks!!.last().kind)
    }

    @Test
    fun `审查结论行可以解析`() {
        assertEquals(DayPlanPolicy.Proposal.PATCH, DayPlanPolicy.parseProposalKind("patch"))
        assertEquals(DayPlanPolicy.Proposal.UNCHANGED, DayPlanPolicy.parseProposalKind(" UNCHANGED "))
        assertNull(DayPlanPolicy.parseProposalKind("REWRITE"))
        assertNull(DayPlanPolicy.parseProposalKind(null))
    }

    // ---- 解读一次审查输出 ----

    @Test
    fun `第一行是结论，后面是块`() {
        val review = DayPlanPolicy.parseReview("PATCH\nFIXED 09:00-10:00 上班")

        assertEquals(DayPlanPolicy.Proposal.PATCH, review.proposal)
        assertEquals(1, review.blocks?.size)
    }

    /** 模型少写一行结论时**不该整份作废**：整段当块、按 REPLACE 处理。 */
    @Test
    fun `认不出结论时整段当作块`() {
        val review = DayPlanPolicy.parseReview("FIXED 09:00-10:00 上班\nOPEN 10:00-11:00 空闲")

        assertEquals(DayPlanPolicy.Proposal.REPLACE, review.proposal)
        assertEquals(2, review.blocks?.size)
    }

    @Test
    fun `结论是 UNCHANGED 时不看后面的块`() {
        val review = DayPlanPolicy.parseReview("UNCHANGED")

        assertEquals(DayPlanPolicy.Proposal.UNCHANGED, review.proposal)
        assertEquals(emptyList<DayPlanPolicy.Block>(), review.blocks)
    }

    @Test
    fun `空输出按什么都不改处理`() {
        val review = DayPlanPolicy.parseReview("")

        assertEquals(DayPlanPolicy.Proposal.UNCHANGED, review.proposal)
        assertNull(review.blocks)
    }

    @Test
    fun `块里有一行坏的，整份仍然拒绝`() {
        val review = DayPlanPolicy.parseReview("REPLACE\nFIXED 09:00-10:00 上班\n坏行")

        assertEquals(DayPlanPolicy.Proposal.REPLACE, review.proposal)
        assertNull(review.blocks)
    }

    @Test
    fun `星期与日期格式`() {
        assertEquals(1, DayPlanPolicy.weekdayOf("2024-01-01"))
        assertNull(DayPlanPolicy.weekdayOf("不是日期"))
        assertEquals("09:00", DayPlanPolicy.formatMinute(540))
        assertEquals("23:59", DayPlanPolicy.formatMinute(24 * 60))
    }

    // ---- 适用性与冲突 ----

    @Test
    fun `日期例外只在那一天生效`() {
        val exception = block(start = 540, end = 600, on = date)

        assertTrue(exception.appliesOn(date, weekday = 6))
        assertFalse(exception.appliesOn("2026-10-11", weekday = 7))
    }

    @Test
    fun `没有星期限定的块每天都生效`() {
        assertTrue(block(start = 540, end = 600).appliesOn(date, weekday = 6))
        assertTrue(block(start = 540, end = 600, days = setOf(6)).appliesOn(date, weekday = 6))
        assertFalse(block(start = 540, end = 600, days = setOf(1)).appliesOn(date, weekday = 6))
    }

    @Test
    fun `冲突时让位给更硬的块`() {
        val resolved = DayPlanPolicy.resolve(
            plan(
                block(DayPlanPolicy.BlockKind.OPEN, 570, 630, "空闲"),
                block(DayPlanPolicy.BlockKind.FIXED, 540, 600, "上班")
            ),
            date, weekday = 6, fromMinute = 0
        )

        assertEquals(1, resolved.size)
        assertEquals("上班", resolved.single().title)
    }

    @Test
    fun `同一段时间同级的块，日期例外赢`() {
        val resolved = DayPlanPolicy.resolve(
            plan(
                block(DayPlanPolicy.BlockKind.ROUTINE, 540, 600, "常规"),
                block(DayPlanPolicy.BlockKind.ROUTINE, 540, 600, "例外", on = date)
            ),
            date, weekday = 6, fromMinute = 0
        )

        assertEquals("例外", resolved.single().title)
    }

    @Test
    fun `正在进行的块也算在内`() {
        val resolved = DayPlanPolicy.resolve(
            plan(block(start = 540, end = 720, title = "上班")),
            date, weekday = 6, fromMinute = 600
        )

        assertEquals(1, resolved.size)
    }

    @Test
    fun `超出投影窗口的块不算`() {
        val resolved = DayPlanPolicy.resolve(
            plan(block(start = 0, end = 60, title = "凌晨")),
            date, weekday = 6, fromMinute = 600, horizonMinutes = 60
        )

        assertEquals(0, resolved.size)
    }

    @Test
    fun `投影按开始时间排序并截到上限`() {
        val blocks = (0 until 5).map { block(start = it * 60, end = it * 60 + 30, title = "第$it 段") }

        val resolved = DayPlanPolicy.resolve(plan(*blocks.toTypedArray()), date, weekday = 6, fromMinute = 0, maxBlocks = 3)

        assertEquals(3, resolved.size)
        assertEquals(listOf(0, 60, 120), resolved.map { it.startMinute })
    }

    // ---- 每日审查 ----

    @Test
    fun `同一天只审一次，且要过了凌晨三点`() {
        assertTrue(DayPlanPolicy.needsReview(DayPlanPolicy.Plan(reviewedOn = "2026-10-09"), date, localHour = 10))
        assertFalse("同一天不再审", DayPlanPolicy.needsReview(DayPlanPolicy.Plan(reviewedOn = date), date, localHour = 10))
        assertFalse("太早不审", DayPlanPolicy.needsReview(DayPlanPolicy.Plan(reviewedOn = "2026-10-09"), date, localHour = 2))
        assertTrue(DayPlanPolicy.needsReview(DayPlanPolicy.Plan(reviewedOn = "2026-10-09"), date, localHour = 3))
    }

    @Test
    fun `非法提案原样保留旧计划，但仍盖上审查日期`() {
        val old = plan(block(start = 540, end = 600, title = "上班"))

        val kept = DayPlanPolicy.apply(old, DayPlanPolicy.Proposal.REPLACE, blocks = null, dateKey = date, now = now)

        assertEquals(old.blocks, kept.blocks)
        assertEquals("什么都不该变", old.revision, kept.revision)
        assertEquals(date, kept.reviewedOn)
    }

    @Test
    fun `UNCHANGED 什么都不改`() {
        val old = plan(block(start = 540, end = 600, title = "上班"))

        val kept = DayPlanPolicy.apply(old, DayPlanPolicy.Proposal.UNCHANGED, emptyList(), date, now)

        assertEquals(old.blocks, kept.blocks)
        assertEquals(old.revision, kept.revision)
        assertEquals(date, kept.reviewedOn)
    }

    @Test
    fun `EXTEND 追加、REPLACE 整体换掉、PATCH 按分级与起点替换`() {
        val old = plan(block(DayPlanPolicy.BlockKind.FIXED, 540, 600, "上班"))

        val extended = DayPlanPolicy.apply(
            old, DayPlanPolicy.Proposal.EXTEND,
            listOf(block(DayPlanPolicy.BlockKind.ROUTINE, 720, 780, "午休")), date, now
        )
        assertEquals(2, extended.blocks.size)

        val replaced = DayPlanPolicy.apply(
            old, DayPlanPolicy.Proposal.REPLACE,
            listOf(block(DayPlanPolicy.BlockKind.OPEN, 540, 600, "睡到中午")), date, now
        )
        assertEquals(listOf("睡到中午"), replaced.blocks.map { it.title })

        val patched = DayPlanPolicy.apply(
            old, DayPlanPolicy.Proposal.PATCH,
            listOf(block(DayPlanPolicy.BlockKind.FIXED, 540, 660, "上班到 11 点")), date, now
        )
        assertEquals(listOf("上班到 11 点"), patched.blocks.map { it.title })
    }

    @Test
    fun `生效的审查会推进版本号`() {
        val old = plan(block(start = 540, end = 600, title = "上班"))

        val next = DayPlanPolicy.apply(
            old, DayPlanPolicy.Proposal.REPLACE,
            listOf(block(start = 600, end = 660, title = "新的")), date, now
        )

        assertEquals(old.revision + 1, next.revision)
        assertEquals(now, next.updatedAt)
    }

    // ---- 注入 ----

    @Test
    fun `没有块时不产出任何文字`() {
        assertEquals("", DayPlanPolicy.render(emptyList()))
    }

    @Test
    fun `注入必须写明这是计划而不是已发生的事`() {
        val rendered = DayPlanPolicy.render(
            listOf(
                block(DayPlanPolicy.BlockKind.FIXED, 540, 720, "上班"),
                block(DayPlanPolicy.BlockKind.ROUTINE, 720, 780, "午休")
            )
        )

        assertTrue(rendered.contains("计划"))
        assertTrue(rendered.contains("不是已经发生"))
        assertTrue(rendered.contains("09:00-12:00 上班"))
        assertTrue(rendered.contains("12:00-13:00 午休"))
    }

    // ---- 序列化与约定 ----

    @Test
    fun `计划可以 json 往返，坏数据退回空计划`() {
        val full = DayPlanPolicy.Plan(
            blocks = listOf(block(DayPlanPolicy.BlockKind.FIXED, 540, 600, "上班", days = setOf(1, 2, 3))),
            reviewedOn = date,
            revision = 4,
            updatedAt = now
        )

        assertEquals(full, DayPlanPolicy.Plan.fromJson(full.toJson()))
        assertEquals(DayPlanPolicy.Plan(), DayPlanPolicy.Plan.fromJson("不是 json"))
        assertEquals(DayPlanPolicy.Plan(), DayPlanPolicy.Plan.fromJson(null))
    }

    @Test
    fun `scope 与主键名是落库约定，必须稳定`() {
        assertEquals("DAY_PLAN", DayPlanPolicy.SCOPE)
        assertEquals("day-plan:c1", DayPlanPolicy.rowId("c1"))
    }

    @Test
    fun `块分级顺序就是冲突优先级`() {
        val order = DayPlanPolicy.BlockKind.entries.map { it.name }

        assertEquals(listOf("FIXED", "ROUTINE", "FLEXIBLE", "OPEN"), order)
    }
}
