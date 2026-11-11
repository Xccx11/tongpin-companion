package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长线叙事：权重累计、复审时机、催化三态、注入许可。
 *
 * 要钉死的四条：权重写死三档；**dormant 不覆盖 activate**；prime/activate 必须带表现与走向；
 * 注入末尾**必须**带那条禁令。
 */
class LongArcPolicyTest {

    private val now = 1_700_000_000_000L

    private fun state(
        weight: Double = 0.0,
        catalyst: LongArcPolicy.Catalyst = LongArcPolicy.Catalyst.DORMANT,
        expression: String = "",
        branches: List<String> = emptyList(),
        reviewedWeight: Double = 0.0,
        lastReviewedAt: Long = 0L
    ) = LongArcPolicy.State(weight, catalyst, expression, branches, reviewedWeight, lastReviewedAt)

    private val private_ = LongArcPolicy.Source.PRIVATE

    // ---- 权重 ----

    @Test
    fun `权重三档写死`() {
        assertEquals(1.0, LongArcPolicy.weightOf(LongArcPolicy.Source.PRIVATE), 1e-9)
        assertEquals(0.5, LongArcPolicy.weightOf(LongArcPolicy.Source.GROUP), 1e-9)
        assertEquals(0.0, LongArcPolicy.weightOf(LongArcPolicy.Source.UNKNOWN), 1e-9)
    }

    @Test
    fun `私聊一回合加一分，群聊加半分`() {
        assertEquals(1.0, LongArcPolicy.observe(state(), private_).weight, 1e-9)
        assertEquals(0.5, LongArcPolicy.observe(state(), LongArcPolicy.Source.GROUP).weight, 1e-9)
    }

    @Test
    fun `来源不明一分不加`() {
        val base = state(weight = 7.0)

        assertEquals(base, LongArcPolicy.observe(base, LongArcPolicy.Source.UNKNOWN))
    }

    @Test
    fun `累计是累加而不是覆盖`() {
        var current = state()
        repeat(25) { current = LongArcPolicy.observe(current, private_) }

        assertEquals(25.0, current.weight, 1e-9)
    }

    // ---- 复审时机 ----

    @Test
    fun `没到阈值不复审`() {
        assertFalse(LongArcPolicy.dueForReview(state(weight = 24.9)))
    }

    @Test
    fun `到阈值且从没审过就要审第一次`() {
        assertTrue(LongArcPolicy.dueForReview(state(weight = 25.0)))
    }

    @Test
    fun `审过之后要再累计一个增量才复审`() {
        val reviewed = state(weight = 25.0, lastReviewedAt = now, reviewedWeight = 25.0)

        assertFalse(LongArcPolicy.dueForReview(reviewed.copy(weight = 60.0)))
        assertTrue(LongArcPolicy.dueForReview(reviewed.copy(weight = 65.0)))
    }

    @Test
    fun `没到阈值时即使过了很久也不复审`() {
        val stale = state(weight = 10.0, lastReviewedAt = now - 30 * 24 * 3600_000L, reviewedWeight = 0.0)

        assertFalse(LongArcPolicy.dueForReview(stale))
    }

    // ---- 催化三态 ----

    @Test
    fun `prime 与 activate 必须带表现和走向才入库`() {
        val empty = state()
        val noBranches = LongArcPolicy.onReviewed(
            empty, LongArcPolicy.Catalyst.PRIME, "她开始主动问他的周末", emptyList(), now
        )
        val noExpression = LongArcPolicy.onReviewed(
            empty, LongArcPolicy.Catalyst.ACTIVATE, "   ", listOf("一起出门"), now
        )

        assertEquals(LongArcPolicy.Catalyst.DORMANT, noBranches.catalyst)
        assertEquals(LongArcPolicy.Catalyst.DORMANT, noExpression.catalyst)
        assertEquals("空头支票不该覆盖原状态", "", noBranches.firstExpression)
    }

