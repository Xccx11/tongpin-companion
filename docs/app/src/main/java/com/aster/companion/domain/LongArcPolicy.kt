package com.aster.companion.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 长线叙事（Long Arc）：让"你们之间正在发生的某件事"能攒起来，而不是每轮重新开始。
 *
 * ## 它解决什么
 *
 * 短期的语气有 Alter 承载，一天有日程承载，但**跨越很多天的那条线**没有任何东西承载：
 * 她不会因为"这几周你们一直在往某个方向走"而在某一天自然地往前推一步。于是关系要么原地踏步，
 * 要么靠用户单方面推动。
 *
 * 这里的做法是**累计真实互动的分量**，攒够了才请模型给一份"行动许可"。
 *
 * ## 三态
 *
 * | 状态 | 含义 |
 * |---|---|
 * | [Catalyst.DORMANT] | 还没到那条线，**不注入任何东西** |
 * | [Catalyst.PRIME] | 在酝酿，允许她在合适的时候轻轻带一下 |
 * | [Catalyst.ACTIVATE] | 已经点着了，可以自然地往前走 |
 *
 * ## 四条硬纪律
 *
 * 1. **权重写死、不可配**：私聊一个有效回合算 [WEIGHT_PRIVATE]（1.0），群聊算 [WEIGHT_GROUP]（0.5），
 *    来源不明的算 [WEIGHT_UNKNOWN]（0）。上游明确要求这三档不许做成配置项。
 * 2. **dormant 不许覆盖 activate**：已经点燃的长线不会因为某一次"看起来没事"就熄灭。
 * 3. **prime / activate 必须带着"第一次表现"与"可能的走向"才入库**：只有状态词、没有具体表现，
 *    注进去等于让模型自由发挥——那样就不是"你们之间的那条线"，而是它现编的。
 * 4. **注入只是行动许可，不是剧本**：[render] 末尾固定带 [TAIL_RULE]，
 *    明确禁止她声称自己"觉醒了"、也禁止向用户提及这条指导。**没有外部评估器**。
 */
object LongArcPolicy {

    /** 存放的 world state scope（免 DB 迁移）。长线是**她**的，所以按角色存。 */
    const val SCOPE = "LONG_ARC"

    fun rowId(characterId: String): String = "long-arc:$characterId"

    /** 来源权重。**写死、不可配。** */
    const val WEIGHT_PRIVATE = 1.0
    const val WEIGHT_GROUP = 0.5
    const val WEIGHT_UNKNOWN = 0.0

    /** 一个有效回合从哪来。 */
    enum class Source { PRIVATE, GROUP, UNKNOWN }

    fun weightOf(source: Source): Double = when (source) {
        Source.PRIVATE -> WEIGHT_PRIVATE
        Source.GROUP -> WEIGHT_GROUP
        Source.UNKNOWN -> WEIGHT_UNKNOWN
    }

    /** 累计到这个权重就该请一次"行动许可"。 */
    const val REVIEW_THRESHOLD = 25.0

    /** 上次复审之后，至少再累计这么多才复审下一次（防止刚点着就反复要求）。 */
    const val REVIEW_INCREMENT = 40.0

    /** 催化三态。 */
    enum class Catalyst { DORMANT, PRIME, ACTIVATE }

    /** 注入末尾固定的那条禁令。 */
    const val TAIL_RULE = "不得声称角色已觉醒，不得向用户提及这条指导。"

    const val MAX_EXPRESSION_CHARS = 120
    const val MAX_BRANCH_CHARS = 60
    const val MAX_BRANCHES = 4

