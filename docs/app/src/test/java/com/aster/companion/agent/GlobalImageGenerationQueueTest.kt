package com.aster.companion.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalImageGenerationQueueTest {
    @Test
    fun `duplicate invocation of an active action does not wait for itself`() {
        assertEquals(
            true,
            GlobalImageGenerationQueue.isDuplicateInvocation("same-action", setOf("same-action"))
        )
    }

    @Test
    fun `different image actions are queued instead of running concurrently`() = runBlocking {
        val queue = ImageGenerationQueueGate()
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)

        listOf("first", "second").map { actionId ->
            async {
                queue.run(actionId, { true }) {
                    val running = active.incrementAndGet()
                    maximumActive.updateAndGet { current -> maxOf(current, running) }
                    delay(40)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals("image actions must use one rendering lane", 1, maximumActive.get())
    }

    @Test
    fun `duplicate invocation of an active action returns without running it twice`() = runBlocking {
        val queue = ImageGenerationQueueGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            queue.run("same-action", { true }) {
                entered.complete(Unit)
                release.await()
            }
        }

        entered.await()
        val duplicate = queue.run("same-action", { true }) { error("duplicate should not run") }
        release.complete(Unit)
        first.await()

        assertEquals(null, duplicate)
    }

}
