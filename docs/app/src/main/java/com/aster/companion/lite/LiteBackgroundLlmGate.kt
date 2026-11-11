package com.aster.companion.lite

import kotlinx.coroutines.sync.Semaphore

/**
 * 后台 LLM 并发闸（探活 / 记忆压缩 / 朋友圈文案 / Scene 写 tag）。
 *
 * 用户对话不占此闸，优先吃 LM Studio 的 parallel 槽；
 * 后台最多占 2 路，避免与聊天请求重叠把 4 槽打满。
 */
object LiteBackgroundLlmGate {
    private val semaphore = Semaphore(2)

    suspend fun <T> withPermit(block: suspend () -> T): T {
        semaphore.acquire()
        try {
            return block()
        } finally {
            semaphore.release()
        }
    }
}
