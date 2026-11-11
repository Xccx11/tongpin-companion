package com.aster.companion.agent

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGenerationLaneRegistryTest {
    @Test
    fun `different action ids are serialized in the single image lane`() = runBlocking {
        val registry = ImageGenerationLaneRegistry()
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)

        listOf("private-action", "group-action").map { actionId ->
            async {
                registry.withLane(actionId) {
                    val running = active.incrementAndGet()
                    maximumActive.updateAndGet { current -> maxOf(current, running) }
                    delay(40)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals("different image actions must be serialized", 1, maximumActive.get())
    }

    @Test
    fun `same action id remains serialized`() = runBlocking {
        val registry = ImageGenerationLaneRegistry()
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)

        listOf("same-action", "same-action").map {
            async {
                registry.withLane("same-action") {
                    val running = active.incrementAndGet()
                    maximumActive.updateAndGet { current -> maxOf(current, running) }
                    delay(40)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(1, maximumActive.get())
    }
}
