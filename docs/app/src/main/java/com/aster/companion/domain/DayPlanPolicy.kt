package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 日程预排（Preplan）：她**大概**会怎么过这一天。
 *
 * ## 它解决什么
 *
 * 以前她"此刻在做什么"只有一个字段（她自己报的当前活动），没有"接下来大概会怎样"。
 * 于是她的日子没有形状：用户问"你下午有空吗"，她只能现编；她也不会因为"再过一会儿要出门"
 * 而提前收住话头。
 *
 * 这里放一份**软约束**的日程：按周规律排好的块，加上特定日期的例外。
 *
 * ## 三条硬纪律（都是从上游规格抄来的边界）
 *
 * 1. **计划不是事实**。[render] 产出的每一行都带"这是计划，不是已经发生的事"的抬头，
 *    与 `KnowledgeEvidencePolicy.PLANNED_PREFIX` 是同一条纪律：**计划永远不许被演成剧情**。
 *    真实的对话内容、她此刻的状态，**永远优先于**这份计划。
 * 2. **冲突按块的硬度让路**：`FIXED`（上学/考试/明确约定）> `ROUTINE`（通勤/午休/作息）>
 *    `FLEXIBLE`（爱好或可调时段）> `OPEN`（空闲）。同一段时间只留最硬的那一块。
 * 3. **特定日期的例外优先于周规律**：同一天、同一段时间，带 [Block.date] 的那一块赢。
 *
 * ## 每日审查只许这四种结论
 *
 * [Proposal] 就是全部：`UNCHANGED` / `EXTEND` / `PATCH` / `REPLACE`。模型给的块
 * **只要有一行不合法就整体拒绝**（[parseProposal] 返回 null，[apply] 原样返回旧计划）——
 * 宁可这一天没有新计划，也不许半份坏计划生效。同一天只审一次（[Plan.reviewedOn]）。
 */
object DayPlanPolicy {

    /** 存放的 world state scope（免 DB 迁移）。日程是**她**的，所以按角色存、不按会话。 */
    const val SCOPE = "DAY_PLAN"

    fun rowId(characterId: String): String = "day-plan:$characterId"

    /**
     * 块分级。**声明顺序就是冲突优先级**（越靠前越硬）。
     */
    enum class BlockKind {
        /** 上学 / 补课 / 考试 / 明确约定。 */
        FIXED,

        /** 通勤 / 午休 / 常规作息。 */
        ROUTINE,

        /** 爱好或可调时段。 */
        FLEXIBLE,

        /** 空闲。 */
        OPEN
    }

    /** 一个时间块。[startMinute] / [endMinute] 都是从当天 0 点起的分钟数。 */
    @Serializable
    data class Block(
        val kind: BlockKind,
        val startMinute: Int,
        val endMinute: Int,
        val title: String,
        /** 每周哪几天生效（1=周一 … 7=周日）；空集 = 每天。 */
        val daysOfWeek: Set<Int> = emptySet(),
        /** 特定日期（`yyyy-MM-dd`）的例外：设了就**只**在这天生效，并压过同段的周规律。 */
        val date: String? = null
    ) {
        val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)

        fun covers(minuteOfDay: Int): Boolean = minuteOfDay in startMinute until endMinute

