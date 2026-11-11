package com.aster.companion.agent

import com.aster.companion.domain.EmotionalOffsetPolicy

/**
 * 结构化会话状态：**代替**以前的 300 字散文摘要。
 *
 * 为什么要换掉散文摘要（[ConversationSummarizer] 的旧做法）：
 * - 散文是"摘要的摘要"：每 40 条重压一次、每次又只喂进去 `take(120)` 的残缺消息，压几轮开头信息就被磨掉了；
 * - 散文里混着环境描写和心理独白，模型真正要用的"称谓、地点、待办"反而被稀释；
 * - 没法校验：跑偏了（写成对白、写成小说）也照存，错误逐轮放大。
 *
 * 结构化的好处是**可校验、可限量、密度高**：6 个字段、每条 ≤30 字、总长 ≤600 字，
 * 信息量约等于 300 字散文的 3~5 倍，而且"称谓"这种模型最容易漂的东西有专门一格。
 */
object ConversationStateFormat {
    /** 字段名，顺序即校验顺序（prompt 里也照抄这个顺序）。 */
    val FIELDS: List<String> = listOf("人物", "称谓", "地点时间", "已完成", "未完成", "关键事实")

    /**
     * 记录器**额外**输出的第 7 行：本轮氛围相对上一轮的位移
     * （[EmotionalOffsetPolicy.MIN_CHANGE]..[EmotionalOffsetPolicy.MAX_CHANGE]）。
     *
     * 它**不属于**那 6 个状态字段——[validate] 会把它摘掉、放进 [Validation.shift]，
     * 所以落库的「当前状态」文本与改动前**逐字一致**（旧数据、注入逻辑、备份格式都不受影响）。
     * 模型不吐这一行时按"这一轮没有位移"处理，属于正常降级。
     */
    const val SHIFT_FIELD = "情绪位移"

    /** 总长上限（字符）。 */
    const val MAX_CHARS = 600

    /** 每条上限（字符）。 */
    const val MAX_ITEM_CHARS = 30

    /** 普通字段最多几条，超出删最早的。 */
    const val MAX_ITEMS_PER_FIELD = 5

    /**
     * **不淘汰**的字段：人物（谁是谁、什么身份）与关键事实（长期设定）是"永远要对得上"的东西，
     * 让它们跟"已完成"一起按 5 条滚动淘汰，等于过一阵子就忘了她是谁。
     */
    val PINNED_FIELDS: Set<String> = setOf("人物", "关键事实")

    /** 不淘汰字段的上限（放宽，但仍然有上限，防止无限膨胀）。 */
    const val MAX_PINNED_ITEMS = 8

    const val EMPTY_MARK = "无"

    /** 空状态的规范写法。 */
    val EMPTY: String = FIELDS.joinToString("\n") { "$it：$EMPTY_MARK" }

    data class Validation(
        val ok: Boolean,
        val text: String,
        val reason: String? = null,
        /** 本轮氛围位移（来自可选的第 7 行）；null = 记录器没给或认不出来。 */
        val shift: Int? = null
    )

    /**
     * 状态更新调用的 system：**独立、极简、不含任何角色卡**。
     *
     * 以前这一步用的是对话模型 + （可能）带人设的上下文，一个 RP 微调过的模型会顺势"演"下去，
     * 产出带对白和星号动作的"摘要"。这里把身份说死：你是状态记录器，不是角色。
     */
    val SYSTEM_PROMPT: String = """
        你是状态记录器。根据「新增对话」更新「当前状态」。

        硬性规则（必须全部遵守）：
        1. 只输出状态字段本身。不要解释、不要前言、不要结语、不要代码块标记。
        2. 不要文学描写，不要对白，不要续写剧情，不要扮演任何角色。你是记录器，不是他们中的任何一个人。
        3. 只记录对话中明确出现的信息。不要推测、不要脑补、不要添加没有发生的事。
        4. 每条不超过 $MAX_ITEM_CHARS 字；"人物""关键事实"最多 $MAX_PINNED_ITEMS 条，
           其余字段最多 $MAX_ITEMS_PER_FIELD 条，超出时删掉最早的（"人物""关键事实"优先保留）。
        5. 字段没有内容时写「$EMPTY_MARK」。
        6. 严格保持下面 6 个字段的标题、顺序与标点，每行一个字段，不要增加、不要减少。
        7. 在第 6 个字段之后**再单独输出一行**「$SHIFT_FIELD：<整数>」（见下）。它是第 7 行，
           不属于上面 6 个字段，不要把它混进任何字段里。

        「$SHIFT_FIELD」是**本轮氛围相对上一轮**的位移，取值
        ${EmotionalOffsetPolicy.MIN_CHANGE}..${EmotionalOffsetPolicy.MAX_CHANGE} 的整数：
        - 正数 = 更严肃、更收敛（话变短、变正式、变克制、往正经处走）；
        - 负数 = 更轻松、更活跃（话变多、放得开、开玩笑、往热络处走）；
        - 0 = 这一轮没有明显变化（多数时候应该是 0 或 ±1）。
        只判断"相对上一轮"的变化，不要按整段对话的绝对气氛打分。

        输出格式（原样照抄标题），共 ${FIELDS.size + 1} 行：
        ${FIELDS.joinToString("\n") { "$it：" }}
        $SHIFT_FIELD：0
    """.trimIndent()

