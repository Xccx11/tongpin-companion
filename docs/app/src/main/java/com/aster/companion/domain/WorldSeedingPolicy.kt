package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 世界事件播种：让"外面"偶尔真的发生点什么，而不是她的世界里只有这段对话。
 *
 * ## 它解决什么
 *
 * 她的生活以前只有两类输入：用户说的话，和她自己报的状态。于是"外面"是空的——
 * 她不会提到今天下雨、楼下在修路、隔壁搬来了新邻居。这类东西不需要大，但**有和没有差很多**：
 * 它是"她真的活在一个世界里"的最低成本证据。
 *
 * ## 三条设计纪律
 *
 * 1. **确定性轮换六个切面**（[facetFor]）：按角色与时间槽哈希，所以同一拍不同角色抽到的切面不同，
 *    而同一个角色在一段时间里会**均匀地**覆盖六个方面，不会连着三天都在下雨。
 * 2. **切断自馈环**（[seedingInput]）：生成下一件时**只看真实上下文，不喂它自己上一批产出**。
 *    上游作者实测过一个教训：词面相似度 0.6 挡不住同义改写（同一件事换四种说法连排四次），
 *    所以真正的防线不是"比对得更严"，而是**根本别把自己的输出喂回去**。词面去重（[isDuplicate]）
 *    只是第二道，弱得很，别指望它。
 * 3. **注入是"事实权威 / 反应自由"**（[render]）：外面发生的事是**已经发生的事实**，
 *    她可以知道、可以提起；但**怎么反应完全由她决定**，不受这些事限制。
 *    这既避免了"世界事件变成剧情指令"，也避免了"她假装不知道外面在下雨"。
 *
 * ## 默认关闭
 *
 * 它要定期跑模型。上游默认关（`enabled`），同频同样：不开就完全不产生任何事件。
 */
object WorldSeedingPolicy {

    /** 存放的 world state scope（免 DB 迁移）。事件按**角色**存：每个角色有自己的"外面"。 */
    const val SCOPE = "WORLD_EVENT"

    fun rowId(characterId: String): String = "world-events:$characterId"

    /**
     * 六个切面。
     *
     * **这六个名字是同频自己定的**：上游文档只写了"六切面轮换"，没给出清单，
     * 所以这里取的是"一个普通人身边最常变、又最不需要编造"的六类。
     */
    enum class Facet {
        /** 天气与季节。 */
        WEATHER,

        /** 公共事务：修路、停电、社区通知、商场活动。 */
        PUBLIC_AFFAIR,

        /** 旁人动向：邻居、店员、同事、路人。 */
        OTHER_PEOPLE,

        /** 物件与环境变化：坏了、换了、多了什么。 */
        OBJECTS,

        /** 时间节律：周末、节前、月底、换季。 */
        RHYTHM,

        /** 城市氛围：热闹、冷清、气味、声音。 */
        AMBIENCE
    }

    /** 时间槽：45 分钟一格（对齐上游 cadence），同一格里只播一次。 */
    const val SLOT_MS = 45 * 60_000L

    /** 每次到点有 25% 概率直接跳过（节律抖动，免得像定时任务）。 */
    const val SKIP_CHANCE = 0.25

    /** 同时最多积压几件没被用掉的。 */
    const val MAX_PENDING = 4

    /** 每天最多产生几件。 */
    const val DAILY_CAP = 4

    /** 超过这个年龄的事件不再注入（上游 maxHorizonHours=72）。 */
    const val MAX_HORIZON_MS = 72 * 3600_000L

    /** 一次注入最多几件（多了会淹掉对话本身）。 */
    const val MAX_INJECTED = 2

    const val MAX_TITLE_CHARS = 24
    const val MAX_DETAIL_CHARS = 80

    /**
     * 词面近重复的判定阈值。
     *
     * **比上游的 0.6 更严**：上游作者实测 0.6 挡不住同义改写。但别把希望放在这一条上——
     * 见 [seedingInput]。低于这个相似度就算"是另一件事"。
     */
    const val DUPLICATE_RATIO = 0.5

