package com.aster.companion.proactive

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「用户此刻正在某个会话里回复」这件事的**唯一来源**。
 *
 * ## 为什么需要它
 *
 * 主动陪伴的判据 `LiteProactiveThreadSnapshot.active`（"用户当前正在这个线程里活动"）
 * 一直是**写死的 `false`** —— 一道永远为假的闸。它看着像保护，实际什么都没挡。
 *
 * 而它要挡的时序是真实存在的：她的回复要经过一次完整的模型调用（system 可以很长，
 * 实测这类调用能跑到几十秒），这段时间里"距最后一条用户消息"已经可能超过空闲阈值
 * （默认 5 分钟、探活的 idleQuietMs 最低 2 分钟），后台就会在她**正被回复**的会话上插手。
 *
 * 这里只存"哪些会话正在回复"，不做别的判断；清理由调用方在 `finally` 里保证，
 * 所以一次失败/取消也不会把它卡住（否则这个会话会被永远静音）。
 */
@Singleton
class ThreadActivitySignal @Inject constructor() {

    private val replyingThreadIds = ConcurrentHashMap.newKeySet<String>()

    fun markReplying(threadId: String) {
        if (threadId.isNotBlank()) replyingThreadIds.add(threadId)
    }

    fun clearReplying(threadId: String) {
        replyingThreadIds.remove(threadId)
    }

    fun isReplying(threadId: String): Boolean =
        threadId.isNotBlank() && replyingThreadIds.contains(threadId)

    /** 诊断用：排查"她怎么不说话"时，先看是不是被这道闸挡住了。 */
    fun count(): Int = replyingThreadIds.size
}
