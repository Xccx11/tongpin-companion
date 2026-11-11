package com.aster.companion.diag

/**
 * 轻量的**请求追踪**：记录最近 N 次对外请求的路径、耗时、成功与否。
 *
 * 为什么需要它（B1 可观测性的第一步）：
 * 出图与对话的慢，经常是"某一次调用卡住"而不是整体慢。把每跳的耗时留在内存里，
 * 出问题时能直接看"哪一跳、多少毫秒、失败几次"，而不是翻日志猜。
 *
 * 设计取舍：
 * - **只留最近 N 条**（默认 64），避免无限增长；不做持久化（重启即清空）。
 * - 线程安全：多个协程/线程都会写，用同步块保护；读取返回**快照**，调用方拿到的列表不会被后续写入改动。
 * - 纯 Kotlin、无 Android 依赖，因此可以在 JVM 单元测试里直接验证（见 RequestTraceTest）。
 */
object RequestTrace {

    /** 一条请求记录。 */
    data class Entry(
        val path: String,
        val durationMs: Long,
        val ok: Boolean,
        val atMillis: Long,
    )

    private const val DEFAULT_CAPACITY = 64

    @Volatile private var capacity: Int = DEFAULT_CAPACITY

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()

    /** 仅供测试使用：调整容量并清空。生产代码不要调用。 */
    internal fun configureForTest(capacity: Int) {
        require(capacity > 0) { "capacity must be positive" }
        synchronized(lock) {
            this.capacity = capacity
            buffer.clear()
        }
    }

    /** 记录一次请求。超过容量时丢弃最旧的一条。 */
    fun record(path: String, durationMs: Long, ok: Boolean, atMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            buffer.addLast(Entry(path = path, durationMs = durationMs.coerceAtLeast(0L), ok = ok, atMillis = atMillis))
            while (buffer.size > capacity) buffer.removeFirst()
        }
    }

    /** 最近记录的快照（旧 → 新）。 */
    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    /** 汇总：总次数、失败次数、平均耗时、P95 耗时（没有样本时为 0）。 */
    fun summary(): Summary = synchronized(lock) {
        if (buffer.isEmpty()) return Summary(0, 0, 0L, 0L)
        val durations = buffer.map { it.durationMs }.sorted()
        val p95Index = ((durations.size - 1) * 0.95).toInt().coerceIn(0, durations.size - 1)
        Summary(
            total = buffer.size,
            failures = buffer.count { !it.ok },
            averageMs = durations.sum() / durations.size,
            p95Ms = durations[p95Index],
        )
    }

    data class Summary(val total: Int, val failures: Int, val averageMs: Long, val p95Ms: Long)

    /** 仅供测试使用：清空记录。 */
    internal fun clearForTest() {
        synchronized(lock) { buffer.clear() }
    }
}