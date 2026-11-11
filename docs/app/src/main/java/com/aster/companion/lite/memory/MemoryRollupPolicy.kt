package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind

/** L1 一次压缩的产出：0–3 条事实。 */
data class MemoryDraft(val kind: MemoryKind, val content: String)

/**
 * L1 压缩的纯逻辑（记忆分册 §4.2）：
 * 解析模型的「事实行」，判定该不该写，和已有 L2 去重。
 * 这里不做网络、不碰数据库，方便单测。
 */
object MemoryRollupPolicy {

    const val NONE = "NONE"
    const val MAX_FACTS_PER_ROLLUP = 3
    const val MAX_FACT_CHARS = 40

    /** 触发参数（分册 §10）。 */
    data class Trigger(
        /** 线程自上次压缩后新增多少条才压缩。 */
        val minNewMessages: Int = 10,
        /** 同一线程两次自动压缩的最小间隔。 */
        val minIntervalMs: Long = 10 * 60_000L
    )

    /**
     * 解析模型输出。只认「TYPE: 事实」这种行；没有类型前缀时按 EVENT 处理。
     * 出现 NONE、markdown、超长或空行时按 §4.2 的失败处理返回 null（调用方重试一次后放弃）。
     */
    fun parse(raw: String): List<MemoryDraft>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.equals(NONE, ignoreCase = true)) return emptyList()
        if (text.contains("```")) return null
        val drafts = mutableListOf<MemoryDraft>()
        for (line in text.lines()) {
            val trimmed = line.trim().removePrefix("-").trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.equals(NONE, ignoreCase = true)) continue
            // 提案行（`PATCH…`）是给设定提案系统的，**不是记忆**：跳过它们，
            // 否则 `PATCH: 习惯早起` 会被 splitKind 当成一条 EVENT 事实写进记忆库。
            if (trimmed.startsWith(PATCH_PREFIX, ignoreCase = true)) continue
            if (MARKDOWN_LINE.containsMatchIn(trimmed)) return null
            val (kind, content) = splitKind(trimmed)
            val cleaned = content.trim().replace(WHITESPACE, " ")
            if (cleaned.isEmpty()) continue
            drafts += MemoryDraft(kind = kind, content = cleaned.take(MAX_FACT_CHARS))
            if (drafts.size >= MAX_FACTS_PER_ROLLUP) break
        }
        return drafts
    }

    /** 与既有 L2 重复的事实：返回命中的既有内容，表示只更新旧条而不是新增。 */
    fun duplicateOf(candidate: String, existing: List<String>): String? {
        val normalized = normalize(candidate)
        if (normalized.isEmpty()) return null
        return existing.firstOrNull { fact ->
            val other = normalize(fact)
            other.isNotEmpty() && (other.contains(normalized) || normalized.contains(other) || overlap(normalized, other) >= 0.7)
        }
    }

    /**
     * 这次该不该压缩：新增条数够，且距上次压缩够久。 */
    fun shouldTrigger(
        newMessageCount: Int,
        sinceLastRollupMs: Long,
        trigger: Trigger = Trigger()
    ): Boolean = newMessageCount >= trigger.minNewMessages && sinceLastRollupMs >= trigger.minIntervalMs

    /**
     * 落地性可信度：这条事实能不能在**产生它的那段对话里**找到词面依据。
     *
     * 上游由压缩模型自报 confidence；同频的压缩模型不返回它，所以用**确定性检查**代替：
     * 拿事实的关键词去原始对话里找，命中越多越可信。"模型编出来的事实"通常一个词都对不上。
     *
     * **依据不足不等于要丢掉**——它只是该排得靠后，所以低分也给一个非零值
     * （`0` 会被读侧解释成"未标注"，那是另一回事）。
     */
    fun groundingConfidence(fact: String, source: String): Double {
        val keywords = MemoryRetrievalPolicy.keywordsOf(fact)
        if (keywords.isEmpty() || source.isBlank()) return CONFIDENCE_WEAK
        val hits = keywords.count { source.contains(it, ignoreCase = true) }
        return when {
            hits == 0 -> CONFIDENCE_UNSUPPORTED
            hits * 2 >= keywords.size -> CONFIDENCE_GROUNDED
            else -> CONFIDENCE_WEAK
        }
    }

    /** 每条事实都能在来源对话里对上一半以上关键词。 */
    const val CONFIDENCE_GROUNDED = 0.9

    /** 有一部分能对上，或压根没法比（关键词为空/来源为空）。 */
    const val CONFIDENCE_WEAK = 0.6

    /** 一个关键词都对不上——大概率是模型编的。 */
    const val CONFIDENCE_UNSUPPORTED = 0.3

    private fun splitKind(line: String): Pair<MemoryKind, String> {
        val match = TYPED_LINE.find(line)
        if (match != null) {
            val kind = when (match.groupValues[1].uppercase()) {
                "PREFERENCE" -> MemoryKind.PREFERENCE
                "PLAN" -> MemoryKind.PLAN
                "RELATIONSHIP" -> MemoryKind.RELATIONSHIP
                else -> MemoryKind.EVENT
            }
            return kind to match.groupValues[2]
        }
        return MemoryKind.EVENT to line
    }

    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private fun overlap(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val setA = a.toSet()
        val setB = b.toSet()
        val common = setA.count { it in setB }
        return common.toDouble() / minOf(setA.size, setB.size)
    }

    private val TYPED_LINE = Regex("^(PREFERENCE|PLAN|EVENT|RELATIONSHIP)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val MARKDOWN_LINE = Regex("^#{1,6}\\s|^\\*\\s")
    private val WHITESPACE = Regex("\\s+")

    /** 设定提案行的前缀（见 `StatePatchPolicy.parseCandidates`）。 */
    private const val PATCH_PREFIX = "PATCH"
}
