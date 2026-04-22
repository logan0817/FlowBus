package com.logan.flowbus

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EventFlowExtensionTest {

    @After
    fun tearDown() {
        removeStickyEvent<String>()
        removeStickyEvent<String>(eventName = "global.named.sticky")
        removeStickyEvent<String>(eventName = "alias.demo")
    }

    @Test
    fun `stickyEventFlow reads latest value from global bus`() = runBlocking {
        postStickyEvent("global-sticky")

        assertEquals("global-sticky", stickyEventFlow<String>().first())
    }

    @Test
    fun `android api supports custom event names for global flows`() = runBlocking {
        val firstFlow = eventFlow<Int>(eventName = "counter.a") as MutableSharedFlow<Int>
        val secondFlow = eventFlow<Int>(eventName = "counter.b") as MutableSharedFlow<Int>
        val first = async { firstFlow.first() }
        val second = async { secondFlow.first() }

        firstFlow.subscriptionCount.first { it > 0 }
        secondFlow.subscriptionCount.first { it > 0 }
        postEvent(event = 1, eventName = "counter.a")
        postEvent(event = 2, eventName = "counter.b")

        assertEquals(1, first.await())
        assertEquals(2, second.await())
    }

    @Test
    fun `scoped owner api reads and writes owner scoped events`() = runBlocking {
        val owner = TestOwner()
        val flow = owner.scopedEventFlow<String>(eventName = "owner.named") as MutableSharedFlow<String>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        owner.postScopedEvent(event = "owner-value", eventName = "owner.named")

        assertEquals("owner-value", received.await())
        owner.viewModelStore.clear()
    }

    @Test
    fun `scoped sticky owner api supports custom ViewModelStoreOwner`() = runBlocking {
        val owner = TestOwner()
        owner.postScopedStickyEvent(event = "owner-sticky", eventName = "owner.sticky")

        assertEquals("owner-sticky", owner.scopedStickyEventFlow<String>(eventName = "owner.sticky").first())

        clearStickyEvent<String>(owner = owner, eventName = "owner.sticky")
        owner.viewModelStore.clear()
    }

    @Test
    fun `onEvent alias subscribes with custom event name`() = runBlocking {
        val received = CompletableDeferred<String>()
        val job = onEvent<String>(isSticky = true, eventName = "alias.demo") {
            received.complete(it)
        }

        postStickyEvent(event = "hello", eventName = "alias.demo")

        assertEquals("hello", withTimeout(1_000) { received.await() })
        job.cancelAndJoin()
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }
}
