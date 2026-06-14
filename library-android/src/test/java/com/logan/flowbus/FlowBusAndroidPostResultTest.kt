package com.logan.flowbus

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBusPostOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowBusAndroidPostResultTest {

    @After
    fun tearDown() {
        FlowBusAndroid.resetForTests()
    }

    @Test
    fun `try post event result returns detailed result for global event`() {
        val result = tryPostEventResult(
            event = AndroidPostResultEvent("value"),
            eventName = "android.post.result"
        )

        assertTrue(result.accepted)
        assertEquals("android.post.result", result.eventName)
        assertNull(result.scopeName)
        assertFalse(result.isSticky)
        assertEquals(FlowBusPostOutcome.AcceptedWithoutActiveSubscriber, result.outcome)
    }

    @Test
    fun `try post event result to owner returns detailed result`() {
        val owner = TestOwner()

        try {
            val result = tryPostEventResultTo(
                owner = owner,
                event = AndroidPostResultEvent("owner"),
                eventName = "android.post.result.owner"
            )

            assertTrue(result.accepted)
            assertEquals("android.post.result.owner", result.eventName)
            assertNull(result.scopeName)
            assertFalse(result.isSticky)
        } finally {
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun `try post sticky event result marks sticky events`() {
        val result = tryPostStickyEventResult(
            event = AndroidPostResultEvent("sticky"),
            eventName = "android.post.result.sticky"
        )

        assertTrue(result.accepted)
        assertTrue(result.isSticky)
        assertEquals(1, result.stickyReplayCount)
    }

    private data class AndroidPostResultEvent(val value: String)

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }
}
