package com.aster.companion.agent

/**
 * 削预算的顺序：**先丢最旧的整条消息 → 再压人设 → 最后才允许硬截**。
 *
 * 为什么不靠"把单条消息截短"来省 token：单条上限（[ConversationRequestPolicy] 里 2000 字）是**安全网**
 * （防一次粘贴/一张图的描述几万字），它一截就会把一句话砍半，正是"称谓漂移""答那半句"的来源。
 * 所以省预算的正道是整条整条地丢最旧的历史，保留最近消息的完整性。
 */
object LiteContextBudgetPolicy {

    /**
     * 人设可以被压到这个下限就不许再压了：再短就不是这个人了。
     * 人设优先级高于"更早的历史"，但低于"最近的消息"。
     */
    const val MIN_PERSONALITY_CHARS = 400

    /**
     * @param personalityLimit 下一次拼 system 时该给人设留多少字（≤ 调用方给的上限）
     * @param historyCount     历史里**保留最近多少条**（按时间顺序的尾部）
     * @param estimatedChars   按这个方案拼出来的估算字符数
     * @param overBudget       削到底还是超（调用方按需记录/兜底）
     */
    data class Plan(
        val personalityLimit: Int,
        val historyCount: Int,
        val estimatedChars: Int,
        val overBudget: Boolean
    )

    /**
     * @param budgetChars      总预算（见 [ConversationContextWindowPolicy.inputBudgetChars]）
     * @param fixedChars       system 里**除人设以外**的全部字符（时间、此刻、照片、状态块、说话方式、记得的事…）
     * @param personalityChars 人设原文长度（未截断前）
     * @param personalityLimit 调用方允许的人设上限
     * @param historyChars     历史每条消息的字符数，**按时间顺序**（最早在前）
     * @param userChars        本轮用户消息字符数
     */
    fun plan(
        budgetChars: Int,
        fixedChars: Int,
        personalityChars: Int,
        personalityLimit: Int,
        historyChars: List<Int>,
        userChars: Int
    ): Plan {
        val budget = budgetChars.coerceAtLeast(1)
        val cap = personalityLimit.coerceAtLeast(MIN_PERSONALITY_CHARS)
        val personality = personalityChars.coerceIn(0, cap)

        // 1) 全都放得下：一条不丢。
        val full = fixedChars + personality + historyChars.sum() + userChars
        if (full <= budget) {
            return Plan(personalityLimit = cap, historyCount = historyChars.size, estimatedChars = full, overBudget = false)
        }

        // 2) 先丢最旧的整条消息，直到放得下为止。
        var kept = historyChars.size
        var historyCharsTotal = historyChars.sum()
        while (kept > 0 && fixedChars + personality + historyCharsTotal + userChars > budget) {
            historyCharsTotal -= historyChars[historyChars.size - kept]
            kept -= 1
        }
        if (fixedChars + personality + historyCharsTotal + userChars <= budget) {
            return Plan(personalityLimit = cap, historyCount = kept, estimatedChars = fixedChars + personality + historyCharsTotal + userChars, overBudget = false)
        }

        // 3) 历史已经清空还超：压人设（留到 MIN_PERSONALITY_CHARS 为止）。
        val overshoot = fixedChars + personality + userChars - budget
        val trimmedPersonality = (personality - overshoot).coerceAtLeast(MIN_PERSONALITY_CHARS)
        val estimated = fixedChars + trimmedPersonality + userChars
        return Plan(
            personalityLimit = trimmedPersonality,
            historyCount = 0,
            estimatedChars = estimated,
            overBudget = estimated > budget
        )
    }
}
