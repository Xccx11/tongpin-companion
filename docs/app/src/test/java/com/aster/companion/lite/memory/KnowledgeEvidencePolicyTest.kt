package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 证据角色：这条事实她是怎么知道的。
 *
 * 要治的是"把转述当亲历"——用户随口说一句，下一轮她就说成自己经历过的。
 * 判定必须**确定性**（只看关键词落在谁的话里），所以这里能逐条钉死。
 */
class KnowledgeEvidencePolicyTest {

    private fun user(text: String, id: String? = "u") = KnowledgeEvidencePolicy.Clause(text, fromUser = true, messageId = id)
    private fun hers(text: String, id: String? = "c") = KnowledgeEvidencePolicy.Clause(text, fromUser = false, messageId = id)

    // ---- 分类 ----

    @Test
    fun `关键词落在用户的话里就是转述`() {
        val verdict = KnowledgeEvidencePolicy.classify("喜欢美式咖啡", listOf(user("我特别喜欢美式咖啡", "u1")))

        assertEquals(KnowledgeEvidencePolicy.Mode.REPORTED, verdict.mode)
        assertEquals("我特别喜欢美式咖啡", verdict.clauseText)
        assertEquals("u1", verdict.sourceMessageId)
    }

    @Test
    fun `关键词落在她自己的话里就是亲历`() {
        val verdict = KnowledgeEvidencePolicy.classify("喜欢美式咖啡", listOf(hers("我也喜欢美式咖啡", "c1")))

        assertEquals(KnowledgeEvidencePolicy.Mode.OBSERVED, verdict.mode)
        assertEquals("c1", verdict.sourceMessageId)
    }

    @Test
    fun `两边都命中一样多时不猜，判为来源不明`() {
        val verdict = KnowledgeEvidencePolicy.classify(
            "喜欢美式咖啡",
            listOf(user("喜欢美式咖啡", "u1"), hers("喜欢美式咖啡", "c1"))
        )

        assertEquals(KnowledgeEvidencePolicy.Mode.UNCLASSIFIED, verdict.mode)
    }

    @Test
    fun `一个关键词都对不上也判为来源不明，并把事实本身当引用`() {
        val verdict = KnowledgeEvidencePolicy.classify("喜欢美式咖啡", listOf(user("今天天气不错")))

        assertEquals(KnowledgeEvidencePolicy.Mode.UNCLASSIFIED, verdict.mode)
        assertEquals("喜欢美式咖啡", verdict.clauseText)
        assertEquals(null, verdict.sourceMessageId)
    }

    @Test
    fun `引用取命中最多的一条原话`() {
        val verdict = KnowledgeEvidencePolicy.classify(
            "喜欢美式咖啡",
            listOf(user("美式", "u1"), hers("喜欢美式咖啡", "c9"))
        )

        assertEquals(KnowledgeEvidencePolicy.Mode.OBSERVED, verdict.mode)
        assertEquals("喜欢美式咖啡", verdict.clauseText)
        assertEquals("c9", verdict.sourceMessageId)
    }

    @Test
    fun `没有原话时判为来源不明`() {
        assertEquals(
            KnowledgeEvidencePolicy.Mode.UNCLASSIFIED,
            KnowledgeEvidencePolicy.classify("喜欢美式咖啡", emptyList()).mode
        )
    }

    // ---- 确认（升格） ----

    @Test
    fun `用户先提出、之后她回应，才算已确认`() {
        val mode = KnowledgeEvidencePolicy.promote(
            previous = KnowledgeEvidencePolicy.Mode.REPORTED,
            fact = "下周三出差",
            clausesInOrder = listOf(
                user("我下周三要出差", "u1"),
                hers("下周三出差的事我记下了", "c1")
            )
        )

        assertEquals(KnowledgeEvidencePolicy.Mode.CONFIRMED, mode)
    }

