package com.aster.companion.lite.memory

import java.util.concurrent.ConcurrentHashMap

/**
 * 事实向量的小缓存（进程内）。
 *
 * 为什么需要：L2 记忆表没有向量列，向量只能现算 —— 而一次回复要挑出最多 12 条候选事实，
 * 每条一次 `/v1/embeddings`。这些事实在好几轮对话里都不会变，不缓存就等于每轮用户消息
 * 都要重新问一遍 Embedding 服务（十几秒的等待，卡就卡在这）。内容或时间一变，key 就变，
 * 自然重算。
 *
 * 上限到了**整体清空**而不是做 LRU：这里同时活跃的事实本来就只有几十条，
 * 清空比重排更简单，也不会留下"越用越慢"的隐性状态。
 */
internal class MemoryVectorCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    private val entries = ConcurrentHashMap<String, FloatArray>()

    /** key 里必须带上模型名与 `updatedAt`，否则改了内容或换了模型都会命中旧向量。 */
    fun key(modelName: String, factId: String, updatedAt: Long): String =
        "$modelName@$factId@$updatedAt"

    fun get(key: String): FloatArray? = entries[key]

    fun put(key: String, vector: FloatArray) {
        if (entries.size >= maxEntries) entries.clear()
        entries[key] = vector
    }

    fun size(): Int = entries.size

    companion object {
        const val DEFAULT_MAX_ENTRIES = 512
    }
}
