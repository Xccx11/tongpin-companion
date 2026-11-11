package com.aster.companion.diag

/**
 * **离线队列**：网络不可用/请求失败时，把"待重放的动作"排队，恢复后按序重放。
 *
 * 为什么需要它（B2 离线优先）：
 * 现在发消息/发照片失败就只是报错，用户得自己重发。真正该做的是：
 * 失败的动作进队列 → 网络恢复/下次前台 → **自动重放**，并且**同一个动作不会重复生效**。
 *
 * 设计要点：
 * - **幂等键**：每个动作有唯一 key（例如 messageId），重复入队会被忽略（不会重复发送）。
 * - **上限**：最多 N 条（默认 32），超出丢**最旧**的（避免无限堆积）。
 * - **不持久化**：进程内队列；持久化（Room）是下一步。
 * - **纯 Kotlin**：可在 JVM 单测里验证（见 PendingActionQueueTest）。
 */
object PendingActionQueue {

    /** 一个待重放的动作。 */
    data class Action(
        val key: String,
        val kind: String,
        val payload: String,
        val enqueuedAtMillis: Long,
    )

    private const val DEFAULT_CAPACITY = 32

    @Volatile private var capacity: Int = DEFAULT_CAPACITY
    private val lock = Any()
    private val order = ArrayDeque<String>()
    private val byKey = LinkedHashMap<String, Action>()

    /** 仅供测试：设置容量并清空。 */
    internal fun configureForTest(capacity: Int) {
        require(capacity > 0) { "capacity must be positive" }
        synchronized(lock) {
            this.capacity = capacity
            order.clear()
            byKey.clear()
        }
    }

    /** 入队。相同 key 已存在时**忽略**（返回 false），保证幂等。 */
    fun enqueue(key: String, kind: String, payload: String, atMillis: Long = System.currentTimeMillis()): Boolean {
        if (key.isBlank()) return false
        synchronized(lock) {
            if (byKey.containsKey(key)) return false
            byKey[key] = Action(key, kind, payload, atMillis)
            order.addLast(key)
            while (order.size > capacity) {
                val dropped = order.removeFirst()
                byKey.remove(dropped)
            }
            return true
        }
    }

    /** 取出队首动作（不移除）。 */
    fun peek(): Action? = synchronized(lock) { order.firstOrNull()?.let { byKey[it] } }

    /** 当前队列快照（旧 → 新）。 */
    fun snapshot(): List<Action> = synchronized(lock) { order.mapNotNull { byKey[it] } }

    fun size(): Int = synchronized(lock) { order.size }

    /** 某个动作处理成功/确定不能再重试时，出队。 */
    fun remove(key: String): Boolean = synchronized(lock) {
        if (byKey.remove(key) == null) return false
        order.remove(key)
        return true
    }

    /** 仅供测试：清空。 */
    internal fun clearForTest() {
        synchronized(lock) {
            order.clear()
            byKey.clear()
        }
    }
    /**
     * **重放循环**：按 FIFO 依次把动作交给 [handler]。
     *
     * 约定：
     * - handler 返回 true → 该动作**已完成**，出队；
     * - 返回 false → **保留**在队列里（下次再试），并且**停止本轮**（避免在离线时反复打同一个失败的请求）。
     *
     * 返回本轮成功出队的条数。整个过程串行，保证同一时刻只有一个重放在飞。
     */
    suspend fun drain(handler: suspend (Action) -> Boolean): Int {
        var done = 0
        while (true) {
            val next = peek() ?: break
            val ok = runCatching { handler(next) }.getOrDefault(false)
            if (!ok) break
            remove(next.key)
            done++
        }
        return done
    }
}
