package com.logan.flowbus.core

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `event key can convert to event channel`() {
        val key = eventKey<String>("ui.toast")
        val channel = key.asEventChannel()

        assertEquals(key.name, channel.name)
        assertEquals(String::class, channel.valueType)
        assertEquals(key, channel.asEventKey())
    }
}
