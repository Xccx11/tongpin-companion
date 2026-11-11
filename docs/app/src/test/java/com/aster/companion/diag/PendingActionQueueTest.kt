package com.aster.companion.diag

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PendingActionQueueTest {

    @Before
    fun setUp() {
        PendingActionQueue.configureForTest(capacity = 3)
    }

    @After
    fun tearDown() {
        PendingActionQueue.clearForTest()
        PendingActionQueue.configureForTest(capacity = 32)
    }

    @Test
    fun `enqueue is idempotent by key`() {
        assertTrue(PendingActionQueue.enqueue("m1", "chat", "hello"))
        assertFalse("同一个 key 不应重复入队", PendingActionQueue.enqueue("m1", "chat", "hello again"))
        assertEquals(1, PendingActionQueue.size())
    }

    @Test
    fun `blank key is rejected`() {
        assertFalse(PendingActionQueue.enqueue("  ", "chat", "x"))
        assertEquals(0, PendingActionQueue.size())
    }

    @Test
    fun `oldest entry is dropped when capacity exceeded`() {
        PendingActionQueue.enqueue("a", "chat", "1")
        PendingActionQueue.enqueue("b", "chat", "2")
        PendingActionQueue.enqueue("c", "chat", "3")
        PendingActionQueue.enqueue("d", "chat", "4")

        assertEquals(3, PendingActionQueue.size())
        assertEquals(listOf("b", "c", "d"), PendingActionQueue.snapshot().map { it.key })
        assertEquals("b", PendingActionQueue.peek()?.key)
    }

    @Test
    fun `remove drains the head in order`() {
        PendingActionQueue.enqueue("a", "chat", "1")
        PendingActionQueue.enqueue("b", "chat", "2")

        assertTrue(PendingActionQueue.remove("a"))
        assertEquals("b", PendingActionQueue.peek()?.key)
        assertFalse("已出队的 key 不能再次移除", PendingActionQueue.remove("a"))
        assertNull(PendingActionQueue.peek()?.key?.let { null })
    }

    @Test
    fun `snapshot keeps FIFO order`() {
        PendingActionQueue.enqueue("x", "photo", "p1")
        PendingActionQueue.enqueue("y", "photo", "p2")
        assertEquals(listOf("x", "y"), PendingActionQueue.snapshot().map { it.key })
    }
    @Test
    fun `drain replays in order and removes successes`() = kotlinx.coroutines.runBlocking {
        PendingActionQueue.enqueue("a", "scene_photo", "p1")
        PendingActionQueue.enqueue("b", "scene_photo", "p2")

        val seen = mutableListOf<String>()
        val done = PendingActionQueue.drain { action ->
            seen += action.key
            true
        }

        assertEquals(2, done)
        assertEquals(listOf("a", "b"), seen)
        assertEquals(0, PendingActionQueue.size())
    }

    @Test
    fun `drain keeps failing action and stops`() = kotlinx.coroutines.runBlocking {
        PendingActionQueue.enqueue("a", "scene_photo", "p1")
        PendingActionQueue.enqueue("b", "scene_photo", "p2")

        val attempts = mutableListOf<String>()
        val done = PendingActionQueue.drain { action ->
            attempts += action.key
            false
        }

        assertEquals(0, done)
        assertEquals(listOf("a"), attempts)
        assertEquals(2, PendingActionQueue.size())
    }

    @Test
    fun `drain treats handler exception as failure`() = kotlinx.coroutines.runBlocking {
        PendingActionQueue.enqueue("a", "scene_photo", "p1")
        val done = PendingActionQueue.drain { throw IllegalStateException("boom") }
        assertEquals(0, done)
        assertEquals(1, PendingActionQueue.size())
    }
}
