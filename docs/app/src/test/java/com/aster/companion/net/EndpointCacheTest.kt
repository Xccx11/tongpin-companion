package com.aster.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EndpointCache] 的行为测试（A5 的收尾：把两处并发修复钉住）。
 *
 * 这些断言不依赖 dao / 网络 / 协程，纯 JVM 即可跑 —— 所以它守的是**缓存语义**本身：
 * 写入能读到、清空能清掉、探测三字段始终一致。
 */
class EndpointCacheTest {

    @Test
    fun `baseUrls are readable after put`() {
        val cache = EndpointCache()
        cache.putBaseUrl("p1", "http://192.168.0.100:8088/v1")
        assertEquals("http://192.168.0.100:8088/v1", cache.baseUrlOf("p1"))
        assertNull(cache.baseUrlOf("missing"))
    }

    @Test
    fun `putBaseUrl accumulates instead of replacing`() {
        val cache = EndpointCache()
        cache.putBaseUrl("p1", "lan1")
        cache.putBaseUrl("p2", "lan2")
        assertEquals("lan1", cache.baseUrlOf("p1"))
        assertEquals("lan2", cache.baseUrlOf("p2"))
    }

    @Test
    fun `clearBaseUrls removes all entries`() {
        val cache = EndpointCache()
        cache.putBaseUrl("p1", "lan1")
        cache.clearBaseUrls()
        assertNull(cache.baseUrlOf("p1"))
    }

    @Test
    fun `probe state is updated as one immutable value`() {
        val cache = EndpointCache()
        cache.setProbe(url = "http://a", ok = true, at = 123L)
        val s = cache.probeState()
        assertEquals("http://a", s.url)
        assertTrue(s.ok)
        assertEquals(123L, s.at)

        cache.setProbe(url = "http://b", ok = false, at = 456L)
        val t = cache.probeState()
        assertEquals("http://b", t.url)
        assertEquals(false, t.ok)
        assertEquals(456L, t.at)
    }

    @Test
    fun `clearAll resets both probe and baseUrls`() {
        val cache = EndpointCache()
        cache.putBaseUrl("p1", "lan1")
        cache.setProbe(url = "http://a", ok = true, at = 1L)

        cache.clearAll()

        assertNull(cache.baseUrlOf("p1"))
        val s = cache.probeState()
        assertNull(s.url)
        assertEquals(false, s.ok)
        assertEquals(0L, s.at)
    }
}