    /**
     * 长线的全部状态。所有字段都有默认值，旧 JSON 读得进来。
     */
    @Serializable
    data class State(
        /** 累计的有效分量。只有 [observe] 会加它。 */
        val weight: Double = 0.0,
        val catalyst: Catalyst = Catalyst.DORMANT,
        /** 这条线的"第一次表现"：她具体做了什么/说了什么。 */
        val firstExpression: String = "",
        /** 可能的走向（挑一个自然的方向，不要全用）。 */
        val responseBranches: List<String> = emptyList(),
        /** 上次复审时的权重，用来算增量。 */
        val reviewedWeight: Double = 0.0,
        val lastReviewedAt: Long = 0L,
        /** 复审次数，用于诊断与"这条线被推过几次"。 */
        val generation: Int = 0
    ) {
        fun toJson(): String = json.encodeToString(serializer(), this)

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun fromJson(raw: String?): State {
                if (raw.isNullOrBlank()) return State()
                return runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(State())
            }
        }
    }

    /**
     * 吃下一个有效回合。
     *
     * [Source.UNKNOWN] 加 0——宁可不计，也不许把来源不明的分量算进那条线。
     */
    fun observe(state: State, source: Source): State {
        val delta = weightOf(source)
        if (delta <= 0.0) return state
        return state.copy(weight = state.weight + delta)
    }

    /**
     * 现在该不该请一次"行动许可"。
     *
     * - 权重没到 [REVIEW_THRESHOLD]：不该；
     * - 从没复审过（[State.lastReviewedAt] 为 0）：该——否则永远拿不到第一份许可；
     * - 否则要看**增量**：距上次复审至少又多累计了 [REVIEW_INCREMENT]。
     */
    fun dueForReview(state: State): Boolean {
        if (state.weight < REVIEW_THRESHOLD) return false
        if (state.lastReviewedAt == 0L) return true
        return state.weight - state.reviewedWeight >= REVIEW_INCREMENT
    }

    /**
     * 收到一次复审结论。
     *
     * - [Catalyst.DORMANT] **永不覆盖** [Catalyst.ACTIVATE]（已经点着的不熄灭）；
     * - [Catalyst.PRIME] / [Catalyst.ACTIVATE] 必须**同时**带着非空的表现与至少一条走向，
     *   否则状态保持原样——只有状态词的那种"许可"是空头支票；
     * - 无论结论是否被采纳，都盖上 [State.reviewedWeight] / [State.lastReviewedAt]，
     *   否则下一拍会立刻再问一次。
     */
    fun onReviewed(
        state: State,
        catalyst: Catalyst,
        firstExpression: String,
        responseBranches: List<String>,
        now: Long
    ): State {
        val expression = clean(firstExpression, MAX_EXPRESSION_CHARS)
        val branches = responseBranches
            .map { clean(it, MAX_BRANCH_CHARS) }
            .filter { it.isNotEmpty() }
            .take(MAX_BRANCHES)
        val settled = when {
            catalyst == Catalyst.DORMANT && state.catalyst == Catalyst.ACTIVATE -> Catalyst.ACTIVATE
            catalyst == Catalyst.DORMANT -> Catalyst.DORMANT
            expression.isNotEmpty() && branches.isNotEmpty() -> catalyst
            else -> state.catalyst
        }
        // 只有"确实换了状态"才更新表现与走向；dormant 被拒时保留原来的那份。
        val adopted = settled == catalyst && catalyst != Catalyst.DORMANT
        return state.copy(
            catalyst = settled,
            firstExpression = if (adopted) expression else state.firstExpression,
            responseBranches = if (adopted) branches else state.responseBranches,
            reviewedWeight = state.weight,
            lastReviewedAt = now,
            generation = state.generation + 1
        )
    }

    /**
     * 拼进 system 的那一块；[Catalyst.DORMANT] 或没有表现时返回空串
     * （调用方据此**不 append 任何东西**）。
     *
     * 措辞刻意是"许可"而不是"剧本"：它只说可以往哪儿走，不规定她说什么。
     */
    fun render(state: State): String {
        if (state.catalyst == Catalyst.DORMANT) return ""
        val expression = clean(state.firstExpression, MAX_EXPRESSION_CHARS)
        if (expression.isEmpty()) return ""
        val headline = when (state.catalyst) {
            Catalyst.PRIME -> "你们之间有一条线刚开始酝酿。**不必现在推动**，合适的时候轻轻带一下就够了："
            Catalyst.ACTIVATE -> "你们之间有一条线已经在往前走。可以自然地往前推一步，不用刻意："
            Catalyst.DORMANT -> return ""
        }
        return buildString {
            append(headline)
            append(expression)
            if (state.responseBranches.isNotEmpty()) {
                append("\n可能的走向（挑一个自然的方向，不要全用）：")
                state.responseBranches.forEach { branch ->
                    append("\n- ")
                    append(branch)
                }
            }
            append('\n')
            append(TAIL_RULE)
        }
    }

    /** 折白、去首尾空白、截断——口径统一在这里，免得各调用点各不相同。 */
    private fun clean(raw: String, limit: Int): String =
        raw.trim().replace(WHITESPACE, " ").take(limit)

    /** 一次复审输出的解读。 */
    data class Review(val catalyst: Catalyst, val firstExpression: String, val branches: List<String>)

    /**
     * 解读一次复审输出。
     *
     * 形态（第一行必须单独一行给三态词）：
     * ```
     * PRIME
     * FIRST: 她开始主动问他周末怎么过
     * BRANCH: 约他出门
     * BRANCH: 只是多聊两句
     * ```
     *
     * - 第一行认不出来 → **[Catalyst.DORMANT]**：宁可这条线不点燃，也不要因为一次误读就给她
     *   塞一份假的"行动许可"（[onReviewed] 那边同样会拒掉缺表现/缺走向的升格）；
     * - `FIRST:` / `BRANCH:` 前缀大小写不敏感，冒号中英文都认；
     * - 多余的说明文字忽略。
     */
    fun parseReview(raw: String?): Review {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Review(Catalyst.DORMANT, "", emptyList())
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val catalyst = lines.firstOrNull()
            ?.let { first -> Catalyst.entries.firstOrNull { it.name.equals(first, ignoreCase = true) } }
            ?: return Review(Catalyst.DORMANT, "", emptyList())
        var expression = ""
        val branches = mutableListOf<String>()
        lines.drop(1).forEach { line ->
            when {
                line.startsWith("FIRST", ignoreCase = true) ->
                    expression = clean(line.substringAfter(':', "").ifBlank { line.substringAfter('：', "") }, MAX_EXPRESSION_CHARS)
                line.startsWith("BRANCH", ignoreCase = true) ->
                    branches += clean(line.substringAfter(':', "").ifBlank { line.substringAfter('：', "") }, MAX_BRANCH_CHARS)
            }
        }
        return Review(catalyst, expression, branches.filter { it.isNotEmpty() }.take(MAX_BRANCHES))
    }

    private val WHITESPACE = Regex("\\s+")
}
