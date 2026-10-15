package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusDeliveryContractTest {
    @Test
    fun `normal and sticky publications only reach their own streams`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("contract.split")
        val normal = mutableListOf<Int>()
        val sticky = mutableListOf<Int>()
        val normalJob = launch(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).collect { normal += it } }
        val stickyJob = launch(start = CoroutineStart.UNDISPATCHED) { bus.stickyFlow(key).collect { sticky += it } }
        try {
            bus.post(key, 1)
            bus.postSticky(key, 2)
            runCurrent()
            assertEquals(listOf(1), normal)
            assertEquals(listOf(2), sticky)
            assertEquals(2, bus.consumeStickyLatest(key))
            assertNull(bus.consumeStickyLatest(key))
            assertTrue(normalJob.isActive)
            assertTrue(stickyJob.isActive)
        } finally {
            normalJob.cancelAndJoin()
            stickyJob.cancelAndJoin()
        }
    }

    @Test
    fun `registration is not readiness and an offline ordinary value is never replayed`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("contract.readiness")
        val received = mutableListOf<Int>()
        val job = launch { bus.flow(key).collect { received += it } }
        try {
            assertTrue(bus.post(key, 1))
            runCurrent()
            assertTrue(received.isEmpty())
            bus.post(key, 2)
            runCurrent()
            assertEquals(listOf(2), received)
        } finally {
            job.cancelAndJoin()
        }
    }

    private sealed interface Event {
        data object Child : Event
    }

    @Test
    fun `parent subscriptions require the sender to use the same parent channel`() = runTest {
        val bus = FlowBus()
        val received = mutableListOf<Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { bus.flow<Event>().collect { received += it } }
        try {
            bus.post(Event.Child)
            runCurrent()
            assertTrue(received.isEmpty())
            bus.post<Event>(Event.Child)
            runCurrent()
            assertEquals(listOf(Event.Child), received)
        } finally {
            job.cancelAndJoin()
        }
    }
}
