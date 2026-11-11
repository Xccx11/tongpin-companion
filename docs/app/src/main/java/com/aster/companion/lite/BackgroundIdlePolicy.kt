package com.aster.companion.lite

/**
 * 后台推演的「空闲让路」判据。
 *
 * 背景：世界推演（时间胶囊的数据源）、朋友圈决策、日记写作这些后台 LLM 调用，
 * 过去在你正聊天时照样发出去，跟你的对话抢同一张卡。这个 policy 决定「现在该不该让路」。
 *
 * 判据只用一样东西：**最近一条"用户"消息的时间**。最近一条用户消息在阈值之内就是
 * "还在聊"，后台先别跑。刻意做得简单、可预测、可单测 —— 不要在这里加别的信号。
 */
object BackgroundIdlePolicy {
    /** 关（0）= 不限制，后台立刻跑，跟以前一样。 */
    const val DISABLED_MS = 0L

    /**
     * 默认 **5 分钟**。
     *
     * 用户定的：时间胶囊（对话后世界推演）和主动陪伴都要更安静，所以默认抬到 5 分钟 ——
     * 你 5 分钟没发消息，后台才开始跑；世界推演、主动陪伴、朋友圈、睡前日记都按这个阈值让路。
     */
    const val DEFAULT_IDLE_MS = 5 * 60 * 1000L

    /** 设置页的下拉档位：label → 空闲阈值。 */
    val OPTIONS: List<Pair<String, Long>> = listOf(
        "关（后台立刻跑）" to DISABLED_MS,
        "30 秒" to 30_000L,
        "1 分钟" to 60_000L,
        "3 分钟" to 180_000L,
        "5 分钟（推荐）" to DEFAULT_IDLE_MS,
    )

    /**
     * 后台该不该让路。true = 用户还在聊，这一拍先别跑。
     *
     * - [idleMs] <= 0：功能关掉，永远不让路（回到旧行为）。
     * - 没有用户消息记录（[lastUserMessageAt] == null）：当成空闲，不让路。
     * - 时钟回拨（now < lastUserMessageAt）也算"还在聊"：宁可少跑一次，也不要抢模型。
     */
    fun shouldDefer(lastUserMessageAt: Long?, now: Long, idleMs: Long): Boolean {
        if (idleMs <= DISABLED_MS) return false
        if (lastUserMessageAt == null) return false
        return now - lastUserMessageAt < idleMs
    }

    fun labelFor(idleMs: Long): String =
        OPTIONS.firstOrNull { it.second == idleMs }?.first ?: "${idleMs / 1000} 秒"
}
