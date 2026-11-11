package com.aster.companion.lite.memory

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 向量缓存是"回复别卡在十几秒"的关键：一次回复最多 12 条候选事实，每条一次 Embedding 调用。
 */
class MemoryVectorCacheTest {

    @Test
    fun `同一事实第二次直接命中`() {
        val cache = MemoryVectorCache()
        val key = cache.key("nomic-embed", "fact-1", 1000L)

        assertNull(cache.get(key))
        cache.put(key, floatArrayOf(1f, 2f, 3f))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), cache.get(key)!!, 0f)
    }

    @Test
    fun `内容改了或换了模型都要重算`() {
        val cache = MemoryVectorCache()
        cache.put(cache.key("nomic-embed", "fact-1", 1000L), floatArrayOf(1f))

        // 同一条事实更新了（updatedAt 变了）→ key 不同，不会命中旧向量。
        assertNull(cache.get(cache.key("nomic-embed", "fact-1", 2000L)))
        // 换了 Embedding 模型 → 维度/语义都可能不同，也不能命中。
        assertNull(cache.get(cache.key("other-embed", "fact-1", 1000L)))
        // 不同事实之间更不能串。
        assertNull(cache.get(cache.key("nomic-embed", "fact-2", 1000L)))
        assertNotEquals(cache.key("nomic-embed", "fact-1", 1000L), cache.key("nomic-embed", "fact-2", 1000L))
    }

    @Test
    fun `超过上限整体清空，不会无限长大`() {
        val cache = MemoryVectorCache(maxEntries = 3)
        cache.put("a", floatArrayOf(1f))
        cache.put("b", floatArrayOf(1f))
        cache.put("c", floatArrayOf(1f))
        assertEquals(3, cache.size())

        cache.put("d", floatArrayOf(1f))

        assertEquals(1, cache.size())
        assertArrayEquals(floatArrayOf(1f), cache.get("d")!!, 0f)
    }
}
