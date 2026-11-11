package com.aster.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 世界事件播种：切面轮换、节律、上限、去重、注入语义。
 *
 * 最要紧的两条：**同一角色会均匀走过六个切面**；**注入写的是"事实权威 / 反应自由"**
 * ——外面发生的事是事实，但她怎么反应由她决定。
 */
class WorldSeedingPolicyTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 3600_000L

    private fun event(
        title: String,
        detail: String = "",
        at: Long = now,
        injected: Boolean = false,
        facet: WorldSeedingPolicy.Facet = WorldSeedingPolicy.Facet.WEATHER
    ) = WorldSeedingPolicy.Event("e-$title", facet, title, detail, at, injected)

    private fun log(
        events: List<WorldSeedingPolicy.Event> = emptyList(),
        lastSeededAt: Long = 0L,
        todayCount: Int = 0,
        dayKey: String = ""
    ) = WorldSeedingPolicy.Log(events, lastSeededAt, todayCount, dayKey)

    private fun sampler(value: Double) = WorldSeedingPolicy.Sampler { value }

    private val slot = now / WorldSeedingPolicy.SLOT_MS

    // ---- 切面轮换 ----

    @Test
    fun `同一个角色同一个槽总是同一个切面`() {
        assertEquals(
            WorldSeedingPolicy.facetFor("c1", slot),
            WorldSeedingPolicy.facetFor("c1", slot)
        )
    }

    /** 轮换的意义：连续时间槽会依次走过六个切面，而不是随机重复同一个。 */
    @Test
    fun `连续的时间槽会依次走过六个切面`() {
        val order = (0 until 6).map { WorldSeedingPolicy.facetFor("c1", slot + it) }

        assertEquals(6, order.toSet().size)
        order.zipWithNext { a, b ->
            assertEquals((a.ordinal + 1) % 6, b.ordinal)
        }
    }

    @Test
    fun `同一个槽里不同角色落在不同切面上`() {
        val facets = listOf("c1", "c2", "c3", "c4").map { WorldSeedingPolicy.facetFor(it, slot) }

        assertTrue("四个角色不该全挤在同一个切面", facets.toSet().size > 1)
    }

    @Test
    fun `负数哈希也不会越界`() {
        for (id in listOf("", "a", "她", "abcdefghijklmnop", "\uD83D\uDE00")) {
            for (offset in -20L..20L) {
                assertTrue(WorldSeedingPolicy.facetFor(id, slot + offset) in WorldSeedingPolicy.Facet.entries)
            }
        }
    }

    // ---- 节律 ----

    @Test
    fun `没播过时到点就播`() {
        assertTrue(WorldSeedingPolicy.shouldSeed(now, lastSeededAt = 0L, sampler = sampler(0.9)))
    }

    @Test
    fun `同一个时间槽里不重复播`() {
        assertFalse(
            WorldSeedingPolicy.shouldSeed(
                now,
                lastSeededAt = now - WorldSeedingPolicy.SLOT_MS + 1,
                sampler = sampler(0.9)
            )
        )
    }

    @Test
    fun `过了时间槽才可能再播`() {
        assertTrue(
            WorldSeedingPolicy.shouldSeed(
                now,
                lastSeededAt = now - WorldSeedingPolicy.SLOT_MS,
                sampler = sampler(0.9)
            )
        )
    }

    /** 25% 的随机跳过：太准点就像定时任务。 */
    @Test
    fun `抽到跳过就不播`() {
        assertFalse(WorldSeedingPolicy.shouldSeed(now, 0L, sampler(0.1)))
        assertTrue(WorldSeedingPolicy.shouldSeed(now, 0L, sampler(WorldSeedingPolicy.SKIP_CHANCE)))
    }

    // ---- 上限 ----

    @Test
    fun `积压到上限就不再产生`() {
        val full = log(events = List(WorldSeedingPolicy.MAX_PENDING) { event("事$it") })

        assertFalse(WorldSeedingPolicy.withinCaps(full, now, "2026-10-10"))
        assertTrue(WorldSeedingPolicy.withinCaps(log(events = List(2) { event("事$it") }), now, "2026-10-10"))
    }

    @Test
    fun `已注入的不算积压`() {
        val used = log(events = List(WorldSeedingPolicy.MAX_PENDING) { event("事$it", injected = true) })

        assertTrue(WorldSeedingPolicy.withinCaps(used, now, "2026-10-10"))
    }

    @Test
    fun `超过视野的旧事件不算积压`() {
        val stale = log(
            events = List(WorldSeedingPolicy.MAX_PENDING) { event("旧$it", at = now - WorldSeedingPolicy.MAX_HORIZON_MS - 1) }
        )

        assertTrue(WorldSeedingPolicy.withinCaps(stale, now, "2026-10-10"))
    }

    @Test
    fun `当天到上限就不再产生，跨日归零`() {
        val capped = log(todayCount = WorldSeedingPolicy.DAILY_CAP, dayKey = "2026-10-10")

        assertFalse(WorldSeedingPolicy.withinCaps(capped, now, "2026-10-10"))
        assertTrue("跨日应当重新计数", WorldSeedingPolicy.withinCaps(capped, now, "2026-10-11"))
    }

    // ---- 去重（已知很弱）----

    @Test
    fun `完全一样就算重复`() {
        assertTrue(WorldSeedingPolicy.isDuplicate("楼下在修路", listOf(event(title = "楼下在修路"))))
    }

    @Test
    fun `完全无关就不算重复`() {
        assertFalse(WorldSeedingPolicy.isDuplicate("今天降温", listOf(event(title = "楼下在修路"))))
    }

    /**
     * **已知弱点**：只差一个字，相似度就掉到阈值以下。
     *
     * 上游作者实测过"同一件事换四种说法连排四次"，所以真正的防线不是比得更严，
     * 而是 [WorldSeedingPolicy.seedingInput] ——**根本不把自己的输出喂回去**。
     */
    @Test
    fun `词面去重挡不住同义改写`() {
        assertFalse(WorldSeedingPolicy.isDuplicate("楼下修路", listOf(event(title = "楼下在修路"))))
    }

    @Test
    fun `相似度是字符二元组的 Jaccard`() {
        assertEquals(1.0, WorldSeedingPolicy.similarity("今天下雨了", "今天下雨了"), 1e-9)
        assertEquals(0.0, WorldSeedingPolicy.similarity("abc", "xyz"), 1e-9)
        assertEquals(0.0, WorldSeedingPolicy.similarity("", "任何"), 1e-9)
        assertEquals(
            WorldSeedingPolicy.similarity("今天下雨", "下雨了"),
            WorldSeedingPolicy.similarity("下雨了", "今天下雨"),
            1e-9
        )
    }

    // ---- 切断自馈环 ----

    /** 给模型的输入**只有切面与真实上下文**——结构上就拿不到自己上一批产出。 */
    @Test
    fun `播种输入里不含自己上一批产出`() {
        val input = WorldSeedingPolicy.seedingInput(
            WorldSeedingPolicy.Facet.OTHER_PEOPLE,
            "用户说楼下搬来了新邻居"
        )

        assertTrue(input.contains("旁人动向"))
        assertTrue(input.contains("楼下搬来了新邻居"))
        assertFalse("输入里不该出现事件本身", input.contains("e-"))
    }

    @Test
    fun `没有上下文时只给切面`() {
        val input = WorldSeedingPolicy.seedingInput(WorldSeedingPolicy.Facet.WEATHER, "")

        assertTrue(input.contains("天气"))
        assertFalse(input.contains("用户这边"))
    }

    // ---- 注入 ----

    @Test
    fun `没有可用事件时不注入`() {
        assertEquals("", WorldSeedingPolicy.render(log(), now))
        // 注意视野是 72 小时，所以"1 天前"仍然在视野内 —— 这里要用**超出视野**的时刻。
        assertEquals(
            "",
            WorldSeedingPolicy.render(
                log(events = listOf(event("旧的", at = now - WorldSeedingPolicy.MAX_HORIZON_MS - 1))),
                now
            )
        )
        assertEquals("", WorldSeedingPolicy.render(log(events = listOf(event("用过了", injected = true))), now))
    }

    /** 抬头必须写清"事实权威 / 反应自由"，否则世界事件就变成了剧情指令。 */
    @Test
    fun `注入写明这是事实、但反应由她决定`() {
        val rendered = WorldSeedingPolicy.render(log(events = listOf(event("楼下在修路", "早上堵了一阵"))), now)

        assertTrue(rendered.contains("已经发生的事实"))
        assertTrue(rendered.contains("你怎么反应完全由你决定"))
        assertTrue(rendered.contains("楼下在修路"))
        assertTrue(rendered.contains("早上堵了一阵"))
    }

    @Test
    fun `一次注入不超过上限，且按发生时间排`() {
        val events = (0 until 5).map { event("事$it", at = now - it * 60_000L) }

        val rendered = WorldSeedingPolicy.render(log(events = events), now)

        val lines = rendered.lines().filter { it.startsWith("- ") }
        assertEquals(WorldSeedingPolicy.MAX_INJECTED, lines.size)
        assertTrue("最早发生的应当排最前", lines.first().contains("事4"))
    }

    @Test
    fun `标记注入只影响给到的那些`() {
        val a = event("甲")
        val b = event("乙")
        val marked = WorldSeedingPolicy.markInjected(log(events = listOf(a, b)), setOf(a.id), now)

        assertTrue(marked.events.first { it.id == a.id }.injected)
        assertFalse(marked.events.first { it.id == b.id }.injected)
    }

    @Test
    fun `裁剪丢掉过期事件、保留未用的、已用的只留最近若干`() {
        val pending = event("待用", at = now - 1000)
        val stale = event("过期", at = now - WorldSeedingPolicy.MAX_HORIZON_MS - 1)
        val used = (0 until 12).map { event("用$it", at = now - it * 60_000L, injected = true) }

        val pruned = WorldSeedingPolicy.prune(log(events = listOf(pending, stale) + used), now, keepInjected = 3)

        assertTrue(pruned.events.any { it.title == "待用" })
        assertFalse(pruned.events.any { it.title == "过期" })
        assertEquals(3, pruned.events.count { it.injected })
    }

    @Test
    fun `渲染之后事件不会被自动标记，必须显式标记`() {
        val one = event("甲")
        val base = log(events = listOf(one))

        WorldSeedingPolicy.render(base, now)

        assertFalse("render 不该有副作用", base.events.single().injected)
        assertTrue(WorldSeedingPolicy.markInjected(base, setOf(one.id), now).events.single().injected)
    }

    // ---- 序列化与约定 ----

    @Test
    fun `事件与日志可以 json 往返，坏数据退回默认`() {
        val one = event("甲", "细节", facet = WorldSeedingPolicy.Facet.AMBIENCE)
        assertEquals(one, WorldSeedingPolicy.Event.fromJson(one.toJson()))
        assertEquals(null, WorldSeedingPolicy.Event.fromJson("不是 json"))

        val full = log(events = listOf(one), lastSeededAt = now, todayCount = 2, dayKey = "2026-10-10")
        assertEquals(full, WorldSeedingPolicy.Log.fromJson(full.toJson()))
        assertEquals(WorldSeedingPolicy.Log(), WorldSeedingPolicy.Log.fromJson(null))
        assertEquals(WorldSeedingPolicy.Log(), WorldSeedingPolicy.Log.fromJson("不是 json"))
    }

    @Test
    fun `scope 与主键名是落库约定，必须稳定`() {
        assertEquals("WORLD_EVENT", WorldSeedingPolicy.SCOPE)
        assertEquals("world-events:c1", WorldSeedingPolicy.rowId("c1"))
    }

    @Test
    fun `六个切面都在`() {
        assertEquals(6, WorldSeedingPolicy.Facet.entries.size)
    }

    // ---- 解读模型输出 ----

    @Test
    fun `解读出标题与说明`() {
        val parsed = WorldSeedingPolicy.parseEvent(
            "TITLE: 楼下在修路\nDETAIL: 早上出门堵了一阵",
            WorldSeedingPolicy.Facet.PUBLIC_AFFAIR,
            id = "w1",
            now = now
        )

        assertEquals("楼下在修路", parsed?.title)
        assertEquals("早上出门堵了一阵", parsed?.detail)
        assertEquals(WorldSeedingPolicy.Facet.PUBLIC_AFFAIR, parsed?.facet)
        assertEquals("w1", parsed?.id)
        assertEquals(now, parsed?.at)
        assertFalse("刚播出来的不该是已注入", parsed!!.injected)
    }

    /** **宁可不播，也不要播一件没头没脑的事**。 */
    @Test
    fun `没有标题就作废`() {
        for (raw in listOf("", "   ", "DETAIL: 只有说明", "随便写点什么", null)) {
            assertEquals("「$raw」应当作废", null, WorldSeedingPolicy.parseEvent(raw, WorldSeedingPolicy.Facet.WEATHER, "w", now))
        }
    }

    @Test
    fun `没有说明也可以，大小写与全角冒号都认`() {
        val parsed = WorldSeedingPolicy.parseEvent(
            "title：降温了\n这是解释，忽略\n多余的行",
            WorldSeedingPolicy.Facet.WEATHER,
            id = "w2",
            now = now
        )

        assertEquals("降温了", parsed?.title)
        assertEquals("", parsed?.detail)
    }

    @Test
    fun `标题与说明都按上限截断`() {
        val parsed = WorldSeedingPolicy.parseEvent(
            "TITLE: " + "字".repeat(200) + "\nDETAIL: " + "字".repeat(400),
            WorldSeedingPolicy.Facet.AMBIENCE,
            id = "w3",
            now = now
        )

        assertEquals(WorldSeedingPolicy.MAX_TITLE_CHARS, parsed?.title?.length)
        assertEquals(WorldSeedingPolicy.MAX_DETAIL_CHARS, parsed?.detail?.length)
    }
}
