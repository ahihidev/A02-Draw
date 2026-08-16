package com.a02.draw.ads.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdUnitSingleFlightQueueTest {
    @Test
    fun `same ad unit starts requests one at a time in fifo order`() {
        val queue = AdUnitSingleFlightQueue()
        val starts = mutableListOf<String>()
        val completions = mutableListOf<() -> Unit>()

        repeat(3) { index ->
            queue.enqueue("same-id") { complete ->
                starts += "request-$index"
                completions += complete
            }
        }

        assertEquals(listOf("request-0"), starts)
        assertEquals(2, queue.pendingCount("same-id"))
        completions[0]()
        assertEquals(listOf("request-0", "request-1"), starts)
        completions[1]()
        completions[2]()
        assertFalse(queue.isActive("same-id"))
    }

    @Test
    fun `different ad units can load in parallel`() {
        val queue = AdUnitSingleFlightQueue()

        queue.enqueue("high") { }
        queue.enqueue("medium") { }

        assertTrue(queue.isActive("high"))
        assertTrue(queue.isActive("medium"))
    }

    @Test
    fun `duplicate completion cannot release the next request`() {
        val queue = AdUnitSingleFlightQueue()
        val starts = mutableListOf<Int>()
        lateinit var firstComplete: () -> Unit

        queue.enqueue("same-id") { complete ->
            starts += 1
            firstComplete = complete
        }
        queue.enqueue("same-id") { starts += 2 }

        firstComplete()
        firstComplete()

        assertEquals(listOf(1, 2), starts)
        assertTrue(queue.isActive("same-id"))
    }
}