    @Test
    fun `只有用户的提议、她还没回应时不算确认`() {
        val mode = KnowledgeEvidencePolicy.promote(
            previous = KnowledgeEvidencePolicy.Mode.REPORTED,
            fact = "下周三出差",
            clausesInOrder = listOf(user("我下周三要出差", "u1"))
        )

        assertEquals(KnowledgeEvidencePolicy.Mode.REPORTED, mode)
    }

    /** 顺序反了（她先提、用户后说）不构成"先提出后回应"，绝不硬判确认。 */
    @Test
    fun `顺序不对就不升格`() {
        val mode = KnowledgeEvidencePolicy.promote(
            previous = KnowledgeEvidencePolicy.Mode.REPORTED,
            fact = "下周三出差",
            clausesInOrder = listOf(
                hers("下周三出差？", "c1"),
                user("我下周三要出差", "u1")
            )
        )

        assertEquals(KnowledgeEvidencePolicy.Mode.REPORTED, mode)
    }

    @Test
    fun `判不出关键词时保持原模式`() {
        assertEquals(
            KnowledgeEvidencePolicy.Mode.BELIEF,
            KnowledgeEvidencePolicy.promote(KnowledgeEvidencePolicy.Mode.BELIEF, "好", listOf(user("好")))
        )
    }

    // ---- 能不能当事实说 ----

    @Test
    fun `只有亲历与已确认能以事实口气出现`() {
        assertTrue(KnowledgeEvidencePolicy.assertsAsFact(KnowledgeEvidencePolicy.Mode.OBSERVED))
        assertTrue(KnowledgeEvidencePolicy.assertsAsFact(KnowledgeEvidencePolicy.Mode.CONFIRMED))
        assertFalse(KnowledgeEvidencePolicy.assertsAsFact(KnowledgeEvidencePolicy.Mode.REPORTED))
        assertFalse(KnowledgeEvidencePolicy.assertsAsFact(KnowledgeEvidencePolicy.Mode.BELIEF))
        assertFalse(KnowledgeEvidencePolicy.assertsAsFact(KnowledgeEvidencePolicy.Mode.PROPOSAL))
        assertFalse(KnowledgeEvidencePolicy.assertsAsFact(KnowledgeEvidencePolicy.Mode.CONDITIONAL))
        assertFalse(KnowledgeEvidencePolicy.assertsAsFact(KnowledgeEvidencePolicy.Mode.UNCLASSIFIED))
    }

    @Test
    fun `非事实模式都有限定语，事实模式没有`() {
        assertEquals("", KnowledgeEvidencePolicy.qualifier(KnowledgeEvidencePolicy.Mode.OBSERVED))
        assertEquals("", KnowledgeEvidencePolicy.qualifier(KnowledgeEvidencePolicy.Mode.CONFIRMED))
        for (mode in KnowledgeEvidencePolicy.Mode.entries) {
            if (KnowledgeEvidencePolicy.assertsAsFact(mode)) continue
            assertTrue("$mode 必须有提示语气的限定语", KnowledgeEvidencePolicy.qualifier(mode).isNotEmpty())
        }
    }

    // ---- 注入正文 ----

    @Test
    fun `转述的事实带上限定语`() {
        assertEquals(
            "（他说的）他同事下个月结婚",
            KnowledgeEvidencePolicy.decorate("REPORTED", "他同事下个月结婚")
        )
    }

    @Test
    fun `亲历的事实不加任何前缀`() {
        assertEquals("她昨天去了医院", KnowledgeEvidencePolicy.decorate("OBSERVED", "她昨天去了医院"))
    }

    /** 没有证据行 = 老数据 / 用户手动「让她记住」：它本来就是用户亲亲密接触代的，不该被标成来源不明。 */
    @Test
    fun `没有证据行时不加限定语`() {
        assertEquals("她怕黑", KnowledgeEvidencePolicy.decorate("", "她怕黑"))
        assertEquals("她怕黑", KnowledgeEvidencePolicy.decorate("   ", "她怕黑"))
    }

    @Test
    fun `认不出来的角色名按来源不明处理`() {
        assertEquals("（来源不明）她怕黑", KnowledgeEvidencePolicy.decorate("WHATEVER", "她怕黑"))
    }

