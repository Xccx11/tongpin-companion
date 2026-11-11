package com.aster.companion.agent

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「正在出图」的全局闸门。
 *
 * 为什么要有它：本机只有一块显卡，出图（ComfyUI）和对话（LM Studio）不能同时在显存里 ——
 * 网关必须在两种模式之间来回装卸模型。所以出图那 30～120 秒里，如果 App 还在发对话请求
 * （主动引擎、感知事件最频繁），网关就会不断切模式，把正在出的图拖慢甚至打断 ——
 * 表现就是"电脑上早出好了、App 还在转"。
 *
 * 用法：出图开始 [begin]、结束 [end]；主动/感知那类**非用户直接触发**的对话请求，看到 [busy]
 * 就先跳过或延后处理（不丢事件），等出图结束再走。
 */
@Singleton
class ImageWorkGate @Inject constructor() {
    private val inflight = AtomicInteger(0)

    /** 是否有出图任务在跑。 */
    val busy: Boolean get() = inflight.get() > 0

    fun begin() {
        inflight.incrementAndGet()
    }

    fun end() {
        inflight.updateAndGet { current -> if (current > 0) current - 1 else 0 }
    }

    /** 把一次出图包起来：无论成功失败都会释放。 */
    suspend fun <T> wrap(block: suspend () -> T): T {
        begin()
        return try {
            block()
        } finally {
            end()
        }
    }
}