        /** 这一天适不适用。 */
        fun appliesOn(dateKey: String, weekday: Int): Boolean =
            if (date != null) date == dateKey else daysOfWeek.isEmpty() || weekday in daysOfWeek
    }

    /** 一整份日程。所有字段都有默认值，所以旧 JSON 读得进来。 */
    @Serializable
    data class Plan(
        val blocks: List<Block> = emptyList(),
        /** 上次审查的本地日期（`yyyy-MM-dd`）。同一天只审一次。 */
        val reviewedOn: String = "",
        val revision: Int = 0,
        val updatedAt: Long = 0L
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): Plan {
                if (raw.isNullOrBlank()) return Plan()
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(Plan())
            }
        }
    }

    /** 每日审查允许的四种结论，**没有第五种**。 */
    enum class Proposal { UNCHANGED, EXTEND, PATCH, REPLACE }

    /** 投影给主叙事的窗口：从此刻起约 12 小时。 */
    const val HORIZON_MINUTES = 12 * 60

    /** 投影给主叙事最多几项。 */
    const val MAX_BLOCKS = 8

    /** 本地时间过了这个点才做当日审查（太早她还没"醒来"）。 */
    const val REVIEW_AFTER_HOUR = 3

    /** 说明文字的长度上限（一行只占一点位置）。 */
    const val MAX_TITLE_CHARS = 24

    /**
     * 今天该不该做一次审查。
     *
     * 同一天只审一次；过了 [REVIEW_AFTER_HOUR] 点才算数。
     */
    fun needsReview(plan: Plan, dateKey: String, localHour: Int): Boolean =
        plan.reviewedOn != dateKey && localHour >= REVIEW_AFTER_HOUR

    /** 日期串 → 星期（1=周一 … 7=周日）；解析不出来返回 null。 */
    fun weekdayOf(dateKey: String): Int? = runCatching {
        java.time.LocalDate.parse(dateKey).dayOfWeek.value
    }.getOrNull()

    /**
     * 解析一行 `KIND HH:MM-HH:MM 说明`。
     *
     * 宽容：冒号中英文都认、`-`/`~`/`～` 都当分隔符、`KIND` 大小写不敏感。
     * 解析不出来返回 null（调用方据此**整体拒绝**）。
     */
    fun parseLine(raw: String): Block? {
        val line = raw.trim().removePrefix("-").trim()
        if (line.isEmpty()) return null
        val match = LINE.find(line) ?: return null
        val kind = BlockKind.entries.firstOrNull { it.name.equals(match.groupValues[1], ignoreCase = true) }
            ?: return null
        val start = minuteOf(match.groupValues[2], match.groupValues[3]) ?: return null
        val end = minuteOf(match.groupValues[4], match.groupValues[5]) ?: return null
        val title = match.groupValues[6].trim().replace(WHITESPACE, " ").take(MAX_TITLE_CHARS)
        if (title.isEmpty() || end <= start) return null
        return Block(kind = kind, startMinute = start, endMinute = end, title = title)
    }

    /**
     * 解析整段模型输出。
     *
     * **任何一行不合法就整体返回 null**——上游的纪律是"非法提案整体拒绝、旧版本继续有效"，
     * 所以这里不做"跳过坏行留下好行"的宽容处理。
     */
    fun parseProposal(raw: String): List<Block>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.equals(NONE, ignoreCase = true)) return emptyList()
        if (text.contains("```")) return null
        val blocks = mutableListOf<Block>()
        for (line in text.lines()) {
            if (line.isBlank()) continue
            blocks += parseLine(line) ?: return null
        }
        return blocks
    }

    /** `UNCHANGED` 之类的结论行 → 枚举；认不出来返回 null。 */
    fun parseProposalKind(raw: String?): Proposal? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return Proposal.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
    }

    /** 一次审查输出的完整解读。 */
    data class Review(val proposal: Proposal, val blocks: List<Block>?)

    /**
     * 解读一次审查输出：**第一行是结论**，后面每行一个块。
     *
     * - 第一行认不出结论时**按 [Proposal.REPLACE] 处理**（整段都当块）——免得模型少写一行结论
     *   就把整份计划作废；
     * - 结论是 [Proposal.UNCHANGED] 时后面不该有块，直接返回空列表；
     * - 块只要有一行不合法，[Review.blocks] 就是 `null`，[apply] 会原样保留旧计划。
     */
    fun parseReview(raw: String?): Review {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Review(Proposal.UNCHANGED, null)
        val lines = text.lines()
        val kind = parseProposalKind(lines.firstOrNull())
        if (kind == Proposal.UNCHANGED) return Review(Proposal.UNCHANGED, emptyList())
        val body = if (kind != null) lines.drop(1) else lines
        return Review(kind ?: Proposal.REPLACE, parseProposal(body.joinToString("\n")))
    }

    /**
     * 取这一天从 [fromMinute] 起、落在 [horizonMinutes] 内的块。
     *
     * 顺序：先按"日期例外优先、块越硬越前"排序，再**贪心丢掉与已保留块时间重叠的**，
     * 最后按开始时间排好、截到 [maxBlocks]。正在进行的块（起点在 [fromMinute] 之前但还没结束）
     * 也算在内——她此刻正在做这件事。
     */
    fun resolve(
        plan: Plan,
        dateKey: String,
        weekday: Int,
        fromMinute: Int,
        horizonMinutes: Int = HORIZON_MINUTES,
        maxBlocks: Int = MAX_BLOCKS
    ): List<Block> {
        val until = fromMinute + horizonMinutes
        val applicable = plan.blocks
            .filter { it.appliesOn(dateKey, weekday) && it.durationMinutes > 0 }
            .filter { it.startMinute < until && it.endMinute > fromMinute }
            .sortedWith(
                compareBy<Block> { it.kind.ordinal }
                    .thenByDescending { it.date != null }
                    .thenBy { it.startMinute }
            )
        val kept = mutableListOf<Block>()
        for (block in applicable) {
            // 时间重叠 = 让位给更硬的那一块（排序已经保证了先来的更硬）。
            if (kept.any { it.startMinute < block.endMinute && block.startMinute < it.endMinute }) continue
            kept += block
        }
        return kept.sortedBy { it.startMinute }.take(maxBlocks.coerceAtLeast(0))
    }

    /**
     * 应用一次每日审查。
     *
     * [blocks] 为 null（模型输出非法）时**原样返回旧计划**——旧版本继续有效。
     * 同一天的审查结论一律盖上 [Plan.reviewedOn]，所以一天只会走到这里一次。
     */
    fun apply(
        plan: Plan,
        proposal: Proposal,
        blocks: List<Block>?,
        dateKey: String,
        now: Long
    ): Plan {
        if (blocks == null || proposal == Proposal.UNCHANGED) {
            return plan.copy(reviewedOn = dateKey, updatedAt = now)
        }
        val merged = when (proposal) {
            // 追加：新的排在旧的后面（旧的先占位，冲突时旧的赢）。
            Proposal.EXTEND -> plan.blocks + blocks
            // 修订：同 kind + 同起点的旧块被替换掉。
            Proposal.PATCH -> plan.blocks.filterNot { old ->
                blocks.any { it.kind == old.kind && it.startMinute == old.startMinute }
            } + blocks
            // 整体重排。
            Proposal.REPLACE -> blocks
            Proposal.UNCHANGED -> plan.blocks
        }
        return plan.copy(
            blocks = merged.sortedWith(compareBy({ it.startMinute }, { it.kind.ordinal })),
            reviewedOn = dateKey,
            revision = plan.revision + 1,
            updatedAt = now
        )
    }

    /** 分钟的 `HH:MM` 形式（跨天夹取到 23:59）。 */
    fun formatMinute(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        return String.format(java.util.Locale.US, "%02d:%02d", clamped / 60, clamped % 60)
    }

    /**
     * 拼进 system 的那一块；没有块时返回空串（调用方据此不 append 任何东西）。
     *
     * 抬头必须写清"这是计划"，否则她真的会当成已经发生过的事去演。
     */
    fun render(blocks: List<Block>): String {
        if (blocks.isEmpty()) return ""
        return buildString {
            append("她今天的安排（**这是计划，不是已经发生的事**；")
            append("真实对话里发生的事和她此刻的状态永远以那些为准）：")
            blocks.forEach { block ->
                append("\n- ")
                append(formatMinute(block.startMinute))
                append('-')
                append(formatMinute(block.endMinute))
                append(' ')
                append(block.title)
            }
        }
    }

    /** `HH:MM` → 从 0 点起的分钟数；越界返回 null。 */
    private fun minuteOf(hour: String, minute: String): Int? {
        val h = hour.toIntOrNull() ?: return null
        val m = minute.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    const val NONE = "NONE"

    private val LINE = Regex(
        "^([A-Za-z]+)\\s+([0-9]{1,2})[:：]([0-9]{2})\\s*[-~～]\\s*([0-9]{1,2})[:：]([0-9]{2})\\s+(.+)$"
    )
    private val WHITESPACE = Regex("\\s+")
}
