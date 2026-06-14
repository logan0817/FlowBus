package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlowBusInspectorTest {

    @Before
    fun setUp() {
        DefaultFlowBus.resetForTests()
    }

    @After
    fun tearDown() {
        DefaultFlowBus.resetForTests()
    }

    @Test
    fun `inspect exposes root event metadata without replay payload`() {
        val config = FlowBusConfig(
            normalBufferCapacity = 2,
            stickyReplay = 1,
            stickyExtraBufferCapacity = 3,
            overflowPolicy = BufferOverflow.DROP_OLDEST
        )
        val bus = FlowBus(config)
        val key = eventKey<String>("inspect.root")

        bus.post(key, "normal")
        bus.postSticky(key, "secret")

        val snapshot = bus.inspect()
        val event = snapshot.root.events.single()

        assertEquals(2, snapshot.config.normalBufferCapacity)
        assertEquals(1, snapshot.config.stickyReplay)
        assertEquals(3, snapshot.config.stickyExtraBufferCapacity)
        assertEquals(BufferOverflow.DROP_OLDEST, snapshot.config.overflowPolicy)
        assertEquals(key.name, event.eventName)
        assertEquals(String::class.qualifiedName, event.valueTypeName)
        assertTrue(event.hasNormalFlow)
        assertTrue(event.hasStickyFlow)
        assertEquals(1, event.stickyReplayCount)
        assertFalse(snapshot.toString().contains("secret"))
    }

    @Test
    fun `inspector separates root and scoped event metadata`() {
        val bus = FlowBus()
        val rootKey = eventKey<String>("inspect.root.only")
        val scopedKey = eventKey<Int>("inspect.scoped.only")

        bus.post(rootKey, "root")
        bus.scoped("feature").postSticky(scopedKey, 7)

        val snapshot = bus.inspector().snapshot()
        val rootEvent = snapshot.root.events.single()
        val scope = snapshot.scopes.single()
        val scopedEvent = scope.events.single()

        assertEquals(rootKey.name, rootEvent.eventName)
        assertTrue(rootEvent.hasNormalFlow)
        assertFalse(rootEvent.hasStickyFlow)
        assertEquals("feature", scope.scopeName)
        assertEquals(scopedKey.name, scopedEvent.eventName)
        assertFalse(scopedEvent.hasNormalFlow)
        assertTrue(scopedEvent.hasStickyFlow)
        assertEquals(1, scopedEvent.stickyReplayCount)
    }

    @Test
    fun `inspect scope returns existing scope snapshot and null for missing scope`() {
        val bus = FlowBus()
        val key = eventKey<String>("inspect.scope.lookup")

        bus.scoped("feature").post(key, "value")

        val existing = bus.inspectScope("feature")
        val missing = bus.inspectScope("missing")

        assertEquals("feature", existing?.scopeName)
        assertEquals(key.name, existing?.events?.single()?.eventName)
        assertNull(missing)
    }

    @Test
    fun `inspect scope rejects blank scope name`() {
        val bus = FlowBus()

        try {
            bus.inspectScope("   ")
            throw AssertionError("Expected blank scopeName to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("scopeName"))
        }
    }

    @Test
    fun `inspect does not create missing event flows`() {
        val bus = FlowBus()

        val snapshot = bus.inspect()

        assertTrue(snapshot.root.events.isEmpty())
        assertTrue(snapshot.scopes.isEmpty())
        assertFalse(bus.hasEventFlow(eventName = "inspect.missing", isSticky = false))
        assertFalse(bus.hasEventFlow(eventName = "inspect.missing", isSticky = true))
    }

    @Test
    fun `cleanup APIs do not create missing events or bind their types`() {
        val bus = FlowBus()
        val clearKey = eventKey<String>("inspect.cleanup.missing.clear")
        val removeStickyKey = eventKey<String>("inspect.cleanup.missing.remove.sticky")
        val removeEventKey = eventKey<String>("inspect.cleanup.missing.remove.event")
        val consumeKey = eventKey<String>("inspect.cleanup.missing.consume")

        bus.clearSticky(clearKey)
        bus.removeSticky(removeStickyKey)
        bus.removeEvent(removeEventKey)
        assertNull(bus.consumeStickyLatest(consumeKey))

        assertEquals(emptyList<String>(), bus.inspect().root.events.map { it.eventName })
        bus.post(eventKey<Int>(clearKey.name), 1)
        bus.post(eventKey<Int>(removeStickyKey.name), 2)
        bus.post(eventKey<Int>(removeEventKey.name), 3)
        bus.post(eventKey<Int>(consumeKey.name), 4)
    }

    @Test
    fun `default flowbus inspect delegates to installed bus`() {
        val config = FlowBusConfig(normalBufferCapacity = 4)
        DefaultFlowBus.configure(config)

        DefaultFlowBus.post("value", eventName = "inspect.default")

        val snapshot = DefaultFlowBus.inspect()
        val event = snapshot.root.events.single()

        assertEquals(4, snapshot.config.normalBufferCapacity)
        assertEquals("inspect.default", event.eventName)
        assertEquals(String::class.qualifiedName, event.valueTypeName)
        assertTrue(event.hasNormalFlow)
    }

    @Test
    fun `default flowbus inspect does not initialize default bus`() {
        val snapshot = DefaultFlowBus.inspect()

        assertEquals(FlowBusConfig().toSnapshot(), snapshot.config)
        assertTrue(snapshot.root.events.isEmpty())
        assertTrue(snapshot.scopes.isEmpty())
        assertNull(DefaultFlowBus.inspectScope("missing"))

        val config = FlowBusConfig(normalBufferCapacity = 8)
        DefaultFlowBus.configure(config)

        assertEquals(config, DefaultFlowBus.raw().config)
    }

    @Test
    fun `default flowbus inspector does not initialize default bus`() {
        val inspector = DefaultFlowBus.inspector()

        assertTrue(inspector.snapshot().root.events.isEmpty())

        DefaultFlowBus.configure(FlowBusConfig(normalBufferCapacity = 9))
        DefaultFlowBus.post("value", eventName = "inspect.after.configure")

        val event = inspector.snapshot().root.events.single()
        assertEquals("inspect.after.configure", event.eventName)
        assertTrue(event.hasNormalFlow)
    }
}
