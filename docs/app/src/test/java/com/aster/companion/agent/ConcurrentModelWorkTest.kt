package com.aster.companion.agent

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentModelWorkTest {
    @Test
    fun `runs independent model tasks concurrently and keeps result order`() = runBlocking {
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val results = ConcurrentModelWork.map(listOf(1, 2, 3)) { value ->
            val running = active.incrementAndGet()
            maximumActive.updateAndGet { current -> maxOf(current, running) }
            delay(40)
            active.decrementAndGet()
            value * 2
        }

        assertEquals(listOf(2, 4, 6), results)
        assertTrue("independent model tasks were serialized", maximumActive.get() > 1)
    }
}
