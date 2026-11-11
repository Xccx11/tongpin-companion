package com.aster.companion.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * EndpointResolver 的**缓存行为**测试（A4/A5 交叉：先守住"清空后回退"的语义，
 * 之后把缓存搬进注入实例时，这些断言仍然成立）。
 */
class EndpointResolverCacheTest {

    @Before
    fun setUp() {
        EndpointResolver.clearCaches()
    }

    @After
    fun tearDown() {
        EndpointResolver.clearCaches()
    }

    @Test
    fun `cachedBaseUrl falls back when no snapshot exists`() {
        assertEquals("https://fallback.example", EndpointResolver.cachedBaseUrl("p1", "https://fallback.example"))
    }

    @Test
    fun `clearCaches resets the snapshot`() {
        EndpointResolver.clearCaches()
        assertEquals("fb", EndpointResolver.cachedBaseUrl("p1", "fb"))
        assertEquals("fb2", EndpointResolver.cachedBaseUrl("p2", "fb2"))
    }
}