    @Test
    fun `带齐了就能升格并记下表现`() {
        val next = LongArcPolicy.onReviewed(
            state(weight = 25.0),
            LongArcPolicy.Catalyst.PRIME,
            "她开始主动问他周末怎么过",
            listOf("约他出门", "只是多聊两句"),
            now
        )

        assertEquals(LongArcPolicy.Catalyst.PRIME, next.catalyst)
        assertEquals("她开始主动问他周末怎么过", next.firstExpression)
        assertEquals(2, next.responseBranches.size)
        assertEquals(25.0, next.reviewedWeight, 1e-9)
        assertEquals(now, next.lastReviewedAt)
        assertEquals(1, next.generation)
    }

    /** 已经点着的长线不该因为某一次"看起来没事"就熄灭。 */
    @Test
    fun `dormant 不覆盖 activate`() {
        val lit = state(
            weight = 100.0,
            catalyst = LongArcPolicy.Catalyst.ACTIVATE,
            expression = "他们已经约好周末一起去",
            branches = listOf("按约出门")
        )

        val after = LongArcPolicy.onReviewed(lit, LongArcPolicy.Catalyst.DORMANT, "", emptyList(), now)

        assertEquals(LongArcPolicy.Catalyst.ACTIVATE, after.catalyst)
        assertEquals("他们已经约好周末一起去", after.firstExpression)
        assertEquals(listOf("按约出门"), after.responseBranches)
    }

    @Test
    fun `dormant 覆盖 dormant 是允许的，但仍要盖上复审时间`() {
        val after = LongArcPolicy.onReviewed(state(weight = 25.0), LongArcPolicy.Catalyst.DORMANT, "", emptyList(), now)

        assertEquals(LongArcPolicy.Catalyst.DORMANT, after.catalyst)
        assertEquals(now, after.lastReviewedAt)
        assertEquals(25.0, after.reviewedWeight, 1e-9)
    }

    @Test
    fun `被拒绝的升格也要盖上复审时间，免得下一拍又问一次`() {
        val after = LongArcPolicy.onReviewed(state(weight = 25.0), LongArcPolicy.Catalyst.PRIME, "有表现", emptyList(), now)

        assertEquals(LongArcPolicy.Catalyst.DORMANT, after.catalyst)
        assertEquals(now, after.lastReviewedAt)
    }

    @Test
    fun `走向会被清洗、去空、截断并限个数`() {
        val next = LongArcPolicy.onReviewed(
            state(weight = 25.0),
            LongArcPolicy.Catalyst.ACTIVATE,
            "  表现  ",
            listOf("  一  ", "", "   ", "二", "三", "四", "五", "六"),
            now
        )

        assertEquals("表现", next.firstExpression)
        assertEquals(LongArcPolicy.MAX_BRANCHES, next.responseBranches.size)
        assertEquals(listOf("一", "二", "三", "四"), next.responseBranches)
    }

    @Test
    fun `表现与走向都按字符上限截断`() {
        val next = LongArcPolicy.onReviewed(
            state(weight = 25.0),
            LongArcPolicy.Catalyst.PRIME,
            "字".repeat(500),
            listOf("字".repeat(500)),
            now
        )

        assertEquals(LongArcPolicy.MAX_EXPRESSION_CHARS, next.firstExpression.length)
        assertEquals(LongArcPolicy.MAX_BRANCH_CHARS, next.responseBranches.single().length)
    }

    // ---- 注入 ----

    @Test
    fun `没到那条线时不注入任何东西`() {
        assertEquals("", LongArcPolicy.render(state()))
        assertEquals("", LongArcPolicy.render(state(catalyst = LongArcPolicy.Catalyst.PRIME)))
    }

    @Test
    fun `注入是行动许可，并带上可能的走向`() {
        val rendered = LongArcPolicy.render(
            state(
                catalyst = LongArcPolicy.Catalyst.PRIME,
                expression = "她开始主动问他周末怎么过",
                branches = listOf("约他出门", "只是多聊两句")
            )
        )

        assertTrue(rendered.contains("她开始主动问他周末怎么过"))
        assertTrue(rendered.contains("约他出门"))
        assertTrue(rendered.contains("只是多聊两句"))
        assertTrue("prime 要写明不必现在推动", rendered.contains("不必现在推动"))
    }

