package com.aster.companion.ui

/**
 * 多气泡投递的**节奏**：她的话分几条冒出来，中间停一下，像在打字。
 *
 * ## 为什么在展示层做
 *
 * 同频的"拆成几个气泡"本来就是**展示层**的事（[ChatMessagePresentation.displayMessages]
 * 在渲染时把一条消息拆开），数据库里始终是一条消息。所以"投递节奏"也放在这一层：
 *
 * - **记忆 / 状态 / 压缩这些管线看到的仍然是一条消息**——不会因为拆段而多出几轮对话，
 *   也不会把段落数算进"新消息条数"；这是刻意的，改了会牵动一堆阈值；
 * - 用户看到的则是"她分几次说完的"。
 *
 * （上游是把每一段**分别投递**并各自记账的；同频的出站就落在这里，所以等价。）
 *
 * ## 只有"刚到的最后一条"才逐段冒出来
 *
 * 这条规则同时解决三个问题：滚上去再看不会**重播**动画；历史消息一律整条显示；
 * 流式占位（正文还在长）不会被截断——它的 id 以 `stream:` 开头，一律整条显示。
 */
object BubbleRevealPolicy {

    /**
     * 只有**这个时间窗内**到的消息才逐段冒出来。
     *
     * 用"消息有多新"来判定而不是记"播过没有"，是为了免疫重组与滚动：
     * 时间一到它自然就整条显示了，不需要额外的已播集合。
     */
    const val STAGGER_WINDOW_MS = 20_000L

    /** 每个字大约"打"多久。 */
    const val MS_PER_CHAR = 55L

    /** 最短停顿：再短的话几个气泡会一起蹦出来，看不出是分开发的。 */
    const val MIN_PAUSE_MS = 320L

    /** 最长停顿：太长会让人以为她卡住了。 */
    const val MAX_PAUSE_MS = 2_000L

    /**
     * 这条消息要不要逐段冒出来。
     *
     * - [streaming]：这条**本身**是流式占位（正文还在长），一律整条显示；
     * - [progressiveOutput]：**流式输出开着**。这时正文本来就是一整条逐步长出来的，
     *   用户已经看全了；定稿后再拆开逐段冒，等于"把刚看全的话收回去一点点重放"。
     *   所以两者**互斥**：流式开着就不做逐段冒泡。
     * - [createdAt] 在未来（时钟回拨）时**不播**——宁可整条显示，也不要为了一个负的年龄
     *   去算出一个离谱的等待。
     */
    fun shouldStagger(
        createdAt: Long,
        now: Long,
        streaming: Boolean,
        segmentCount: Int,
        progressiveOutput: Boolean = false
    ): Boolean {
        if (progressiveOutput) return false
        if (streaming) return false
        if (segmentCount < 2) return false
        if (createdAt <= 0L) return false
        val age = now - createdAt
        return age in 0..STAGGER_WINDOW_MS
    }

    /** 显示这一段之前要停多久：按这一段自己的字数估（她"打"这段要多久）。 */
    fun pauseBefore(segment: String): Long =
        (segment.trim().length.toLong() * MS_PER_CHAR).coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)

    /**
     * 逐段的时间表：第 `i` 项 = 显示第 `i` 段**之前**要等多久；第 0 项永远是 0（第一段立刻出）。
     *
     * 长度与 [segments] 一致，调用方按下标取用即可。
     */
    fun revealSchedule(segments: List<String>): List<Long> =
        segments.mapIndexed { index, segment -> if (index == 0) 0L else pauseBefore(segment) }
}
