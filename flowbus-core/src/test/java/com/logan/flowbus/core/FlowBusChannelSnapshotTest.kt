package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusChannelSnapshotTest {
    private val key = eventKey<Int>("channel.snapshot")

    @Test
    fun `unknown queries do not create scopes flows or bindings`() {
        val bus = FlowBus()
        val shared = bus.scoped("shared")
        val owned = bus.openScope("owned")
        try {
            assertNull(bus.inspectChannel(key))
            assertNull(shared.inspectChannel(key))
            assertNull(owned.inspectChannel(key))
            assertFalse(bus.hasScope("shared"))
            assertFalse(bus.hasScope("owned"))
            assertTrue(bus.inspect().root.events.isEmpty())
            assertTrue(bus.inspect().scopes.single().events.isEmpty())
            bus.configureChannel(eventKey<String>(key.name), FlowBusChannelConfig())
        } finally {
            owned.close()
        }
    }

    @Test
    fun `configured snapshot exposes resolved policies without creating flows or payloads`() {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 2, stickyExtraBufferCapacity = 3))
        bus.configureChannel(key, FlowBusChannelConfig(
            normalBufferCapacity = 0,
            normalOverflowPolicy = BufferOverflow.SUSPEND,
            stickyOverflowPolicy = BufferOverflow.DROP_LATEST
        ))
        val before = bus.inspect()
        val snapshot = checkNotNull(bus.inspectChannel(key))
        assertEquals(key.name, snapshot.eventName)
        assertEquals(Int::class.qualifiedName, snapshot.valueTypeName)
        assertEquals(0, snapshot.normalBufferCapacity)
        assertEquals(BufferOverflow.SUSPEND, snapshot.normalOverflowPolicy)
        assertEquals(2, snapshot.stickyReplay)
        assertEquals(3, snapshot.stickyExtraBufferCapacity)
        assertEquals(BufferOverflow.DROP_LATEST, snapshot.stickyOverflowPolicy)
        assertFalse(snapshot.hasNormalFlow)
        assertFalse(snapshot.hasStickyFlow)
        assertEquals(0, snapshot.normalSubscriptionCount)
        assertEquals(0, snapshot.stickySubscriptionCount)
        assertEquals(before, bus.inspect())
    }

    @Test
    fun `ordinary and sticky subscriber counts are independent and do not change metrics`() = runTest {
        val bus = FlowBus()
        val normal = launch(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).collect {} }
        val sticky = launch(start = CoroutineStart.UNDISPATCHED) { bus.stickyFlow(key).collect {} }
        try {
            val before = bus.inspect()
            val both = checkNotNull(bus.inspectChannel(key))
            assertEquals(1, both.normalSubscriptionCount)
            assertEquals(1, both.stickySubscriptionCount)
            assertTrue(both.hasNormalFlow)
            assertTrue(both.hasStickyFlow)
            assertEquals(before, bus.inspect())
            normal.cancelAndJoin()
            val stickyOnly = checkNotNull(bus.inspectChannel(key))
            assertEquals(0, stickyOnly.normalSubscriptionCount)
            assertEquals(1, stickyOnly.stickySubscriptionCount)
        } finally {
            normal.cancelAndJoin()
            sticky.cancelAndJoin()
        }
    }

    @Test
    fun `read only query rejects known type conflicts without completing a raw type binding`() {
        val bus = FlowBus()
        bus.configureChannel(key, FlowBusChannelConfig())
        val before = bus.inspect()
        assertThrows(IllegalArgumentException::class.java) { bus.inspectChannel(eventKey<String>(key.name)) }
        assertEquals(before, bus.inspect())
        val raw = rawEventKey("raw.snapshot")
        bus.flow(raw)
        assertNull(bus.inspectChannel(eventKey<String>(raw.name))?.valueTypeName)
        bus.configureChannel(eventKey<Int>(raw.name), FlowBusChannelConfig())
        assertEquals(Int::class.qualifiedName, bus.inspectChannel(raw)?.valueTypeName)
    }

    @Test
    fun `removed flows retain config while full scope cleanup resets its binding`() {
        val bus = FlowBus()
        val owned = bus.openScope("session")
        val shared = bus.scoped("session")
        owned.configureChannel(key, FlowBusChannelConfig(normalBufferCapacity = 1))
        owned.flow(key)
        owned.stickyFlow(key)
        owned.removeEvent(key)
        owned.removeSticky(key)
        val retained = checkNotNull(owned.inspectChannel(key))
        assertEquals(1, retained.normalBufferCapacity)
        assertFalse(retained.hasNormalFlow)
        assertFalse(retained.hasStickyFlow)
        assertEquals(retained, shared.inspectChannel(key))
        assertNull(bus.inspectChannel(key))
        owned.close()
        assertThrows(IllegalStateException::class.java) { owned.inspectChannel(key) }
        assertNull(shared.inspectChannel(key))
        assertFalse(bus.hasScope("session"))
        shared.flow(key)
        assertEquals(64, shared.inspectChannel(key)?.normalBufferCapacity)
    }
}
