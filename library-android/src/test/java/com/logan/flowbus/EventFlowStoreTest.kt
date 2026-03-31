package com.logan.flowbus

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class EventFlowStoreTest {

    @Test
    fun `normal post only creates normal flow`() {
        val store = EventFlowStore()

        store.post(eventName = "demo", value = "normal", isSticky = false)

        assertTrue(store.hasEventFlow(eventName = "demo", isSticky = false))
        assertFalse(store.hasEventFlow(eventName = "demo", isSticky = true))
    }

    @Test
    fun `sticky post caches latest value`() {
        val store = EventFlowStore()

        store.post(eventName = "demo", value = "first", isSticky = true)
        store.post(eventName = "demo", value = "second", isSticky = true)

        assertTrue(store.hasEventFlow(eventName = "demo", isSticky = true))
        assertEquals(listOf("second"), store.stickyReplayCache("demo"))
    }

    @Test
    fun `clear sticky event only clears replay cache`() {
        val store = EventFlowStore()

        store.post(eventName = "demo", value = "sticky", isSticky = true)
        store.clearStickyEvent("demo")

        assertTrue(store.hasEventFlow(eventName = "demo", isSticky = true))
        assertEquals(emptyList<Any>(), store.stickyReplayCache("demo"))
    }

    @Test
    fun `remove sticky event removes cached flow`() {
        val store = EventFlowStore()

        store.post(eventName = "demo", value = "sticky", isSticky = true)
        store.removeStickyEvent("demo")

        assertFalse(store.hasEventFlow(eventName = "demo", isSticky = true))
        assertEquals(emptyList<Any>(), store.stickyReplayCache("demo"))
    }

    @Test
    fun `normal event flow keeps bounded pending events and drops oldest when overloaded`() = runBlocking {
        val store = EventFlowStore(normalExtraBufferCapacity = 2)
        val flow = store.getEventFlow(eventName = "demo", isSticky = false)
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val firstReceived = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val job = launch {
            flow.collect { value ->
                received += value as Int
                if (received.size == 1 && !firstReceived.isCompleted) {
                    firstReceived.complete(Unit)
                    releaseFirst.await()
                }
                if (received.size == 3 && !completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
        }

        flow.subscriptionCount.first { it > 0 }
        store.post(eventName = "demo", value = 0, isSticky = false)
        firstReceived.await()

        store.post(eventName = "demo", value = 1, isSticky = false)
        store.post(eventName = "demo", value = 2, isSticky = false)
        store.post(eventName = "demo", value = 3, isSticky = false)
        store.post(eventName = "demo", value = 4, isSticky = false)

        releaseFirst.complete(Unit)

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(listOf(0, 3, 4), received)
        job.cancelAndJoin()
    }
}
