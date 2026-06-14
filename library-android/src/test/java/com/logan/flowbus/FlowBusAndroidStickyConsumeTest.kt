package com.logan.flowbus

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FlowBusAndroidStickyConsumeTest {

    private val globalChannel = eventChannel<String>("android.sticky.consume.channel")
    private val ownerChannel = eventChannel<String>("android.sticky.consume.owner.channel")

    @After
    fun tearDown() {
        removeStickyEvent<String>(eventName = "android.sticky.consume.global")
        removeStickyEvent<String>(eventName = "android.sticky.consume.type")
        globalChannel.removeSticky()
        FlowBusAndroid.resetForTests()
    }

    @Test
    fun `consume sticky latest event returns latest global value and clears replay`() {
        postStickyEvent(event = "first", eventName = "android.sticky.consume.global")
        postStickyEvent(event = "second", eventName = "android.sticky.consume.global")

        assertEquals("second", consumeStickyLatestEvent<String>(eventName = "android.sticky.consume.global"))
        assertNull(consumeStickyLatestEvent<String>(eventName = "android.sticky.consume.global"))
    }

    @Test
    fun `consume sticky latest event from owner returns scoped value and clears replay`() {
        val owner = TestOwner()

        try {
            postStickyEventTo(owner = owner, event = "owner", eventName = "android.sticky.consume.owner")

            assertEquals("owner", consumeStickyLatestEvent<String>(owner = owner, eventName = "android.sticky.consume.owner"))
            assertNull(consumeStickyLatestEvent<String>(owner = owner, eventName = "android.sticky.consume.owner"))
        } finally {
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun `event channel consumes global and owner sticky latest values`() {
        val owner = TestOwner()

        try {
            globalChannel.postSticky("global-channel")
            ownerChannel.postStickyTo(owner = owner, value = "owner-channel")

            assertEquals("global-channel", globalChannel.consumeStickyLatest())
            assertNull(globalChannel.consumeStickyLatest())
            assertEquals("owner-channel", ownerChannel.consumeStickyLatestFrom(owner))
            assertNull(ownerChannel.consumeStickyLatestFrom(owner))
        } finally {
            ownerChannel.removeStickyFrom(owner)
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun `consume sticky latest event preserves same-name type protection`() {
        postStickyEvent(event = "value", eventName = "android.sticky.consume.type")

        try {
            consumeStickyLatestEvent<Int>(eventName = "android.sticky.consume.type")
            fail("Expected conflicting sticky consume type to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("android.sticky.consume.type"))
        }
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }
}
