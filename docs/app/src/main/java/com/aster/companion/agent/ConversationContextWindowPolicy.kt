package com.aster.companion.agent

/**
 * 模型上下文预算：**App 必须在拼请求之前**把 system + 历史 + 当前消息压进模型的上下文里。
 *
 * 以前每个环节各自有一个上限（人设 2000 字、最近聊天 12 条×160 字、messages 20 条但单条 2000 字…），
 * 合起来没人管总数：16K 的模型最坏会收到 2 万多字（中文约 1 字≈1 token），直接超上下文——
 * 而服务端的溢出策略是 `truncateMiddle`（从中间砍），砍掉的正好是记忆。
 *
 * 这里只有**算术**，裁剪动作在 [LiteContextBudgetPolicy]，拼装在 ConversationTurnCoordinator。
 *
 * 两个数由用户手调（设置 → 聊天 → 模型上下文）：
 * - **上下文长度**：模型能吃多少 token，决定预算（字符）；
 * - **窗口条数**：每轮最多带几条真实历史。它**不再跟着上下文档位自动放大**——
 *   档位写 128K 时窗口跟着涨到 160 条，整条对话每轮重发，prompt 一直涨到 2.3 万 token，
 *   预填充越来越慢（实测每轮 +450 token、线性上涨）。现在默认 40 条，想更大自己调。
 */
object ConversationContextWindowPolicy {
    const val DEFAULT_CONTEXT_TOKENS = 16_384

    /** 下限 4K（再小连 system 都放不下），上限 1M（比现在任何本地模型都大，纯防手滑）。 */
    const val MIN_CONTEXT_TOKENS = 4_096
    const val MAX_CONTEXT_TOKENS = 1_048_576

    /** 窗口条数上下限：4 条是"还能叫对话"的下限，400 条是防手滑。 */
    const val MIN_WINDOW_MESSAGES = 4
    const val MAX_WINDOW_MESSAGES = 400

    /**
     * 设置页的上下文档位（另有「自定义」入口）。
     *
     * 96K 是这次新加的：本地模型常见 96K 一档，以前只能选 64K 或 128K，会把上限配错。
     */
    val PRESETS: List<Pair<String, Int>> = listOf(
        "16K（本地 8B~9B 小模型）" to 16_384,
        "32K" to 32_768,
        "64K" to 65_536,
        "96K" to 98_304,
        "128K" to 131_072,
    )

    /**
     * 窗口条数默认值。40 条 ≈ 最近 20 轮问答：够接上话题，每轮预填充量又不会失控
     * （更早的事由结构化状态 + 长期记忆承接）。
     */
    const val DEFAULT_WINDOW_MESSAGES = 40

    /**
     * 窗口条数档位：标注里的数字是"这个条数配多大的上下文刚好"（经验值 ≈ 每 819 token 一条历史），
     * **不是限制**——128K 的模型上照样可以只带 20 条。
     */
    val WINDOW_PRESETS: List<Pair<String, Int>> = listOf(
        "20 条（配 16K）" to 20,
        "40 条（配 32K，推荐）" to DEFAULT_WINDOW_MESSAGES,
        "60 条（配 48K）" to 60,
        "80 条（配 64K）" to 80,
        "120 条（配 96K）" to 120,
        "160 条（配 128K）" to 160,
    )

    /** 按上下文档位算的**推荐**条数；设置页会把它显示出来供对照。 */
    fun recommendedWindowMessages(contextTokens: Int): Int =
        (normalizeContext(contextTokens) / 819).coerceIn(MIN_WINDOW_MESSAGES, MAX_WINDOW_MESSAGES)

    /**
     * 收敛到合法区间。**不吸附到档位**——自定义值（96K、30000 token、77 条）必须原样保留，
     * 否则"自定义"就是假的。
     */
    fun normalizeContext(tokens: Int): Int = tokens.coerceIn(MIN_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)

    fun normalizeWindow(messages: Int): Int = messages.coerceIn(MIN_WINDOW_MESSAGES, MAX_WINDOW_MESSAGES)

    fun labelFor(contextTokens: Int): String =
        PRESETS.firstOrNull { it.second == normalizeContext(contextTokens) }?.first
            ?: "${formatTokens(normalizeContext(contextTokens))}（自定义）"

    fun windowLabelFor(messages: Int): String =
        WINDOW_PRESETS.firstOrNull { it.second == normalizeWindow(messages) }?.first
            ?: "${normalizeWindow(messages)} 条（自定义）"

    /** 96K / 32K 这种短写法；不是整 K 就按 token 写。 */
    fun formatTokens(tokens: Int): String {
        val value = normalizeContext(tokens)
        return if (value % 1024 == 0) "${value / 1024}K" else "$value"
    }

    /**
     * 输入预算（字符）。中文按 **1 字 ≈ 1 token** 保守估，并给输出留出 1/4：
     * 16K → 12,288 字 · 32K → 24,576 字 · 64K → 49,152 字。
     * 留 1/4 是因为 App 不发 `max_tokens`（采样参数归模型服务），回复长度不可控。
     */
    fun inputBudgetChars(contextTokens: Int): Int = normalizeContext(contextTokens) * 3 / 4

    /** 一次请求的字符数估算（system + 历史 + 当前消息）。 */
    fun estimateChars(
        system: String,
        history: List<ConversationRequestPolicy.Message>,
        userPrompt: String
    ): Int = system.length + history.sumOf { it.content.length } + userPrompt.length
}
