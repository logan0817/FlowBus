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

class EventChannelExtensionTest {

    private val toastChannel = eventChannel<String>("ui.toast")
    private val ownerChannel = eventChannel<String>("feature.toast")

    @After
    fun tearDown() {
        toastChannel.removeSticky()
    }

    @Test
    fun `event channel posts and flows on android global bus`() = runBlocking {
        val flow = toastChannel.flow() as MutableSharedFlow<String>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        toastChannel.post("hello")

        assertEquals("hello", received.await())
    }

    @Test
    fun `scoped owner channel api reads and writes owner scoped events`() = runBlocking {
        val owner = TestOwner()
        val flow = owner.scopedEventFlow(ownerChannel) as MutableSharedFlow<String>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        owner.postScopedEvent(ownerChannel, "scoped")

        assertEquals("scoped", received.await())
        ownerChannel.removeStickyFrom(owner)
        owner.viewModelStore.clear()
    }

    @Test
    fun `onEvent accepts event channel`() = runBlocking {
        val received = CompletableDeferred<String>()
        val job = onEvent(toastChannel, isSticky = true) {
            received.complete(it)
        }

        toastChannel.postSticky("hello")

        assertEquals("hello", withTimeout(1_000) { received.await() })
        job.cancelAndJoin()
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }
}
