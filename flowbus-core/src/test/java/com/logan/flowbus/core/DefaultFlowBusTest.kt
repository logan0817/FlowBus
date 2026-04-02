package com.logan.flowbus.core

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

data class DefaultBusEvent(val message: String)

sealed interface DefaultBusSugarEvent {
    data object Refresh : DefaultBusSugarEvent
}

class DefaultFlowBusTest {

    @Before
    fun setUp() {
        DefaultFlowBus.resetForTests()
    }

    @After
    fun tearDown() {
        DefaultFlowBus.resetForTests()
    }

    @Test
    fun `flowbus overloads use type name as default event name`() = runBlocking {
        val bus = FlowBus()
        val event = DefaultBusEvent(message = "hello")
        val flow = bus.flow<DefaultBusEvent>() as MutableSharedFlow<DefaultBusEvent>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        bus.post(event)

        assertEquals(event, received.await())
    }

    @Test
    fun `flowbus overloads support custom event names for same payload type`() = runBlocking {
        val bus = FlowBus()
        val firstFlow = bus.flow<Int>(eventName = "counter.a") as MutableSharedFlow<Int>
        val secondFlow = bus.flow<Int>(eventName = "counter.b") as MutableSharedFlow<Int>
        val first = async { firstFlow.first() }
        val second = async { secondFlow.first() }

        firstFlow.subscriptionCount.first { it > 0 }
        secondFlow.subscriptionCount.first { it > 0 }
        bus.post(value = 1, eventName = "counter.a")
        bus.post(value = 2, eventName = "counter.b")

        assertEquals(1, first.await())
        assertEquals(2, second.await())
    }

    @Test
    fun `scoped overloads remain isolated from root bus`() = runBlocking {
        val bus = FlowBus()
        val scopedBus = bus.scoped("feature")
        val scopeHandle = bus.openScope("session")

        scopedBus.postSticky("feature-value")
        scopeHandle.postSticky("session-value")

        assertEquals("feature-value", scopedBus.stickyFlow<String>().first())
        assertEquals("session-value", scopeHandle.stickyFlow<String>().first())
        assertFalse(bus.hasEventFlow(eventName = String::class.java.name, isSticky = true))
    }

    @Test
    fun `default flowbus works out of the box`() = runBlocking {
        val event = DefaultBusEvent(message = "singleton")
        val flow = DefaultFlowBus.flow<DefaultBusEvent>() as MutableSharedFlow<DefaultBusEvent>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        assertTrue(DefaultFlowBus.post(event))

        assertEquals(event, received.await())
    }

    @Test
    fun `value sugar sends to default flowbus with explicit parent event type`() = runBlocking {
        val flow = DefaultFlowBus.flow<DefaultBusSugarEvent>() as MutableSharedFlow<DefaultBusSugarEvent>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        assertTrue(DefaultBusSugarEvent.Refresh.send<DefaultBusSugarEvent>())

        assertEquals(DefaultBusSugarEvent.Refresh, received.await())
    }

    @Test
    fun `value sugar can target explicit bus with custom event name`() = runBlocking {
        val bus = FlowBus()
        val event = DefaultBusEvent(message = "named")
        val flow = bus.flow<DefaultBusEvent>(eventName = "demo.named") as MutableSharedFlow<DefaultBusEvent>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        assertTrue(event.sendOn(bus, eventName = "demo.named"))

        assertEquals(event, received.await())
    }

    @Test
    fun `default flowbus key overloads expose full root api`() = runBlocking {
        val key = eventKey<DefaultBusEvent>("default.bus.key")
        val event = DefaultBusEvent(message = "typed")
        val flow = DefaultFlowBus.flow(key) as MutableSharedFlow<DefaultBusEvent>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        assertTrue(DefaultFlowBus.post(key, event))
        assertEquals(event, received.await())

        DefaultFlowBus.postSticky(key, event)
        assertEquals(event, DefaultFlowBus.stickyFlow(key).first())
        DefaultFlowBus.removeSticky(key)

        assertFalse(DefaultFlowBus.raw().hasEventFlow(eventName = key.name, isSticky = true))
    }

    @Test
    fun `default flowbus scoped alias matches raw api naming`() = runBlocking {
        val key = eventKey<String>("feature.message")
        val scoped = DefaultFlowBus.scoped("feature")
        val flow = DefaultFlowBus.scope("feature").flow(key) as MutableSharedFlow<String>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        scoped.post(key, "hello")

        assertEquals("hello", received.await())
        assertFalse(DefaultFlowBus.raw().hasEventFlow(eventName = key.name, isSticky = false))
    }

    @Test
    fun `default flowbus configure installs custom config before first use`() {
        val config = FlowBusConfig(normalBufferCapacity = 1, stickyReplay = 2)

        DefaultFlowBus.configure(config)

        assertEquals(config, DefaultFlowBus.raw().config)
    }

    @Test
    fun `default flowbus install is rejected after first use`() {
        DefaultFlowBus.raw()

        try {
            DefaultFlowBus.install(FlowBus())
            fail("Expected install to fail after the default bus has been initialized.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("before first use"))
        }
    }
}