    /** 随机源（可注入，便于单测）。 */
    fun interface Sampler {
        fun next(): Double

        companion object {
            val random = Sampler { Math.random() }
        }
    }

    /** 一件世界事件。 */
    @Serializable
    data class Event(
        val id: String,
        val facet: Facet,
        val title: String,
        val detail: String,
        /** 发生时刻。 */
        val at: Long,
        /** 已经注入过就不再重复注入。 */
        val injected: Boolean = false
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): Event? {
                if (raw.isNullOrBlank()) return null
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
            }
        }
    }

    /** 一个角色的全部事件（按时间倒序无所谓，调用方自己排）。 */
    @Serializable
    data class Log(
        val events: List<Event> = emptyList(),
        /** 上次播种时刻。 */
        val lastSeededAt: Long = 0L,
        /** 当日已产生几件（配合 [State.dayKey] 跨日归零）。 */
        val todayCount: Int = 0,
        val dayKey: String = ""
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): Log {
                if (raw.isNullOrBlank()) return Log()
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(Log())
            }
        }
    }

    /**
     * 这一拍该用哪个切面。
     *
     * 按角色与时间槽一起哈希：同一个角色在连续时间槽里会**依次走过六个切面**（因为槽号每次 +1），
     * 不同角色在同一槽里又会落在不同面上。
     */
    fun facetFor(characterId: String, slot: Long): Facet {
        val facets = Facet.entries
        val mixed = characterId.hashCode().toLong() * 31L + slot
        val index = ((mixed % facets.size) + facets.size) % facets.size
        return facets[index.toInt()]
    }

    /** 现在是不是该播一件了。 */
    fun shouldSeed(now: Long, lastSeededAt: Long, sampler: Sampler = Sampler.random): Boolean {
        if (lastSeededAt > 0L && now - lastSeededAt < SLOT_MS) return false
        return sampler.next() >= SKIP_CHANCE
    }

    /** 上限：积压太多、或今天已经够了，就都别再产生。 */
    fun withinCaps(log: Log, now: Long, dayKey: String): Boolean {
        val pending = log.events.count { !it.injected && now - it.at <= MAX_HORIZON_MS }
        val today = if (log.dayKey == dayKey) log.todayCount else 0
        return pending < MAX_PENDING && today < DAILY_CAP
    }

    /**
     * 词面近重复。
     *
     * 拿候选与最近若干件的标题 + 说明比相似度，任一超过 [DUPLICATE_RATIO] 就算重复。
     * **这是弱防线**，别指望它挡住同义改写。
     */
    fun isDuplicate(candidate: String, recent: List<Event>): Boolean =
        recent.any { similarity(candidate, it.title) >= DUPLICATE_RATIO || similarity(candidate, it.detail) >= DUPLICATE_RATIO }

    /** 字符 2-gram 的 Jaccard 相似度。 */
    fun similarity(a: String, b: String): Double {
        val setA = bigrams(a)
        val setB = bigrams(b)
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val common = setA.count { it in setB }
        return common.toDouble() / (setA.size + setB.size - common).toDouble()
    }

    /**
     * 交给模型的输入：**只有真实上下文，没有它自己上一批产出**（切断自馈环）。
     *
     * 见过实拍素材的人都知道，把模型上一轮的输出喂回去，它下一轮只会换个说法重写同一件事。
     * 所以这里刻意**不给** [Log.events]，只给切面与真实对话里提到过的环境。
     */
    fun seedingInput(facet: Facet, recentUserContext: String): String = buildString {
        appendLine("这次要播的方面：${facetTitle(facet)}")
        if (recentUserContext.isNotBlank()) {
            appendLine("用户这边最近的真实情况（可以参考，不要编造细节）：")
            append(recentUserContext.trim())
        }
    }.trim()

    /**
     * 这次该注入哪几件。
     *
     * 抽出来是因为 [render] 与"标记为已注入"必须**看到同一份名单**，
     * 各写一份过滤条件迟早会漂移（那样就会出现"注入了但没标记，于是重复注入"）。
     */
    fun visibleForInjection(log: Log, now: Long, maxInjected: Int = MAX_INJECTED): List<Event> =
        log.events
            .filter { !it.injected && now - it.at in 0..MAX_HORIZON_MS }
            .sortedBy { it.at }
            .take(maxInjected.coerceAtLeast(0))

    /**
     * 注入 system 的那一块；没有可用事件时返回空串。
     *
     * 抬头必须把"事实权威 / 反应自由"写清楚：这些是**已经发生的事**，她可以知道、可以提；
     * 但**怎么反应由她决定**，不必受它们限制——否则世界事件就变成了剧情指令。
     */
    fun render(log: Log, now: Long, maxInjected: Int = MAX_INJECTED): String {
        val visible = visibleForInjection(log, now, maxInjected)
        if (visible.isEmpty()) return ""
        return buildString {
            append("外面最近发生的事（**这些是已经发生的事实**，你可以知道、也可以自然地提起；")
            append("但**你怎么反应完全由你决定**，不必受它们限制，也不必每件都提）：")
            visible.forEach { event ->
                append("\n- ")
                append(event.title)
                if (event.detail.isNotBlank()) {
                    append('：')
                    append(event.detail)
                }
            }
        }
    }

    /** 注入之后把这些事件标记为已用。 */
    fun markInjected(log: Log, injectedIds: Set<String>, now: Long): Log = log.copy(
        events = log.events.map { event ->
            if (event.id in injectedIds) event.copy(injected = true) else event
        },
        lastSeededAt = log.lastSeededAt
    )

    /** 裁掉过期与已注入的旧事件，避免无限增长。 */
    fun prune(log: Log, now: Long, keepInjected: Int = 8): Log {
        val fresh = log.events.filter { now - it.at <= MAX_HORIZON_MS }
        val used = fresh.filter { it.injected }.sortedByDescending { it.at }.take(keepInjected)
        val pending = fresh.filterNot { it.injected }
        return log.copy(events = (pending + used).sortedBy { it.at })
    }

    /**
     * 解读模型输出的一件事。
     *
     * 形态：
     * ```
     * TITLE: 楼下在修路
     * DETAIL: 早上出门堵了一阵
     * ```
     *
     * - `TITLE:` 是必需的，缺了或为空就返回 null（**宁可不播，也不要播一件没头没脑的事**）；
     * - `DETAIL:` 可以没有；
     * - 前缀大小写不敏感、冒号中英文都认；多余的解释行忽略。
     */
    fun parseEvent(raw: String?, facet: Facet, id: String, now: Long): Event? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        var title = ""
        var detail = ""
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("TITLE", ignoreCase = true) ->
                    title = clean(line.substringAfter(':', "").ifBlank { line.substringAfter('：', "") }, MAX_TITLE_CHARS)
                line.startsWith("DETAIL", ignoreCase = true) ->
                    detail = clean(line.substringAfter(':', "").ifBlank { line.substringAfter('：', "") }, MAX_DETAIL_CHARS)
            }
        }
        if (title.isEmpty()) return null
        return Event(id = id, facet = facet, title = title, detail = detail, at = now)
    }

    private fun clean(raw: String, limit: Int): String =
        raw.trim().replace(WHITESPACE, " ").take(limit)

    private fun facetTitle(facet: Facet): String = when (facet) {
        Facet.WEATHER -> "天气与季节"
        Facet.PUBLIC_AFFAIR -> "公共事务（修路、停电、社区通知、商场活动）"
        Facet.OTHER_PEOPLE -> "旁人动向（邻居、店员、同事、路人）"
        Facet.OBJECTS -> "物件与环境变化（坏了、换了、多了什么）"
        Facet.RHYTHM -> "时间节律（周末、节前、月底、换季）"
        Facet.AMBIENCE -> "城市氛围（热闹、冷清、气味、声音）"
    }

    private fun bigrams(text: String): Set<String> {
        val normalized = text.lowercase().filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) return emptySet()
        if (normalized.length == 1) return setOf(normalized)
        return (0..normalized.length - 2).map { normalized.substring(it, it + 2) }.toSet()
    }

    private val WHITESPACE = Regex("\\s+")
}