    @Test
    fun `activate 的措辞与 prime 不同`() {
        val primed = LongArcPolicy.render(
            state(catalyst = LongArcPolicy.Catalyst.PRIME, expression = "表现", branches = listOf("走向"))
        )
        val lit = LongArcPolicy.render(
            state(catalyst = LongArcPolicy.Catalyst.ACTIVATE, expression = "表现", branches = listOf("走向"))
        )

        assertTrue(lit.contains("已经在往前走"))
        assertFalse(primed.contains("已经在往前走"))
    }

    /** 这条禁令是整块的边界：不许她声称觉醒、不许提及指导本身。 */
    @Test
    fun `注入末尾固定带那条禁令`() {
        for (catalyst in listOf(LongArcPolicy.Catalyst.PRIME, LongArcPolicy.Catalyst.ACTIVATE)) {
            val rendered = LongArcPolicy.render(state(catalyst = catalyst, expression = "表现"))
            assertTrue("$catalyst 少了禁令", rendered.trimEnd().endsWith(LongArcPolicy.TAIL_RULE))
        }
    }

    // ---- 序列化与约定 ----

    @Test
    fun `状态可以 json 往返，坏数据退回默认值`() {
        val full = state(
            weight = 42.5,
            catalyst = LongArcPolicy.Catalyst.ACTIVATE,
            expression = "表现",
            branches = listOf("走向"),
            reviewedWeight = 25.0,
            lastReviewedAt = now
        )

        assertEquals(full, LongArcPolicy.State.fromJson(full.toJson()))
        assertEquals(LongArcPolicy.State(), LongArcPolicy.State.fromJson("不是 json"))
        assertEquals(LongArcPolicy.State(), LongArcPolicy.State.fromJson(null))
    }

    @Test
    fun `scope 与主键名是落库约定，必须稳定`() {
        assertEquals("LONG_ARC", LongArcPolicy.SCOPE)
        assertEquals("long-arc:c1", LongArcPolicy.rowId("c1"))
    }

    // ---- 解读复审输出 ----

    @Test
    fun `解读三态词与表现走向`() {
        val review = LongArcPolicy.parseReview(
            "PRIME\nFIRST: 她开始主动问他周末怎么过\nBRANCH: 约他出门\nBRANCH: 只是多聊两句"
        )

        assertEquals(LongArcPolicy.Catalyst.PRIME, review.catalyst)
        assertEquals("她开始主动问他周末怎么过", review.firstExpression)
        assertEquals(listOf("约他出门", "只是多聊两句"), review.branches)
    }

    @Test
    fun `只有三态词时表现与走向为空`() {
        val review = LongArcPolicy.parseReview("DORMANT")

        assertEquals(LongArcPolicy.Catalyst.DORMANT, review.catalyst)
        assertEquals("", review.firstExpression)
        assertTrue(review.branches.isEmpty())
    }

    /** 认不出来**宁可不点燃**：一次误读就会给她塞一份假的行动许可。 */
    @Test
    fun `第一行认不出来时按不点燃处理`() {
        for (raw in listOf("", "   ", "不清楚", "YES\nFIRST: 表现", null)) {
            val review = LongArcPolicy.parseReview(raw)
            assertEquals("「$raw」应当判为 DORMANT", LongArcPolicy.Catalyst.DORMANT, review.catalyst)
            assertEquals("", review.firstExpression)
        }
    }

    @Test
    fun `大小写与全角冒号都认`() {
        val review = LongArcPolicy.parseReview("activate\nfirst：表现\nbranch：走向")

        assertEquals(LongArcPolicy.Catalyst.ACTIVATE, review.catalyst)
        assertEquals("表现", review.firstExpression)
        assertEquals(listOf("走向"), review.branches)
    }

    @Test
    fun `解读时忽略多余说明并截断`() {
        val review = LongArcPolicy.parseReview(
            "PRIME\n这是解释，应该被忽略\nFIRST: " + "字".repeat(500) + "\nBRANCH: 一\nBRANCH: 二"
        )

        assertEquals(LongArcPolicy.Catalyst.PRIME, review.catalyst)
        assertEquals(LongArcPolicy.MAX_EXPRESSION_CHARS, review.firstExpression.length)
        assertEquals(2, review.branches.size)
    }
}
