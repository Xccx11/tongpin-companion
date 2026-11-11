package com.aster.companion.agent

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * 出图泳道：所有生图操作串行执行，保证同一时刻只有一次出图在跑。
 *
 * **为什么不再用裸 Mutex**：原来 `withLane` 就是 `mutex.withLock { block() }`。
 * 只要有一个协程卡住不返回（网络请求挂死、ngrok 隧道半死、阻塞 IO 不响应取消），
 * 锁就永远不释放，后来的出图全部无限排队——表现就是「App 说提交了但网关收不到请求」，
 * 界面永远显示「出图进行中」，必须杀进程才能恢复。
 *
 * **现在改成带超时的抢占式**：记录持锁者与持锁起始时间。后来的调用会等，
 * 但一旦发现**持锁时间超过 [LANE_MAX_HOLD_MS]**，就认定那次已经废了、**直接接管**。
 * 旧的即使后来真的返回，也不会再影响任何人。
 *
 * 效果：某次出图彻底卡死时，最多 5 分钟后下一位就能正常出图，不需要杀 App。
 */
internal class ImageGenerationLaneRegistry {

    private val mutex = Mutex()

    /** 当前持锁者（actionId，可能为空）与持锁起始时刻。 */
    @Volatile
    private var holderAction: String? = null

    @Volatile
    private var holderSince: Long = 0L

    /**
     * 是否忙。**持锁超过上限的一律视为不忙**——
     * 否则一次卡死会让「出图进行中」永久挂住，感知事件被无限延后（日志刷屏）。
     */
    fun isBusy(): Boolean {
        if (!mutex.isLocked) return false
        val since = holderSince
        if (since > 0L && System.currentTimeMillis() - since >= LANE_MAX_HOLD_MS) {
            Log.w(TAG, "泳道判为空闲：${holderAction ?: "(未知)"} 已持锁 ${(System.currentTimeMillis() - since) / 1000}s")
            return false
        }
        return true
    }

    suspend fun <T> withLane(actionId: String?, block: suspend () -> T): T {
        acquire(actionId)
        return try {
            block()
        } finally {
            release(actionId)
        }
    }

    /** 只在泳道空闲时运行；抢不到立刻返回 null（用于低优先级的动态配图）。 */
    suspend fun <T> withLaneIfIdle(actionId: String?, block: suspend () -> T): T? {
        if (!mutex.tryLock()) {
            val since = holderSince
            if (since <= 0L || System.currentTimeMillis() - since < LANE_MAX_HOLD_MS) return null
            Log.w(TAG, "泳道被 ${holderAction ?: "(未知)"} 占超上限，接管")
        }
        holderAction = actionId
        holderSince = System.currentTimeMillis()
        return try {
            block()
        } finally {
            release(actionId)
        }
    }

    private suspend fun acquire(actionId: String?) {
        while (true) {
            if (mutex.tryLock()) {
                holderAction = actionId
                holderSince = System.currentTimeMillis()
                return
            }
            val since = holderSince
            val held = if (since > 0L) System.currentTimeMillis() - since else 0L
            if (held >= LANE_MAX_HOLD_MS) {
                // 旧的已超时，判废并接管。这里不再 tryLock：那把锁可能永远等不到，
                // 直接把它当作不存在的门（旧的结束时 release 会安全地跳过）。
                Log.w(
                    TAG,
                    "泳道被 ${holderAction ?: "(未知)"} 占 ${held / 1000}s 超上限，接管给 ${actionId ?: "(未知)"}"
                )
                holderAction = actionId
                holderSince = System.currentTimeMillis()
                return
            }
            if (held >= WAIT_LOG_AFTER_MS && held % WAIT_LOG_INTERVAL_MS < WAIT_POLL_MS) {
                Log.i(TAG, "等泳道：${holderAction ?: "(未知)"} 已持锁 ${held / 1000}s")
            }
            delay(WAIT_POLL_MS)
        }
    }

    private fun release(actionId: String?) {
        // 只有真正的持锁者才解锁；被别人接管过的旧协程在这里安静退出。
        if (holderAction != actionId) return
        holderAction = null
        holderSince = 0L
        if (mutex.isLocked) mutex.unlock()
    }

    private companion object {
        const val TAG = "TongpinImageLane"

        /** 持锁上限：超过就认定那次出图已废，允许接管。 */
        const val LANE_MAX_HOLD_MS = 5 * 60_000L

        /** 排队等待时的轮询间隔。 */
        const val WAIT_POLL_MS = 500L

        /** 等超过这个时长才开始打日志，免得正常排队也吵。 */
        const val WAIT_LOG_AFTER_MS = 30_000L

        /** 等待日志的间隔。 */
        const val WAIT_LOG_INTERVAL_MS = 30_000L
    }
}