    // ---- 计划不是事实（上游 Preplan 的 plannedNotObserved 边界）----

    @Test
    fun `计划还没发生时必须标出来`() {
        assertEquals(
            "（计划，还没发生）下周三开会",
            KnowledgeEvidencePolicy.decorate("", "下周三开会", futurePlan = true)
        )
    }

    @Test
    fun `不是计划时不加计划标记`() {
        assertEquals("她怕黑", KnowledgeEvidencePolicy.decorate("", "她怕黑", futurePlan = false))
    }

    /** 对一条计划来说，"她亲口说过"只说明她提过，不等于这件事发生了。 */
    @Test
    fun `亲历的计划仍然标明还没发生`() {
        assertEquals(
            "（计划，还没发生）下周三开会",
            KnowledgeEvidencePolicy.decorate("OBSERVED", "下周三开会", futurePlan = true)
        )
    }

    /** 只有被后续对话确认过，计划才真正变成"发生过的事"。 */
    @Test
    fun `计划被确认之后撤掉计划标记`() {
        assertEquals(
            "下周三开会",
            KnowledgeEvidencePolicy.decorate("CONFIRMED", "下周三开会", futurePlan = true)
        )
    }

    @Test
    fun `计划与来源两个标记可以同时出现`() {
        assertEquals(
            "（计划，还没发生）（他说的）下周三开会",
            KnowledgeEvidencePolicy.decorate("REPORTED", "下周三开会", futurePlan = true)
        )
    }

    @Test
    fun `没有证据行的计划只带计划标记`() {
        val decorated = KnowledgeEvidencePolicy.decorate("", "下周三开会", futurePlan = true)

        assertTrue(decorated.startsWith(KnowledgeEvidencePolicy.PLANNED_PREFIX))
        assertFalse(decorated.contains("来源不明"))
    }

    @Test
    fun `角色名解析忽略大小写与空白`() {
        assertEquals(KnowledgeEvidencePolicy.Mode.CONFIRMED, KnowledgeEvidencePolicy.parse("confirmed"))
        assertEquals(KnowledgeEvidencePolicy.Mode.BELIEF, KnowledgeEvidencePolicy.parse("  BELIEF "))
        assertEquals(KnowledgeEvidencePolicy.Mode.UNCLASSIFIED, KnowledgeEvidencePolicy.parse(null))
        assertEquals(KnowledgeEvidencePolicy.Mode.UNCLASSIFIED, KnowledgeEvidencePolicy.parse(""))
        assertEquals(KnowledgeEvidencePolicy.Mode.UNCLASSIFIED, KnowledgeEvidencePolicy.parse("WHAT"))
    }
}

/**
 * 日记**不能**被注入 system。
 *
 * 它的重要度是 0，但"排得靠后"不等于"不会进去"——`isInjectable` 以前只挡了 CONVERSATION，
 * 于是日记会以「记得的事」的身份混进 system，和 `importanceOf` 上的注释直接矛盾。
 */
class MemoryBudgetInjectableTest {

    @Test
    fun `日记与对话摘录都不进 system`() {
        assertFalse(MemoryBudgetPolicy.isInjectable(MemoryKind.DIARY))
        assertFalse(MemoryBudgetPolicy.isInjectable(MemoryKind.CONVERSATION))
    }

    @Test
    fun `事实类记忆仍然可以进 system`() {
        assertTrue(MemoryBudgetPolicy.isInjectable(MemoryKind.PREFERENCE))
        assertTrue(MemoryBudgetPolicy.isInjectable(MemoryKind.PLAN))
        assertTrue(MemoryBudgetPolicy.isInjectable(MemoryKind.RELATIONSHIP))
        assertTrue(MemoryBudgetPolicy.isInjectable(MemoryKind.EVENT))
    }

    @Test
    fun `不可注入的种类重要度也是零`() {
        assertEquals(0, MemoryBudgetPolicy.importanceOf(MemoryKind.DIARY))
        assertEquals(0, MemoryBudgetPolicy.importanceOf(MemoryKind.CONVERSATION))
    }
}
