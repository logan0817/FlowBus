package com.logan.flowbus

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorContext
import com.logan.flowbus.core.FlowBusErrorHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class OnEventSuspendingTest {
    @Test
    fun `global suspended callbacks remain sequential and cancellation is not an error`() = runBlocking {
        FlowBusAndroid.resetForTests()
        val errors = mutableListOf<FlowBusErrorContext>()
        FlowBusAndroid.configure(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, _ -> errors += context }))
        val channel = eventChannel<Int>("android.suspend.global")
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val job = onEventSuspending(channel) { value ->
            received += value
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        try {
            val flow = channel.flow() as MutableSharedFlow<Int>
            withTimeout(1_000) { flow.subscriptionCount.first { it > 0 } }
            channel.tryPost(1)
            withTimeout(1_000) { started.await() }
            channel.tryPost(2)
            job.cancelAndJoin()
            assertTrue(cancelled.isCompleted)
            assertEquals(listOf(1), received)
            assertTrue(errors.isEmpty())
        } finally {
            job.cancelAndJoin()
            FlowBusAndroid.resetForTests()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reified owner suspend subscription routes only to owner and propagates policy`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        FlowBusAndroid.resetForTests()
        val errors = mutableListOf<FlowBusErrorContext>()
        FlowBusAndroid.configure(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, _ -> errors += context }))
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val received = CompletableDeferred<Int>()
        val name = "android.suspend.owner"
        val job = onEventSuspending<Int>(from = owner, eventName = name) { value ->
            if (value == 1) throw IllegalArgumentException("handled")
            received.complete(value)
        }
        try {
            val flow = eventFlowFrom<Int>(owner, eventName = name) as MutableSharedFlow<Int>
            withTimeout(1_000) { flow.subscriptionCount.first { it > 0 } }
            postEvent(99, eventName = name)
            postEventTo(owner, 1, eventName = name)
            postEventTo(owner, 2, eventName = name)
            assertEquals(2, withTimeout(1_000) { received.await() })
            assertEquals(name, errors.single().eventName)
        } finally {
            job.cancelAndJoin()
            owner.viewModelStore.clear()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `direct FlowEventBus sticky suspend subscription reads replay`() = runBlocking {
        FlowBusAndroid.resetForTests()
        val bus = FlowEventBus()
        val name = "android.suspend.sticky"
        bus.post(name, 42, Int::class, isSticky = true)
        val received = CompletableDeferred<Int>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.subscribeEventSuspending(name, Int::class, isSticky = true) {
                received.complete(it)
            }
        }
        try { assertEquals(42, withTimeout(1_000) { received.await() }) }
        finally { job.cancelAndJoin(); FlowBusAndroid.resetForTests() }
    }
}
