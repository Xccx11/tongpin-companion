package com.aster.companion.lite.memory

/**
 * 记忆的**证据角色**：这条事实，她是怎么知道的。
 *
 * ## 要解决的问题
 *
 * "她亲眼看到的""别人转述给她的""她自己认定的""她只是提过一次的打算"——这四种在模型眼里
 * 以前完全一样，都是一行白底黑字的"记得的事"。于是把转述当亲历就是必然结果：
 * 用户随口说"我同事下个月结婚"，下一轮她就可能说成"我去参加了婚礼"。
 *
 * 这里逐条记下角色（[Mode]）、谁持有这个认知、以及**逐字的原话引用**，
 * 注入时只让 [assertsAsFact] 的模式以事实口气出现，其余带一个限定语（见 [qualifier]）。
 *
 * ## 判定是确定性的
 *
 * 不看模型自报，只看"这条事实的关键词落在谁的话里"——同一条事实在对话里怎么被说出来的，
 * 就决定了它是什么角色。纯逻辑、无 IO，所以能直接单测。
 */
object KnowledgeEvidencePolicy {

    enum class Mode {
        /** 她亲历或亲口说的。 */
        OBSERVED,

        /** 别人（用户）告诉她的。 */
        REPORTED,

        /** 她自己的主观认定（没有外部依据）。 */
        BELIEF,

        /** 一个提议 / 打算，还没发生。 */
        PROPOSAL,

        /** 有条件的，不一定成立。 */
        CONDITIONAL,

        /** 已被后续对话确认。 */
        CONFIRMED,

        /** 判不出来。 */
        UNCLASSIFIED
    }

    /** 一条来源原话。[fromUser] = 这句话是用户说的。 */
    data class Clause(val text: String, val fromUser: Boolean, val messageId: String? = null)

    /** 判定结果：角色 + **逐字引用** + 可回溯的消息 id。 */
    data class Verdict(val mode: Mode, val clauseText: String, val sourceMessageId: String?)

    /** 从库里读出来的字符串 → 枚举；认不出来一律 [Mode.UNCLASSIFIED]。 */
    fun parse(raw: String?): Mode {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Mode.UNCLASSIFIED
        return Mode.entries.firstOrNull { it.name.equals(text, ignoreCase = true) } ?: Mode.UNCLASSIFIED
    }

    /**
     * 能不能以"事实"的口气注入。
     *
     * 只有 [Mode.OBSERVED]（她亲历）与 [Mode.CONFIRMED]（已被后续对话确认）算数——
     * 上游的边界就是这一条：**确认类不成立就降级，绝不"确认"**。
     */
    fun assertsAsFact(mode: Mode): Boolean = mode == Mode.OBSERVED || mode == Mode.CONFIRMED

    /** 注入时的限定语；`""` = 不加（当事实说）。 */
    fun qualifier(mode: Mode): String = when (mode) {
        Mode.OBSERVED, Mode.CONFIRMED -> ""
        Mode.REPORTED -> "（他说的）"
        Mode.BELIEF -> "（她以为的）"
        Mode.PROPOSAL -> "（她说的打算）"
        Mode.CONDITIONAL -> "（不一定）"
        Mode.UNCLASSIFIED -> "（来源不明）"
    }

    /**
     * 只计划、还没发生的事，注入时**必须**带这一笔。
     *
     * 这是上游 Preplan 的边界（计划的条目一律标 `plannedNotObserved`）在同频的落点：
     * **计划永远不许被当成已发生的剧情**。以前 `PLAN` 类记忆是当事实写进 system 的，
     * 于是"她下周三要开会"很容易被演成"她开完会回来了"。
     */
    const val PLANNED_PREFIX = "（计划，还没发生）"

    /**
     * 注入用的正文：不是"事实"就加限定语；只是打算、还没发生的事再额外标一笔。
     *
     * - [rawMode] 为空（**没有证据行**）时**不加**来源限定语——那包括用户手动「让她记住」的条目，
     *   它们本来就是用户亲亲密接触代的，不该被标成"来源不明"；老数据也走这一支；
     * - [futurePlan] = 这条本来就是"打算"（`MemoryKind.PLAN`）。**只有 `CONFIRMED` 才撤掉
     *   [PLANNED_PREFIX]**：对一条计划来说，"她亲口说过"（`OBSERVED`）只说明她提过，
     *   **不等于这件事发生了**；只有后续对话确认过才算数。
     */
    fun decorate(rawMode: String, content: String, futurePlan: Boolean = false): String {
        val text = rawMode.trim()
        val mode = if (text.isEmpty()) null else parse(text)
        val planned = futurePlan && mode != Mode.CONFIRMED
        val provenance = mode?.let { qualifier(it) }.orEmpty()
        return buildString {
            if (planned) append(PLANNED_PREFIX)
            append(provenance)
            append(content)
        }
    }

    /**
     * 判出证据角色：**看这条事实的关键词主要落在谁的话里**。
     *
     * - 主要命中用户的话 → [Mode.REPORTED]（他告诉她的）
     * - 主要命中她自己的话 → [Mode.OBSERVED]（她亲历或亲口说的）
     * - 打平、或者两边都没命中 → [Mode.UNCLASSIFIED]（不猜）
     */
    fun classify(fact: String, clauses: List<Clause>): Verdict {
        val keywords = MemoryRetrievalPolicy.keywordsOf(fact)
        val scored = clauses.map { it to hits(keywords, it.text) }.filter { it.second > 0 }
        if (keywords.isEmpty() || scored.isEmpty()) {
            return Verdict(Mode.UNCLASSIFIED, fact.trim(), null)
        }
        // 引用取命中最多的一条原话。
        val best = scored.maxBy { it.second }
        val userBest = scored.filter { it.first.fromUser }.maxOfOrNull { it.second } ?: 0
        val selfBest = scored.filterNot { it.first.fromUser }.maxOfOrNull { it.second } ?: 0
        val mode = when {
            userBest > selfBest -> Mode.REPORTED
            selfBest > userBest -> Mode.OBSERVED
            else -> Mode.UNCLASSIFIED
        }
        return Verdict(mode, best.first.text.trim(), best.first.messageId)
    }

    /**
     * 已被后续对话确认吗？**上游规则**：同一参与者先提出，之后另一方回应，才算确认
     * （原话是"同一 participant 的 user-message 提议 + 之后的 character-message 回应"）。
     *
     * 这里对着按时间排好的原话做同一件事：用户侧先命中、之后她那一侧也命中 → [Mode.CONFIRMED]；
     * 不成立就**保住原来的模式**——绝不因为"又提到了一次"就升级成确认。
     */
    fun promote(previous: Mode, fact: String, clausesInOrder: List<Clause>): Mode {
        val keywords = MemoryRetrievalPolicy.keywordsOf(fact)
        if (keywords.isEmpty()) return previous
        val firstUser = clausesInOrder.indexOfFirst { it.fromUser && hits(keywords, it.text) > 0 }
        if (firstUser < 0) return previous
        val responded = clausesInOrder.drop(firstUser + 1)
            .any { !it.fromUser && hits(keywords, it.text) > 0 }
        return if (responded) Mode.CONFIRMED else previous
    }

    /** 一条原话里命中了多少个事实关键词（单个字不算，避免噪音）。 */
    private fun hits(factKeywords: Set<String>, text: String): Int =
        factKeywords.count { it.length >= 2 && text.contains(it, ignoreCase = true) }
}