    /** 注入对话 system 的状态块（读的人是她，不是记录器）。 */
    fun render(state: String): String =
        "当前状态（你们之间已经发生过的事都在这里；和它矛盾的话就是错的）：\n${state.trim()}"

    /** 旧数据（散文摘要）或空值都算"没有可用状态"。 */
    fun isUsable(state: String?): Boolean = !state.isNullOrBlank() && validate(state).ok

    /** 最后一行是不是那行可选的「情绪位移：…」。 */
    fun isShiftLine(line: String?): Boolean {
        val trimmed = line?.trim().orEmpty()
        return trimmed.startsWith("$SHIFT_FIELD：") || trimmed.startsWith("$SHIFT_FIELD:")
    }

    /**
     * 从「情绪位移：…」里取出整数。
     *
     * 认不出来返回 null —— 按"这一轮没有位移"处理，**不判整次更新失败**：语气偏移不值得
     * 为了一个数把状态更新打回重试。越界值会被夹进合法范围。
     */
    fun shiftOf(line: String?): Int? {
        if (!isShiftLine(line)) return null
        val body = requireNotNull(line).trim().removePrefix(SHIFT_FIELD).trimStart('：', ':', ' ')
        return EmotionalOffsetPolicy.parseChange(body)
    }

    /**
     * 校验并规范化一次状态更新输出。**不通过就不要落库**（保留旧状态、下轮重试）。
     *
     * 规范化做的事：补 `无`、按 `；` 拆条、裁掉超过每条上限的字、按字段上限截断条数。
     * 会让整次更新失败的事：字段缺失/顺序不对/多出字段、总长超标、出现对白或星号动作。
     */
    fun validate(raw: String?): Validation {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Validation(false, EMPTY, "空输出")
        if (text.contains('*')) return Validation(false, EMPTY, "含星号动作")
        if (text.any { it == '"' || it == '“' || it == '”' }) {
            return Validation(false, EMPTY, "含引号对白")
        }
        // 刻意**不**禁括号：「家里（客厅）」这种写法是合法的地点描述，
        // 禁了会让正常输出反复被打回，比漏掉一次旁白更糟。跑偏主要靠"字段结构 + 长度"兜住。

        val rawLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // 可选的第 7 行（情绪位移）：摘掉之后必须正好剩下 6 个状态字段。
        val lastLine = rawLines.lastOrNull()
        val shift = shiftOf(lastLine)
        val lines = if (isShiftLine(lastLine)) rawLines.dropLast(1) else rawLines
        if (lines.size != FIELDS.size) {
            return Validation(false, EMPTY, "字段行数 ${lines.size} ≠ ${FIELDS.size}")
        }
        val values = LinkedHashMap<String, String>()
        lines.forEachIndexed { index, line ->
            val field = FIELDS[index]
            if (!line.startsWith("$field：") && !line.startsWith("$field:")) {
                return Validation(false, EMPTY, "第 ${index + 1} 行不是「$field：」")
            }
            values[field] = line.substringAfter('：', line.substringAfter(':'))
        }

        val normalized = FIELDS.map { field ->
            val rawValue = values[field].orEmpty().trim()
            val items = rawValue
                .split('；', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != EMPTY_MARK }
                .distinct()
            val limit = if (field in PINNED_FIELDS) MAX_PINNED_ITEMS else MAX_ITEMS_PER_FIELD
            val kept = items
                .map { if (it.length > MAX_ITEM_CHARS) it.take(MAX_ITEM_CHARS) else it }
                .take(limit)
            "$field：" + (kept.joinToString("；").ifBlank { EMPTY_MARK })
        }.joinToString("\n")

        if (normalized.length > MAX_CHARS) {
            return Validation(false, EMPTY, "总长 ${normalized.length} > $MAX_CHARS")
        }
        return Validation(true, normalized, null, shift)
    }
}
