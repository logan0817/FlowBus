package com.logan.flowbus.core

import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class EventChannelTest {

    @Before
    fun setUp() {
        DefaultFlowBus.resetForTests()
    }

    @After
    fun tearDown() {
        DefaultFlowBus.resetForTests()
    }

    @Test
    fun `event channel posts and flows on default flowbus`() = runBlocking {
        val channel = eventChannel<String>("ui.toast")
        val flow = channel.flow() as MutableSharedFlow<String>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        assertTrue(channel.post("hello"))

        assertEquals("hello", received.await())
    }

    @Test
    fun `event channel targets explicit flowbus`() = runBlocking {
        val bus = FlowBus()
        val channel = eventChannel<Int>("counter.primary")
        val flow = channel.flowOn(bus) as MutableSharedFlow<Int>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        assertTrue(channel.postOn(bus, 42))

        assertEquals(42, received.await())
    }

    @Test
    fun `event channel collect receives default flowbus events`() = runBlocking {
        val channel = eventChannel<String>("channel.collect.default")
        val flow = channel.flow() as MutableSharedFlow<String>
        val received = CompletableDeferred<String>()

        val job = launch {
            channel.collect { value ->
                received.complete(value)
            }
        }

        flow.subscriptionCount.first { it > 0 }
        channel.emit("hello")

        assertEquals("hello", withTimeout(1_000) { received.await() })
        job.cancelAndJoin()
    }

    @Test
    fun `event channel collect receives explicit flowbus events`() = runBlocking {
        val bus = FlowBus()
        val channel = eventChannel<Int>("channel.collect.bus")
        val flow = channel.flowOn(bus) as MutableSharedFlow<Int>
        val received = CompletableDeferred<Int>()

        val job = launch {
            channel.collectOn(bus) { value ->
                received.complete(value)
            }
        }

        flow.subscriptionCount.first { it > 0 }
        channel.emitOn(bus, 7)

        assertEquals(7, withTimeout(1_000) { received.await() })
        job.cancelAndJoin()
    }

    @Test
    fun `flowbus overloads accept event channel`() = runBlocking {
        val bus = FlowBus()
        val channel = eventChannel<String>("feature.notice")
        val scopedBus = bus.scoped("feature")
        val flow = channel.flowOn(scopedBus) as MutableSharedFlow<String>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        assertTrue(channel.postOn(scopedBus, "scoped"))
        assertEquals("scoped", received.await())
        assertFalse(bus.hasEventFlow(eventName = channel.name, isSticky = false))
    }

    @Test
    fun `event channel collect sticky receives scoped replay`() = runBlocking {
        val bus = FlowBus()
        val channel = eventChannel<String>("channel.collect.scoped.sticky")
        val scopedBus = bus.scoped("feature")

        channel.postStickyOn(scopedBus, "sticky")
        val received = CompletableDeferred<String>()

        val job = launch {
            channel.collectStickyOn(scopedBus) { value ->
                received.complete(value)
            }
        }

        assertEquals("sticky", withTimeout(1_000) { received.await() })
        job.cancelAndJoin()
    }

    @Test
    fun `event channel collect receives flowbus scope events`() = runBlocking {
        val bus = FlowBus()
        val channel = eventChannel<String>("channel.collect.scope")
        val scope = bus.openScope("session")
        val flow = channel.flowOn(scope) as MutableSharedFlow<String>
        val received = CompletableDeferred<String>()

        val job = launch {
            channel.collectOn(scope) { value ->
                received.complete(value)
            }
        }

        flow.subscriptionCount.first { it > 0 }
        channel.emitOn(scope, "scoped")

        assertEquals("scoped", withTimeout(1_000) { received.await() })
        job.cancelAndJoin()
        scope.close()
    }

    @Test
    fun `event channel sticky helpers clear scoped replay cache`() = runBlocking {
        val bus = FlowBus()
        val channel = eventChannel<String>("sync.state")

        assertTrue(channel.postStickyOn(bus, "running"))
        assertEquals("running", channel.stickyFlowOn(bus).first())

        channel.clearStickyOn(bus)
        assertEquals(emptyList<Any>(), bus.stickyReplayCache(channel.name))

        channel.removeStickyOn(bus)
        assertFalse(bus.hasEventFlow(eventName = channel.name, isSticky = true))
    }

    @Test
    fun `event channel remove event clears default normal event only`() {
        val channel = eventChannel<String>("channel.remove.default")

        channel.post("normal")
        channel.postSticky("sticky")

        channel.removeEvent()

        assertFalse(DefaultFlowBus.raw().hasEventFlow(eventName = channel.name, isSticky = false))
        assertTrue(DefaultFlowBus.raw().hasEventFlow(eventName = channel.name, isSticky = true))
        assertEquals(listOf("sticky"), DefaultFlowBus.raw().stickyReplayCache(channel.name))
    }

    @Test
    fun `event channel remove event targets explicit buses`() {
        val bus = FlowBus()
        val channel = eventChannel<String>("channel.remove.target")
        val scoped = bus.scoped("feature")
        val scope = bus.openScope("session")
        val rootFlow = channel.flowOn(bus) as MutableSharedFlow<String>
        val scopedFlow = channel.flowOn(scoped) as MutableSharedFlow<String>
        val scopeFlow = channel.flowOn(scope) as MutableSharedFlow<String>

        channel.removeEventOn(bus)
        channel.removeEventOn(scoped)
        channel.removeEventOn(scope)

        assertFalse(bus.hasEventFlow(eventName = channel.name, isSticky = false))
        assertFalse(rootFlow === channel.flowOn(bus))
        assertFalse(scopedFlow === channel.flowOn(scoped))
        assertFalse(scopeFlow === channel.flowOn(scope))
        scope.close()
    }

    @Test
    fun `event key can convert to event channel`() {
        val key = eventKey<String>("ui.toast")
        val channel = key.asEventChannel()

        assertEquals(key.name, channel.name)
        assertEquals(String::class, channel.valueType)
        assertEquals(key, channel.asEventKey())
    }

    @Test
    fun `event key rejects blank name`() {
        try {
            eventKey<String>("   ")
            fail("Expected eventKey(blank) to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("name"))
        }
    }

    @Test
    fun `event channel rejects blank name`() {
        try {
            eventChannel<String>("   ")
            fail("Expected eventChannel(blank) to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("name"))
        }
    }
}
