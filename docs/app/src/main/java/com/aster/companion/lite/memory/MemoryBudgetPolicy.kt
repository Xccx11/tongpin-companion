package com.aster.companion.lite.memory

import com.aster.companion.domain.MemoryKind

/**
 * L2 事实库进 system 的预算（记忆分册 §4.3、§10）。
 *
 * 注：v32 起 `companion_memories` **已有** importance / confidence 列，写入方会显式落值；
 * 这里的 [importanceOf] 仍然保留，用于"库里是 0 = 未标注"时按 kind 退回推断（老数据语义不变）。
 */
data class MemoryBudget(
    /** system 里最多出现几条事实。 */
    val topK: Int = 5,
    /** 「记得的事」整段字符预算。 */
    val totalCharBudget: Int = 400,
    /** 单条事实截断长度。 */
    val perFactChars: Int = 40,
    /** 每个角色 L2 的软上限，超出丢弃低重要度且最旧的。 */
    val perCharacterLimit: Int = 80
)

object MemoryBudgetPolicy {

    val default = MemoryBudget()

    /** kind → 重要度；不可注入的 kind 一律 0。 */
    fun importanceOf(kind: MemoryKind): Int = when (kind) {
        MemoryKind.PREFERENCE, MemoryKind.PLAN -> 3
        MemoryKind.RELATIONSHIP, MemoryKind.EVENT -> 2
        // 日记和 CONVERSATION 一样**不进 system**：它是写给用户看的，不是给模型看的上下文。
        MemoryKind.CONVERSATION, MemoryKind.DIARY -> 0
    }

    /**
     * 是否允许进 system。
     *
     * **日记必须一起排除**：它的重要度是 0（见 [importanceOf]），但"重要度为 0"只是排得靠后，
     * 并不能阻止它被注入——以前这里只挡了 CONVERSATION，于是日记会以"记得的事"的身份
     * 混进 system，和上面那条注释直接矛盾。
     */
    fun isInjectable(kind: MemoryKind): Boolean =
        kind != MemoryKind.CONVERSATION && kind != MemoryKind.DIARY

    /**
     * 按软上限裁剪一个角色的 L2：保留高重要度、较新的，丢掉多余的。
     */
    fun trimToLimit(facts: List<LiteMemoryFact>, budget: MemoryBudget = default): List<LiteMemoryFact> =
        facts.sortedWith(
            compareByDescending<LiteMemoryFact> { it.importance }
                .thenByDescending { it.updatedAt }
        ).take(budget.perCharacterLimit)
}
