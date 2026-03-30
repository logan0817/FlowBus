package com.logan.flowbus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
